package com.thatcoffeelock.terraincognita;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Sea Chart of the Southland, and the voyage it starts. Right-click it at sea, aboard a boat or a ship, and hold
 * your course: after {@link TerraConfig#voyageSeconds} of open water the fog takes you (and your crew) across. Over
 * there the same chart brings you home. Step ashore, or drop the chart, and the heading is lost.
 */
final class Charts {
	static final String KEY = "terra_incognita";
	static final String CHART = "chart";

	/** Who is sailing, and for how many seconds so far. */
	private static final Map<UUID, Integer> VOYAGES = new HashMap<>();

	private Charts() {
	}

	static boolean isChart(ItemStack stack) {
		if (!stack.is(Items.PAPER)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && CHART.equals(data.copyTag().getStringOr(KEY, ""));
	}

	/** A chart, the same one the recipe makes. */
	static ItemStack chart() {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, CHART);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("filled_map"));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Sea Chart of the Southland").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Half of it is blank. The blank half is where the gold is.", ChatFormatting.GRAY),
			text("Take it to sea in a boat (or aboard a ship) and right-click.", ChatFormatting.DARK_GRAY),
			text("Hold your course and Terra Incognita rises out of the fog.", ChatFormatting.DARK_GRAY),
			text("Over there, it brings you home the same way.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	private static Component text(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(style -> style.withItalic(false)).withStyle(color);
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	/** Aboard something, on (or just over) water. */
	static boolean atSea(ServerPlayer player) {
		if (!player.isPassenger()) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		for (int dy = 0; dy <= 4; dy++) {
			if (!level.getBlockState(player.blockPosition().below(dy)).getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	static boolean carriesChart(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isChart(inv.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	/** Right-click with the chart: set a course. */
	static InteractionResult use(ServerPlayer player, InteractionHand hand) {
		if (!isChart(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		ServerLevel level = (ServerLevel) player.level();
		ServerLevel to = Terra.across(level);
		if (to == null) {
			actionBar(player, Terra.terra(level.getServer()) == null ? "Terra Incognita didn't load on this server." : "These aren't waters the chart knows.",
				ChatFormatting.YELLOW);
			return InteractionResult.SUCCESS;
		}
		if (!atSea(player)) {
			actionBar(player, "Take it to sea first: aboard a boat or a ship, out on the water.", ChatFormatting.YELLOW);
			return InteractionResult.SUCCESS;
		}
		Map<String, Object> ship = AhoyLink.aboard(player);
		String refused = ship == null ? null : AhoyLink.refusal(ship);
		if (refused != null) {
			player.sendSystemMessage(Component.literal(refused).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		Integer sailed = VOYAGES.get(player.getUUID());
		if (sailed != null) {
			actionBar(player, "Hold your course... (" + (TerraConfig.get().voyageSeconds - sailed) + "s)", ChatFormatting.GOLD);
			return InteractionResult.SUCCESS;
		}
		VOYAGES.put(player.getUUID(), 0);
		player.sendSystemMessage(Component.literal(Terra.isTerra(to)
			? "You unfold the chart and set a course south, into the blank half. Hold it for " + TerraConfig.get().voyageSeconds + " seconds."
			: "You turn the chart over and set a course for home. Hold it for " + TerraConfig.get().voyageSeconds + " seconds.")
			.withStyle(ChatFormatting.GOLD));
		if (ship != null) {
			player.sendSystemMessage(Component.literal("The " + AhoyLink.name(ship) + " comes along, holds and all.").withStyle(ChatFormatting.GRAY));
		}
		Cmd.sound(level, "minecraft:item.book.page_turn", player.getX(), player.getY(), player.getZ(), 1f, 0.8f);
		return InteractionResult.SUCCESS;
	}

	/** Every second: voyages go on, or lose their heading. */
	static void tick(MinecraftServer server) {
		if (VOYAGES.isEmpty()) {
			return;
		}
		int need = TerraConfig.get().voyageSeconds;
		for (UUID id : List.copyOf(VOYAGES.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive()) {
				VOYAGES.remove(id);
				continue;
			}
			if (!atSea(player) || !carriesChart(player)) {
				VOYAGES.remove(id);
				actionBar(player, "The coast comes back into view. You've lost your heading.", ChatFormatting.RED);
				continue;
			}
			int sailed = VOYAGES.get(id) + 1;
			ServerLevel level = (ServerLevel) player.level();
			if (sailed >= need) {
				VOYAGES.remove(id);
				for (ServerPlayer sailor : Crossing.crew(player)) {
					VOYAGES.remove(sailor.getUUID());
				}
				if (!Crossing.cross(player)) {
					actionBar(player, "The fog closes in, then lets you go. Nothing on the other side.", ChatFormatting.YELLOW);
				}
				continue;
			}
			VOYAGES.put(id, sailed);
			if (sailed == need / 2) {
				// the fog comes in
				for (ServerPlayer sailor : Crossing.crew(player)) {
					Cmd.effect(level, sailor.getUUID().toString(), "blindness", need - sailed + 2, 0);
				}
				Cmd.sound(level, "minecraft:ambient.cave", player.getX(), player.getY(), player.getZ(), 1f, 0.5f);
			}
			actionBar(player, (sailed < need / 2 ? "The coast falls behind you... " : "Fog. Nothing but fog... ") + (need - sailed) + "s",
				sailed < need / 2 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
		}
	}

	static void reset() {
		VOYAGES.clear();
	}
}
