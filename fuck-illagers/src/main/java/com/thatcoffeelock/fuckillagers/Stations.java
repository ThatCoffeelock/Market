package com.thatcoffeelock.fuckillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/** Bounty Stations in the world: remembered when placed, opened on right-click, given back when broken. */
public final class Stations {
	private Stations() {
	}

	/** Called by the BlockItem mixin right after a block is placed from this stack. */
	public static void onPlaced(Level world, BlockPos pos, ItemStack stack) {
		if (world instanceof ServerLevel level && Trophies.isStation(stack)) {
			BlockPos at = pos.immutable();
			FuckIllagersMod.nextTick(() -> {
				if (level.getBlockState(at).is(Blocks.FLETCHING_TABLE)) {
					Bounties.addStation(level, at);
				}
			});
		}
	}

	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (!Bounties.isStation(level, pos)) {
			return InteractionResult.PASS;
		}
		if (!level.getBlockState(pos).is(Blocks.FLETCHING_TABLE)) {
			Bounties.removeStation(level, pos); // gone some other way (explosion, piston...)
			return InteractionResult.PASS;
		}
		StationMenu.open(player, level, pos);
		return InteractionResult.SUCCESS;
	}

	/** Breaking a station gives the station back (not a plain fletching table). False cancels vanilla's break. */
	static boolean allowBreak(Player player, Level world, BlockPos pos) {
		if (!(world instanceof ServerLevel level) || !Bounties.isStation(level, pos)) {
			return true;
		}
		Bounties.removeStation(level, pos);
		level.removeBlock(pos, false);
		if (!player.isCreative()) {
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Trophies.station());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		return false;
	}
}
