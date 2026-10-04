package com.thatcoffeelock.ahoy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The canal drill. Where the hull would hit land, it cuts a channel as wide as the hull: two blocks of still water
 * at the ship's water level, and clear air above it up to the deck rails. Blocks are removed without drops (it's a
 * canal, not a quarry). It refuses to cut chests and other blocks with contents, and unbreakable blocks like bedrock;
 * those still stop the ship. Holes in the canal floor get a dirt bottom so the water stays put.
 */
final class Canal {
	/** Most blocks changed in one go, so a ship ploughing into a mountain can't stall the server. */
	private static final int BUDGET = 600;
	/** How high above the water the channel is cleared (the hull reaches 2.5 blocks above the surface). */
	private static final int HEADROOM = 4;

	enum Result { CLEAR, CUT, REFUSED }

	private Canal() {
	}

	/** Cuts the hull's footprint at this position. CUT if it changed blocks, REFUSED if something uncuttable is in the way. */
	static Result dig(ServerLevel level, double surface, double x, double z, float yaw) {
		int water = (int) Math.floor(surface - 0.5);
		int changed = 0;
		boolean refused = false;
		for (double segment : ShipModel.HULL_Z) {
			double[] c = Ship.toWorld(x, z, yaw, 0, segment);
			double h = ShipModel.HULL_HALF;
			for (int bx = (int) Math.floor(c[0] - h); bx <= (int) Math.floor(c[0] + h); bx++) {
				for (int bz = (int) Math.floor(c[1] - h); bz <= (int) Math.floor(c[1] + h); bz++) {
					// first check the whole column, so a chest halfway up doesn't leave half a column cut
					if (!cuttable(level, bx, bz, water)) {
						refused = true;
						continue;
					}
					for (int y = water - 1; y <= water + HEADROOM; y++) {
						BlockPos pos = new BlockPos(bx, y, bz);
						BlockState state = level.getBlockState(pos);
						boolean wet = y <= water;
						if (wet ? isStillWater(state) : state.isAir()) {
							continue;
						}
						if (changed >= BUDGET) {
							return Result.CUT;
						}
						level.setBlock(pos, wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
						changed++;
					}
					BlockPos floor = new BlockPos(bx, water - 2, bz);
					BlockState below = level.getBlockState(floor);
					if (below.isAir() || (!below.getFluidState().isEmpty() && !below.getFluidState().is(FluidTags.WATER))) {
						level.setBlock(floor, Blocks.DIRT.defaultBlockState(), 3);
						changed++;
					}
				}
			}
		}
		if (changed > 0) {
			return Result.CUT;
		}
		return refused ? Result.REFUSED : Result.CLEAR;
	}

	private static boolean cuttable(ServerLevel level, int bx, int bz, int water) {
		for (int y = water - 1; y <= water + HEADROOM; y++) {
			BlockPos pos = new BlockPos(bx, y, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || isStillWater(state)) {
				continue;
			}
			if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean isStillWater(BlockState state) {
		return state.is(Blocks.WATER) && state.getFluidState().isSource();
	}
}
