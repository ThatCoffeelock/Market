package com.thatcoffeelock.havana;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Rolling cigars: the rules, and opening the Cigar Roller (hold tobacco, right-click a crafting table). */
final class Rolling {
	/** Tobacco per cigar: filler, binder and wrapper. */
	static final int LEAVES_PER_CIGAR = 3;

	/** What rolling would make from these inputs, or why it can't. */
	record Plan(@Nullable String problem, ItemStack cigars, int tobaccoUsed, int flavorUsed, ItemStack remainder) {
		static Plan no(String problem) {
			return new Plan(problem, ItemStack.EMPTY, 0, 0, ItemStack.EMPTY);
		}

		boolean ok() {
			return problem == null;
		}
	}

	private Rolling() {
	}

	/** Up to {@code wanted} cigars from the tobacco (and one flavor item per cigar, if there is one). */
	static Plan plan(ItemStack tobacco, ItemStack flavorItem, int wanted) {
		if (tobacco.isEmpty()) {
			return Plan.no("Put Cured or Aged Tobacco in the tobacco slot.");
		}
		String kind = HavanaItems.kind(tobacco);
		if (kind.equals(HavanaItems.LEAF)) {
			return Plan.no("These leaves are still green. Cure them in a Curing Barrel first.");
		}
		if (!kind.equals(HavanaItems.CURED) && !kind.equals(HavanaItems.AGED)) {
			return Plan.no("That's not tobacco. Nice try.");
		}
		HavanaItems.Flavor flavor = HavanaItems.Flavor.NONE;
		if (!flavorItem.isEmpty()) {
			flavor = HavanaItems.Flavor.of(flavorItem);
			if (flavor == null) {
				return Plan.no("That's not a flavor. Try honey, cocoa beans, sweet berries, glow berries or blaze powder.");
			}
		}
		int count = Math.min(wanted, tobacco.getCount() / LEAVES_PER_CIGAR);
		if (flavor != HavanaItems.Flavor.NONE) {
			count = Math.min(count, flavorItem.getCount());
		}
		if (count <= 0) {
			return Plan.no("You need " + LEAVES_PER_CIGAR + " tobacco per cigar.");
		}
		HavanaItems.Grade grade = kind.equals(HavanaItems.AGED) ? HavanaItems.Grade.AGED : HavanaItems.Grade.CURED;
		ItemStack remainder = flavor.remainder == null ? ItemStack.EMPTY : new ItemStack(HavanaItems.item(flavor.remainder, Items.AIR), count);
		return new Plan(null, HavanaItems.cigar(grade, flavor, count), count * LEAVES_PER_CIGAR,
			flavor == HavanaItems.Flavor.NONE ? 0 : count, remainder);
	}

	/** Hold any tobacco and right-click a crafting table: the Cigar Roller opens instead of the crafting grid. */
	static InteractionResult useTable(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (hand != InteractionHand.MAIN_HAND || !HavanaItems.isTobacco(player.getMainHandItem())
			|| !Crops.blockId(level.getBlockState(pos)).equals("crafting_table")) {
			return InteractionResult.PASS;
		}
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RollerMenu(id, player, level, pos), Component.literal("Cigar Roller")));
		return InteractionResult.SUCCESS;
	}
}
