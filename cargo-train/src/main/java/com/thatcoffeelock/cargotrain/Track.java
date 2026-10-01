package com.thatcoffeelock.cargotrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Follows vanilla rails (any kind: normal, powered, detector, activator) one block at a time, the same way a
 * minecart would: each rail's shape says which two neighbours it connects, and a rail one block lower counts
 * too (that's the bottom of a slope). Junctions follow whatever way the rail currently points, so redstone
 * switches work.
 */
final class Track {
	/** Height of a car's wheels above the rail block's floor. */
	static final double RAIL_TOP = 0.0625;

	enum Kind { RAIL, END, UNLOADED }

	/** The result of one step along the track. {@code pos} is where we looked, even if there's no rail there. */
	record Step(Kind kind, BlockPos pos) {
	}

	/** One rail of a route. Slopes sit half a block higher in the middle. */
	record Node(BlockPos pos, boolean slope) {
		static Node of(ServerLevel level, BlockPos pos) {
			RailShape shape = shape(level.getBlockState(pos));
			return new Node(pos.immutable(), shape != null && isSlope(shape));
		}

		Vec3 point() {
			return new Vec3(pos.getX() + 0.5, pos.getY() + (slope ? 0.5 : 0) + RAIL_TOP, pos.getZ() + 0.5);
		}
	}

	private Track() {
	}

	static boolean isRail(BlockState state) {
		return state.getBlock() instanceof BaseRailBlock;
	}

	static @Nullable RailShape shape(BlockState state) {
		return state.getBlock() instanceof BaseRailBlock rail ? state.getValue(rail.getShapeProperty()) : null;
	}

	static boolean isSlope(RailShape shape) {
		return shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST
			|| shape == RailShape.ASCENDING_NORTH || shape == RailShape.ASCENDING_SOUTH;
	}

	/** The two neighbours a rail connects, as {dx, dy, dz}. Slopes go up one block on their high side. */
	static int[][] exits(RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH -> new int[][] {{0, 0, -1}, {0, 0, 1}};
			case EAST_WEST -> new int[][] {{-1, 0, 0}, {1, 0, 0}};
			case ASCENDING_EAST -> new int[][] {{-1, 0, 0}, {1, 1, 0}};
			case ASCENDING_WEST -> new int[][] {{-1, 1, 0}, {1, 0, 0}};
			case ASCENDING_NORTH -> new int[][] {{0, 1, -1}, {0, 0, 1}};
			case ASCENDING_SOUTH -> new int[][] {{0, 0, -1}, {0, 1, 1}};
			case SOUTH_EAST -> new int[][] {{0, 0, 1}, {1, 0, 0}};
			case SOUTH_WEST -> new int[][] {{0, 0, 1}, {-1, 0, 0}};
			case NORTH_WEST -> new int[][] {{0, 0, -1}, {-1, 0, 0}};
			case NORTH_EAST -> new int[][] {{0, 0, -1}, {1, 0, 0}};
			default -> new int[][] {{0, 0, -1}, {0, 0, 1}};
		};
	}

	/** Where one exit of a rail leads. Never loads a chunk: a step into an unloaded one comes back as UNLOADED. */
	static Step step(ServerLevel level, BlockPos from, int[] exit) {
		BlockPos to = from.offset(exit[0], exit[1], exit[2]);
		if (!level.isLoaded(to)) {
			return new Step(Kind.UNLOADED, to);
		}
		if (isRail(level.getBlockState(to))) {
			return new Step(Kind.RAIL, to);
		}
		if (exit[1] == 0) {
			BlockPos down = to.below();
			if (!level.isLoaded(down)) {
				return new Step(Kind.UNLOADED, down);
			}
			if (isRail(level.getBlockState(down))) {
				return new Step(Kind.RAIL, down);
			}
		}
		return new Step(Kind.END, to);
	}

	/**
	 * The rail after {@code cur}, coming from {@code prev}. If {@code cur} isn't a rail any more, that's the end
	 * of the line. If it no longer points back at {@code prev} (someone re-laid it, or a switch flipped under the
	 * train), we carry on through whichever exit leads further away from where we came from.
	 */
	static Step next(ServerLevel level, @Nullable BlockPos prev, BlockPos cur) {
		RailShape shape = shape(level.getBlockState(cur));
		if (shape == null) {
			return new Step(Kind.END, cur);
		}
		int[][] exits = exits(shape);
		Step a = step(level, cur, exits[0]);
		Step b = step(level, cur, exits[1]);
		if (prev == null) {
			return a;
		}
		if (a.pos().equals(prev)) {
			return b;
		}
		if (b.pos().equals(prev)) {
			return a;
		}
		return a.pos().distSqr(prev) >= b.pos().distSqr(prev) ? a : b;
	}
}
