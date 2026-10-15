package com.thatcoffeelock.hoogheheeren;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds a rival's seat into the running world with vanilla fill and setblock commands: a walled courtyard 25 blocks
 * across with corner towers and a gate to the south, and a brick manor with a great hall (a throne, a carpet in the
 * house's colour, banquet tables, banners) and the house treasury in a chest by the high table.
 * <p>
 * Local coordinates: (0, 0, 0) is the middle of the courtyard at ground level; +z is south, toward the gate.
 */
final class Builder {
	static final int R = 12;
	/** Where the lord stands, in front of the throne, facing the door. */
	static final BlockPos LORD = new BlockPos(0, 2, -8);
	static final BlockPos CHEST = new BlockPos(5, 2, -9);
	static final BlockPos[] GATE_GUARDS = {new BlockPos(-2, 1, 10), new BlockPos(2, 1, 10)};

	private Builder() {
	}

	/** Every corner of the plot (and the middle) is loaded: safe to build. */
	static boolean loaded(ServerLevel level, int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!level.isLoaded(new BlockPos(x + dx * (R + 2), 64, z + dz * (R + 2)))) {
					return false;
				}
			}
		}
		return true;
	}

	/** The ground level for a seat at x, z: the top solid block, never below sea level (a seat at sea is an island). */
	static int ground(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return Math.max(top, level.getSeaLevel());
	}

	static void build(ServerLevel level, Rival r) {
		int cx = r.seatX;
		int cz = r.seatZ;
		int cy = ground(level, cx, cz);
		r.seatY = cy;
		B b = new B(level, cx, cy, cz);
		// the plot
		b.fill(-R, -8, -R, R, -1, R, "cobblestone");
		b.fill(-R, 0, -R, R, 0, R, "grass_block");
		b.fill(-R, 1, -R, R, 16, R, "air");
		// curtain walls and battlements
		b.fill(-R, 1, -R, R, 4, -R, "stone_bricks");
		b.fill(-R, 1, R, R, 4, R, "stone_bricks");
		b.fill(-R, 1, -R, -R, 4, R, "stone_bricks");
		b.fill(R, 1, -R, R, 4, R, "stone_bricks");
		b.fill(-R, 5, -R, R, 5, -R, "stone_brick_wall");
		b.fill(-R, 5, R, R, 5, R, "stone_brick_wall");
		b.fill(-R, 5, -R, -R, 5, R, "stone_brick_wall");
		b.fill(R, 5, -R, R, 5, R, "stone_brick_wall");
		// corner towers
		int[][] corners = {{-R, -R}, {R - 3, -R}, {-R, R - 3}, {R - 3, R - 3}};
		for (int[] c : corners) {
			b.fill(c[0], 1, c[1], c[0] + 3, 7, c[1] + 3, "stone_bricks");
			b.fill(c[0], 8, c[1], c[0] + 3, 8, c[1] + 3, "stone_brick_wall");
			b.fill(c[0] + 1, 8, c[1] + 1, c[0] + 2, 8, c[1] + 2, "air");
		}
		// the gate, and a path up to the door
		b.fill(-1, 1, R, 1, 3, R, "air");
		b.fill(-1, 0, -1, 1, 0, R, "dirt_path");
		// the manor: brick, 13 by 9, flat roof with a parapet
		b.fill(-6, 1, -10, 6, 7, -2, "bricks hollow");
		b.fill(-5, 1, -9, 5, 1, -3, "dark_oak_planks");
		int[][] pillars = {{-6, -10}, {6, -10}, {-6, -2}, {6, -2}};
		for (int[] p : pillars) {
			b.fill(p[0], 1, p[1], p[0], 8, p[1], "stripped_dark_oak_log");
		}
		b.fill(-6, 8, -10, 6, 8, -10, "brick_wall");
		b.fill(-6, 8, -2, 6, 8, -2, "brick_wall");
		b.fill(-6, 8, -10, -6, 8, -2, "brick_wall");
		b.fill(6, 8, -10, 6, 8, -2, "brick_wall");
		for (int x : new int[] {-4, -3, 3, 4}) {
			b.fill(x, 3, -2, x, 4, -2, "glass_pane");
		}
		for (int z : new int[] {-7, -4}) {
			b.fill(-6, 3, z, -6, 4, z, "glass_pane");
			b.fill(6, 3, z, 6, 4, z, "glass_pane");
		}
		b.set(0, 2, -2, "dark_oak_door[facing=north,half=lower]");
		b.set(0, 3, -2, "dark_oak_door[facing=north,half=upper]");
		// the great hall
		b.set(0, 2, -9, "dark_oak_stairs[facing=north]");
		b.fill(-4, 2, -7, -3, 2, -4, "dark_oak_slab[type=top]");
		b.fill(3, 2, -7, 4, 2, -4, "dark_oak_slab[type=top]");
		b.set(-3, 6, -6, "lantern[hanging=true]");
		b.set(3, 6, -6, "lantern[hanging=true]");
		b.set(0, 6, -4, "lantern[hanging=true]");
		b.fill(-5, 2, -9, -5, 3, -9, "bookshelf");
		b.set(4, 2, -9, "barrel[facing=up]");
		b.set(CHEST.getX(), CHEST.getY(), CHEST.getZ(), "chest[facing=south]");
		// the courtyard
		b.fill(8, 1, 6, 9, 1, 7, "hay_block");
		b.set(9, 1, 10, "barrel[facing=up]");
		b.set(-9, 1, -4, "target");
		b.fill(-8, 1, 6, -8, 5, 6, "dark_oak_fence");
		b.set(-11, 3, 0, "wall_torch[facing=east]");
		b.set(11, 3, 0, "wall_torch[facing=west]");
		b.set(-5, 3, R - 1, "wall_torch[facing=north]");
		b.set(5, 3, R - 1, "wall_torch[facing=north]");
		dress(level, r);
		r.built = true;
		Courts.changed();
		HeerenMod.LOG.info("Built {} at {} {} {}", r.seatName, cx, cy, cz);
	}

	/** Hangs the house's colours: banners, the hall carpet, the flag. A new house moving in re-dresses the seat. */
	static void dress(ServerLevel level, Rival r) {
		if (r.seatY == Integer.MIN_VALUE) {
			return;
		}
		B b = new B(level, r.seatX, r.seatY, r.seatZ);
		String c = r.colour;
		b.fill(0, 2, -7, 0, 2, -3, c + "_carpet");
		b.set(-2, 4, -9, c + "_wall_banner[facing=south]");
		b.set(2, 4, -9, c + "_wall_banner[facing=south]");
		b.set(-2, 3, R + 1, c + "_wall_banner[facing=south]");
		b.set(2, 3, R + 1, c + "_wall_banner[facing=south]");
		b.set(-8, 6, 6, c + "_banner[rotation=0]");
		r.dressed = c;
		Courts.changed();
	}

	static BlockPos at(Rival r, BlockPos local) {
		return new BlockPos(r.seatX + local.getX(), r.seatY + local.getY(), r.seatZ + local.getZ());
	}

	/** Fills and sets in local coordinates. */
	private record B(ServerLevel level, int x, int y, int z) {
		void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
			String[] parts = block.split(" ", 2);
			Cmd.run(level, "fill " + Cmd.block(x + x1, y + y1, z + z1) + " " + Cmd.block(x + x2, y + y2, z + z2) + " minecraft:" + parts[0]
				+ (parts.length > 1 ? " " + parts[1] : ""));
		}

		void set(int dx, int dy, int dz, String block) {
			Cmd.run(level, "setblock " + Cmd.block(x + dx, y + dy, z + dz) + " minecraft:" + block);
		}
	}
}
