package com.thatcoffeelock.compagnie;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * Company Desks: cartography tables with a tag on the item. Remembered when placed, opened on right-click (anyone's
 * desk opens their own company: it's an office, not a safe), given back when broken. Colonycraft's Company House
 * registers its own through the API.
 */
public final class Desks {
	static final String ITEM_TAG = "compagnie_desk";
	static final Map<String, Desk> ALL = new LinkedHashMap<>();

	/** A desk in the world. Built-in ones (in a Company House) give nothing back when broken. */
	static final class Desk {
		String dim = "";
		int x;
		int y;
		int z;
		boolean builtIn;

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		String key() {
			return Desks.key(dim, pos());
		}
	}

	private Desks() {
	}

	static String key(String dim, BlockPos pos) {
		return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	static @Nullable Desk at(Level level, BlockPos pos) {
		return ALL.get(key(Cmd.dimId(level), pos));
	}

	static Desk add(ServerLevel level, BlockPos pos, boolean builtIn) {
		Desk d = new Desk();
		d.dim = Cmd.dimId(level);
		d.x = pos.getX();
		d.y = pos.getY();
		d.z = pos.getZ();
		d.builtIn = builtIn;
		ALL.put(d.key(), d);
		Companies.changed();
		return d;
	}

	static void remove(ServerLevel level, BlockPos pos) {
		if (ALL.remove(key(Cmd.dimId(level), pos)) != null) {
			Companies.changed();
		}
	}

	// ---------------------------------------------------------------- the item

	/** Matches data/compagnie/recipe/company_desk.json, so crafted and given ones stack. */
	static ItemStack item() {
		ItemStack stack = new ItemStack(Items.CARTOGRAPHY_TABLE);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ITEM_TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Company Desk").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Ui.t("Place it and right-click it to run your company:", ChatFormatting.GRAY),
			Ui.t("hire agents, bankroll jobs, lend money.", ChatFormatting.GRAY),
			Ui.t("Profit is a moral obligation.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	static boolean isItem(ItemStack stack) {
		if (!stack.is(Items.CARTOGRAPHY_TABLE)) {
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
			CompagnieMod.nextTick(() -> {
				if (level.getBlockState(at).is(Blocks.CARTOGRAPHY_TABLE)) {
					add(level, at, false);
					if (player instanceof ServerPlayer sp) {
						sp.sendSystemMessage(Component.literal("The Company Desk is open for business. Right-click it.").withStyle(ChatFormatting.GOLD));
					}
				}
			});
		}
	}

	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Desk d = at(level, pos);
		if (d == null) {
			return InteractionResult.PASS;
		}
		if (!level.getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)) {
			remove(level, pos); // gone some other way (explosion, piston...)
			return InteractionResult.PASS;
		}
		Company c = Companies.of(player.getUUID(), player.getName().getString());
		c.homeX = pos.getX();
		c.homeZ = pos.getZ();
		DeskMenu.open(player, c);
		return InteractionResult.SUCCESS;
	}

	/** Breaking a desk gives the desk back (not a plain table). False cancels vanilla's break. */
	static boolean allowBreak(Player player, Level world, BlockPos pos) {
		if (!(world instanceof ServerLevel level)) {
			return true;
		}
		Desk d = at(level, pos);
		if (d == null) {
			return true;
		}
		remove(level, pos);
		level.removeBlock(pos, false);
		if (!player.isCreative() && !d.builtIn) {
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		return false;
	}
}
