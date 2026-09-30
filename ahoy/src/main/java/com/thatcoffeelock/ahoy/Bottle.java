package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The Ship in a Bottle: a glass bottle with custom data (vanilla clients just see a glowing bottle).
 * A packed-up ship keeps its layout, name and cargo inside.
 */
public final class Bottle {
	static final String KEY = "ahoy_ship";
	static final String SAVED = "ahoy_saved";

	private Bottle() {
	}

	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack empty() {
		ItemStack stack = new ItemStack(Items.GLASS_BOTTLE);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Ship in a Bottle").withStyle(ChatFormatting.AQUA));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Right-click open water to launch a ship.", ChatFormatting.GRAY),
			text("Needs about 7 × 20 blocks of open water.", ChatFormatting.DARK_GRAY),
			text("Rename it in an anvil to name your ship.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static ItemStack packed(ServerLevel level, ShipData data) {
		ItemStack stack = empty();
		CompoundTag tag = customData(stack);
		ShipData.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), data)
			.resultOrPartial(error -> AhoyMod.LOG.error("Could not bottle ship: {}", error))
			.ifPresent(saved -> tag.put(SAVED, saved));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Ship in a Bottle: " + data.name).withStyle(ChatFormatting.AQUA));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Right-click open water to launch it.", ChatFormatting.GRAY));
		lore.add(text("Cargo: " + data.usedSlots() + " / " + (ShipData.BAY * 2) + " slots used", ChatFormatting.YELLOW));
		lore.add(text("Captain: " + (data.ownerName.isEmpty() ? "nobody yet" : data.ownerName), ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static CompoundTag customData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static boolean isBottle(ItemStack stack) {
		return stack.is(Items.GLASS_BOTTLE) && customData(stack).contains(KEY);
	}

	public static @Nullable ShipData savedData(ItemStack stack, ServerLevel level) {
		CompoundTag tag = customData(stack);
		if (!tag.contains(SAVED)) {
			return null;
		}
		return ShipData.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get(SAVED))
			.resultOrPartial(error -> AhoyMod.LOG.error("Could not unbottle ship: {}", error))
			.orElse(null);
	}

	/** The name given in an anvil, if any. */
	public static @Nullable String customName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}

	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
