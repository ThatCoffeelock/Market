package com.thatcoffeelock.specerij;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The climate a spice plant stands in. At home (its own biome) it grows at full speed. In a Hortus, a glasshouse like
 * the one in Amsterdam that smuggled coffee out of Yemen, it grows anywhere, at half speed. A Hortus is nothing to
 * craft: a plant counts as in one when the first thing above it is glass and there's a fire going nearby (a lit
 * campfire, furnace, smoker or blast furnace, or lava or magma, within 4 blocks). Anywhere else it doesn't grow.
 */
final class Hortus {
	static final int ROOF_REACH = 12;
	static final int HEAT_REACH = 4;

	private Hortus() {
	}

	/** The biome's id at this spot, e.g. "minecraft:jungle". Never loads a chunk. */
	static String biome(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).getRegisteredName();
	}

	static boolean isHome(ServerLevel level, BlockPos pos, Spice spice) {
		String biome = biome(level, pos);
		return spice.homes.contains(biome.substring(biome.indexOf(':') + 1));
	}

	/** The first block above with anything in it is glass (or a pane, or stained or tinted glass). */
	static boolean underGlass(ServerLevel level, BlockPos pos) {
		for (int dy = 1; dy <= ROOF_REACH; dy++) {
			BlockState state = level.getBlockState(pos.above(dy));
			if (state.isAir()) {
				continue;
			}
			return Gardens.blockId(state).contains("glass");
		}
		return false;
	}

	/** A fire going within reach: what keeps the tropics warm in a Dutch winter. */
	static boolean heated(ServerLevel level, BlockPos pos) {
		for (int dx = -HEAT_REACH; dx <= HEAT_REACH; dx++) {
			for (int dz = -HEAT_REACH; dz <= HEAT_REACH; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockState state = level.getBlockState(pos.offset(dx, dy, dz));
					String id = Gardens.blockId(state);
					if (id.equals("lava") || id.equals("magma_block")) {
						return true;
					}
					if ((id.equals("campfire") || id.equals("soul_campfire") || id.equals("furnace") || id.equals("smoker") || id.equals("blast_furnace"))
						&& state.toString().contains("lit=true")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static boolean isHortus(ServerLevel level, BlockPos pos) {
		return underGlass(level, pos) && heated(level, pos);
	}

	/** How fast a plant of this spice grows here: 1 at home, 1/slowdown in a Hortus, 0 anywhere else. */
	static double rate(ServerLevel level, BlockPos pos, Spice spice) {
		if (isHome(level, pos, spice)) {
			return 1;
		}
		return isHortus(level, pos) ? 1 / SpiceConfig.get().hortusSlowdown : 0;
	}
}
