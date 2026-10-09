package com.thatcoffeelock.sellswords;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Mercenary Stations: target blocks with a tag on the item. Remembered when placed, opened on right-click, given
 * back when broken. Colonycraft's Guildhouse registers its own (cheaper to hire at) through {@link SellswordsApi}.
 */
public final class Stations {
	static final String ITEM_TAG = "sellswords_station";
	static final Map<String, Station> ALL = new LinkedHashMap<>();

	private Stations() {
	}

	static String key(String dim, BlockPos pos) {
		return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	static @Nullable Station at(Level level, BlockPos pos) {
		return ALL.get(key(Cmd.dimId(level), pos));
	}

	static Station add(ServerLevel level, BlockPos pos, double discount, String name, boolean builtIn) {
		Station s = new Station();
		s.dim = Cmd.dimId(level);
		s.x = pos.getX();
		s.y = pos.getY();
		s.z = pos.getZ();
		s.discount = Math.max(0, Math.min(0.9, discount));
		s.name = name;
		s.builtIn = builtIn;
		ALL.put(s.key(), s);
		Mercs.changed();
		return s;
	}

	/** Forgets a station. Mercenaries idling there guard the spot instead, until they're given new orders. */
	static void remove(ServerLevel level, BlockPos pos) {
		Station s = ALL.remove(key(Cmd.dimId(level), pos));
		if (s == null) {
			return;
		}
		Mercs.changed();
		for (Merc m : Mercs.ALL.values()) {
			if (m.home.equals(s.key())) {
				m.home = "";
				if (m.orders() == Merc.Orders.STATION) {
					m.orders(Merc.Orders.GUARD);
					m.post(s.dim, s.pos().above());
				}
			}
		}
	}

	/** The station nearest to {@code pos} in the same world, or null. */
	static @Nullable Station nearest(String dim, BlockPos pos) {
		Station best = null;
		double bestDist = Double.MAX_VALUE;
		for (Station s : ALL.values()) {
			if (s.dim.equals(dim)) {
				double d = s.pos().distSqr(pos);
				if (d < bestDist) {
					bestDist = d;
					best = s;
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- the item

	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	/** Matches data/sellswords/recipe/mercenary_station.json, so crafted and given ones stack. */
	static ItemStack item() {
		ItemStack stack = new ItemStack(Items.TARGET);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ITEM_TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Mercenary Station").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Place it, then right-click it to hire", ChatFormatting.GRAY),
			text("sellswords. Idle ones patrol 50 blocks around it.", ChatFormatting.GRAY),
			text("They work for Marks. And for gold. Mostly gold.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	static boolean isItem(ItemStack stack) {
		if (!stack.is(Items.TARGET)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ITEM_TAG);
	}

	static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	// ---------------------------------------------------------------- in the world

	/** Called by the BlockItem mixin right after a block is placed from this stack. */
	public static void onPlaced(Level world, BlockPos pos, ItemStack stack, @Nullable Player player) {
		if (world instanceof ServerLevel level && isItem(stack)) {
			BlockPos at = pos.immutable();
			SellswordsMod.nextTick(() -> {
				if (level.getBlockState(at).is(Blocks.TARGET)) {
					add(level, at, 0, "Mercenary Station", false);
					if (player instanceof ServerPlayer sp) {
						sp.sendSystemMessage(Component.literal("Mercenary Station open for business. Right-click it to hire.").withStyle(ChatFormatting.GOLD));
					}
				}
			});
		}
	}

	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Station s = at(level, pos);
		if (s == null) {
			return InteractionResult.PASS;
		}
		if (!level.getBlockState(pos).is(Blocks.TARGET)) {
			remove(level, pos); // gone some other way (explosion, piston...)
			return InteractionResult.PASS;
		}
		StationMenu.open(player, level, s);
		return InteractionResult.SUCCESS;
	}

	/** Breaking a station gives the station back (not a plain target). False cancels vanilla's break. */
	static boolean allowBreak(Player player, Level world, BlockPos pos) {
		if (!(world instanceof ServerLevel level)) {
			return true;
		}
		Station s = at(level, pos);
		if (s == null) {
			return true;
		}
		remove(level, pos);
		level.removeBlock(pos, false);
		if (!player.isCreative() && !s.builtIn) {
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		return false;
	}

	/** What hiring costs at this station, in cents. */
	static long hireCost(Station s) {
		return Math.round(SellswordsConfig.get().hireCost * 100 * (1 - s.discount));
	}
}
