package com.thatcoffeelock.sellswords;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Finding somewhere a mercenary can stand. */
final class Spots {
	private Spots() {
	}

	static boolean passable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	/** Room for feet and head, and something solid underneath. */
	static boolean standable(ServerLevel level, BlockPos pos) {
		return passable(level, pos) && passable(level, pos.above()) && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
	}

	/** The closest place to stand within {@code radius} blocks of {@code center} (sideways, and a couple of blocks up or down). */
	static BlockPos near(ServerLevel level, BlockPos center, int radius) {
		BlockPos found = find(level, center, radius);
		return found != null ? found : center.above();
	}

	static @Nullable BlockPos find(ServerLevel level, BlockPos center, int radius) {
		for (int r = 1; r <= radius; r++) {
			for (int dy : new int[] {0, 1, -1, 2, -2}) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue; // only the ring at this distance
						}
						BlockPos p = center.offset(dx, dy, dz);
						if (level.isLoaded(p) && standable(level, p)) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}
}
