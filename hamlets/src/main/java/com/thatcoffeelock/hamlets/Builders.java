package com.thatcoffeelock.hamlets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import static com.thatcoffeelock.hamlets.Canvas.AIR;
import static com.thatcoffeelock.hamlets.Canvas.crop;
import static com.thatcoffeelock.hamlets.Canvas.facing;
import static com.thatcoffeelock.hamlets.Canvas.farmland;
import static com.thatcoffeelock.hamlets.Canvas.hanging;
import static com.thatcoffeelock.hamlets.Canvas.slab;
import static com.thatcoffeelock.hamlets.Canvas.st;
import static com.thatcoffeelock.hamlets.Canvas.stairs;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

/** The blueprints. Local frame: y 0 is the ground, the front faces south (+z). */
final class Builders {
	/** Floor level of the dungeon's rooms, relative to the surface. */
	static final int DUNGEON_FLOOR = -16;

	/** Villagers pick up the job that goes with the workstation they find. */
	private static final List<Block> WORKSTATIONS = List.of(
		Blocks.LECTERN, Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.LOOM, Blocks.SMOKER,
		Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.BREWING_STAND,
		Blocks.BARREL, Blocks.CAULDRON);
	private static final List<Block> CROPS = List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS);
	private static final List<Block> FLOWERS = List.of(Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET);

	private Builders() {
	}

	static void build(Canvas c, Plan plan) {
		switch (plan) {
			case COTTAGE -> cottage(c);
			case CASTLE -> castle(c);
			case DUNGEON -> dungeon(c);
		}
		c.finish();
	}

	// ---------------------------------------------------------------- cottage

	private static void cottage(Canvas c) {
		Style s = c.style;
		boolean ruin = c.ruined;
		c.clear(-8, 1, -8, 8, 10, 8);
		c.foundation(-4, -3, 4, 3, st(s.stone));
		c.foundation(-8, -8, 8, 8, st(s.fill));
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				c.set(x, 0, z, ruin && c.rng.nextFloat() < 0.35f ? st(Blocks.COARSE_DIRT) : st(s.ground));
			}
		}

		// the house: 9 x 7, stone plinth, plank walls, log corners
		c.fill(-4, 0, -3, 4, 0, 3, st(s.stone));
		c.fill(-3, 0, -2, 3, 0, 2, st(s.planks));
		for (int y = 1; y <= 4; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -3; z <= 3; z++) {
					boolean wall = Math.abs(x) == 4 || Math.abs(z) == 3;
					if (!wall || (y == 4 && Math.abs(z) != 3)) {
						continue;
					}
					boolean corner = Math.abs(x) == 4 && Math.abs(z) == 3;
					BlockState b = corner ? st(s.log) : y == 1 ? st(s.stone) : st(s.planks);
					c.set(x, y, z, c.worn(b, corner || y == 1 ? 0f : 0.07f));
				}
			}
		}
		for (int x : new int[] {-2, 2}) {
			c.window(x, 2, -3);
			c.window(x, 2, 3);
		}
		c.window(-4, 2, 0);
		c.window(4, 2, 0);
		if (!ruin || c.rng.nextFloat() < 0.4f) {
			c.door(0, 1, 3, s.door, SOUTH);
		} else {
			c.clear(0, 1, 3, 0, 2, 3);
		}

		// gable roof along x, eaves overhanging by one
		for (int k = 0; k <= 3; k++) {
			for (int x = -5; x <= 5; x++) {
				c.set(x, 4 + k, -4 + k, c.worn(stairs(s.roof, SOUTH, false), 0.03f));
				c.set(x, 4 + k, 4 - k, c.worn(stairs(s.roof, NORTH, false), 0.03f));
			}
			for (int z = -(3 - k); z <= 3 - k; z++) {
				c.set(-4, 4 + k, z, c.worn(st(s.planks), 0.05f));
				c.set(4, 4 + k, z, c.worn(st(s.planks), 0.05f));
			}
		}
		for (int x = -5; x <= 5; x++) {
			c.set(x, 8, 0, slab(s.roofSlab, false));
		}

		if (!ruin) {
			c.bed(-3, 1, -1, s.bed, NORTH);
			c.bed(-1, 1, -1, s.bed, NORTH);
			c.set(3, 1, -2, facing(c.pick(WORKSTATIONS), SOUTH));
			c.chest(3, 1, 0, WEST, s.houseLoot);
			c.set(3, 1, 2, st(Blocks.CRAFTING_TABLE));
			c.set(-3, 1, 2, st(Blocks.POTTED_POPPY));
			c.set(0, 3, -2, facing(Blocks.WALL_TORCH, SOUTH));
			garden(c);
			c.villager(0, 1, 0, false);
			c.villager(1, 1, 1, false);
			if (c.rng.nextFloat() < 0.4f) {
				c.villager(-1, 1, 1, true);
			}
			if (c.rng.nextFloat() < 0.5f) {
				c.spawn(EntityTypes.CAT, 2, 1, 1);
			}
		} else {
			// someone was brewing something they shouldn't have
			c.set(-3, 1, -2, st(Blocks.CAULDRON));
			c.set(-2, 1, -2, st(Blocks.BREWING_STAND));
			c.chest(3, 1, 0, WEST, BuiltInLootTables.SIMPLE_DUNGEON);
			for (int x : new int[] {-3, 3}) {
				for (int z : new int[] {-2, 2}) {
					if (c.rng.nextFloat() < 0.7f) {
						c.set(x, 3, z, st(Blocks.COBWEB));
					}
				}
			}
			garden(c);
			c.set(1, 1, 4, facing(Blocks.CARVED_PUMPKIN, SOUTH));
			c.spawn(EntityTypes.ZOMBIE_VILLAGER, 0, 1, 0); // a former resident. Curable, if you're kind
			c.spawn(EntityTypes.ZOMBIE, 1, 1, -1);
			c.spawn(c.rng.nextFloat() < 0.5f ? EntityTypes.WITCH : EntityTypes.SKELETON, -1, 1, 1);
		}
	}

	/** Path to the door and two vegetable plots in front of the house. */
	private static void garden(Canvas c) {
		Style s = c.style;
		boolean ruin = c.ruined;
		for (int z = 4; z <= 8; z++) {
			c.set(0, 0, z, st(ruin ? Blocks.GRAVEL : Blocks.DIRT_PATH));
		}
		for (int side : new int[] {-1, 1}) {
			Block crop = c.pick(CROPS);
			for (int i = 2; i <= 6; i++) {
				for (int z = 5; z <= 7; z++) {
					int x = side * i;
					if (i == 4 && z == 6) {
						c.set(x, 0, z, st(Blocks.WATER));
					} else if (ruin) {
						c.set(x, 0, z, st(Blocks.COARSE_DIRT));
						c.set(x, 1, z, c.rng.nextFloat() < 0.3f ? st(Blocks.DEAD_BUSH) : AIR);
					} else {
						c.set(x, 0, z, farmland());
						c.set(x, 1, z, crop(crop, 2 + c.rng.nextInt(6)));
					}
				}
			}
		}
		if (!ruin) {
			c.set(-1, 1, 5, st(Blocks.COMPOSTER));
			if (s.ground == Blocks.GRASS_BLOCK) {
				for (int x = -8; x <= 8; x++) {
					for (int z = -8; z <= -6; z++) {
						if (c.rng.nextFloat() < 0.1f) {
							c.set(x, 1, z, st(c.pick(FLOWERS)));
						}
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------- castle

	private static void castle(Canvas c) {
		Style s = c.style;
		boolean ruin = c.ruined;
		c.clear(-17, 1, -17, 17, 16, 17);
		c.foundation(-17, -17, 17, 17, st(s.stone));
		for (int x = -17; x <= 17; x++) {
			for (int z = -17; z <= 17; z++) {
				BlockState g = st(s.ground);
				if (ruin) {
					float r = c.rng.nextFloat();
					g = r < 0.3f ? st(Blocks.COARSE_DIRT) : r < 0.45f ? st(Blocks.GRAVEL) : g;
				}
				c.set(x, 0, z, g);
			}
		}
		for (int z = 2; z <= 17; z++) {
			for (int x = -1; x <= 1; x++) {
				c.set(x, 0, z, st(ruin ? Blocks.GRAVEL : Blocks.DIRT_PATH));
			}
		}

		// curtain wall: two thick, walkway on the inner ring, merlons on the outer
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				if (ring != 13 && ring != 14) {
					continue;
				}
				for (int y = 0; y <= 6; y++) {
					c.set(x, y, z, c.worn(st(s.bricks), y == 0 ? 0f : 0.05f));
				}
				if (ring == 14) {
					c.set(x, 7, z, c.worn(st(s.bricks), 0.2f));
					if (((x + z) & 1) == 0) {
						c.set(x, 8, z, c.worn(st(s.bricks), 0.35f));
					}
				}
			}
		}
		c.clear(-1, 1, 13, 1, 3, 14); // the gate

		int[] sx = {-1, 1, -1, 1};
		int[] sz = {-1, -1, 1, 1};
		for (int i = 0; i < 4; i++) {
			tower(c, i, sx[i], sz[i]);
		}
		keep(c);
		courtyard(c);
	}

	private static void tower(Canvas c, int index, int sx, int sz) {
		Style s = c.style;
		boolean ruin = c.ruined;
		int tx = 14 * sx;
		int tz = 14 * sz;
		int cut = ruin ? c.rng.nextInt(5) : 0;
		int top = 11 - cut;
		for (int x = tx - 3; x <= tx + 3; x++) {
			for (int z = tz - 3; z <= tz + 3; z++) {
				boolean edge = Math.abs(x - tx) == 3 || Math.abs(z - tz) == 3;
				for (int y = 0; y <= top; y++) {
					if (edge) {
						c.set(x, y, z, c.worn(st(s.bricks), y == 0 ? 0f : 0.04f));
					} else if (y == 0 || y == 6 || (y == 11 && cut == 0)) {
						c.set(x, y, z, st(y == 11 ? s.bricks : s.planks));
					} else {
						c.set(x, y, z, AIR);
					}
				}
				if (edge) {
					if (cut == 0 && ((x + z) & 1) == 0) {
						c.set(x, 12, z, st(s.bricks));
					} else if (cut > 0 && c.rng.nextFloat() < 0.4f) {
						c.set(x, top + 1, z, c.worn(st(s.bricks), 0f)); // ragged broken top
					}
				}
			}
		}
		// arrow slits
		for (int y : new int[] {3, 8}) {
			if (y < top) {
				c.set(tx + 3 * sx, y, tz, AIR);
				c.set(tx, y, tz + 3 * sz, AIR);
			}
		}
		// door to the courtyard, ladder in the far corner
		int dx = tx - 3 * sx;
		int dz = tz - 2 * sz;
		if (ruin) {
			c.clear(dx, 1, dz, dx, 2, dz);
		} else {
			c.door(dx, 1, dz, s.door, Canvas.dir(-sx, 0));
		}
		c.ladder(tx + 2 * sx, 1, cut == 0 ? 11 : top, tz + 2 * sz, Canvas.dir(-sx, 0));

		if (!ruin) {
			c.set(tx, 5, tz, hanging(Blocks.LANTERN));
			if (index == 0) {
				c.chest(tx, 1, tz, Canvas.dir(-sx, 0), BuiltInLootTables.VILLAGE_ARMORER);
			}
		} else {
			if (index == 0) {
				c.chest(tx, 1, tz, Canvas.dir(-sx, 0), BuiltInLootTables.PILLAGER_OUTPOST);
			}
			c.spawn(EntityTypes.SKELETON, tx - sx, 1, tz - sz);
		}
	}

	private static void keep(Canvas c) {
		Style s = c.style;
		boolean ruin = c.ruined;
		int x1 = -6;
		int x2 = 6;
		int z1 = -9;
		int z2 = 1;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
				for (int y = 0; y <= 10; y++) {
					if (edge) {
						c.set(x, y, z, c.worn(st(s.bricks), y == 0 ? 0f : 0.03f));
					} else if (y == 0 || y == 5) {
						c.set(x, y, z, st(s.planks));
					} else if (y == 10) {
						c.set(x, y, z, st(s.bricks));
					} else {
						c.set(x, y, z, AIR);
					}
				}
				if (edge && ((x + z) & 1) == 0) {
					c.set(x, 11, z, c.worn(st(s.bricks), 0.3f));
				}
			}
		}
		for (int y : new int[] {3, 7}) {
			for (int x : new int[] {-4, -2, 2, 4}) {
				c.window(x, y, z1);
				c.window(x, y, z2);
			}
			for (int z : new int[] {-7, -4, -1}) {
				c.window(x1, y, z);
				c.window(x2, y, z);
			}
		}
		if (ruin) {
			c.clear(0, 1, z2, 0, 2, z2);
		} else {
			c.door(0, 1, z2, s.door, SOUTH);
		}
		c.ladder(5, 1, 10, -8, WEST);

		if (!ruin) {
			for (int[] l : new int[][] {{-3, -4}, {3, -4}, {0, -1}}) {
				c.set(l[0], 4, l[1], hanging(Blocks.LANTERN));
				c.set(l[0], 9, l[1], hanging(Blocks.LANTERN));
			}
			// great hall: a long table and the workshops along the walls
			c.fill(-2, 1, -5, 2, 1, -4, slab(s.slab, true));
			List<Block> jobs = new ArrayList<>(WORKSTATIONS);
			Collections.shuffle(jobs, new Random(c.rng.nextLong()));
			int j = 0;
			for (int z : new int[] {-7, -5, -3, -1}) {
				c.set(-5, 1, z, facing(jobs.get(j++), EAST));
			}
			for (int z : new int[] {-6, -4, -2}) {
				c.set(5, 1, z, facing(jobs.get(j++), WEST));
			}
			c.villager(-2, 1, -2, false);
			c.villager(2, 1, -2, false);
			c.villager(0, 1, -7, false);
			c.villager(-3, 1, -6, false);
			// bedrooms upstairs
			for (int z : new int[] {-7, -5, -3}) {
				c.bed(-4, 6, z, s.bed, WEST);
			}
			for (int z : new int[] {-6, -4, -2}) {
				c.bed(4, 6, z, s.bed, EAST);
			}
			c.fill(0, 6, -7, 0, 6, -1, st(Style.block("red_carpet")));
			c.chest(-1, 6, -8, SOUTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);
			c.chest(1, 6, -8, SOUTH, s.houseLoot);
			c.villager(-2, 6, -1, false);
			c.villager(2, 6, -1, true);
		} else {
			c.spawner(0, 1, -4, c.rng.nextBoolean() ? EntityTypes.SKELETON : EntityTypes.ZOMBIE);
			for (int i = 0; i < 12; i++) {
				int x = -5 + c.rng.nextInt(10);
				int z = -8 + c.rng.nextInt(9);
				int y = c.rng.nextBoolean() ? 4 : 9;
				c.set(x, y, z, st(Blocks.COBWEB));
			}
			c.set(-2, 1, -6, slab(s.slab, true));
			c.set(1, 1, -6, slab(s.slab, true));
			c.chest(-5, 1, -8, EAST, BuiltInLootTables.SIMPLE_DUNGEON);
			c.chest(0, 6, -8, SOUTH, BuiltInLootTables.STRONGHOLD_CORRIDOR);
			c.spawn(EntityTypes.ZOMBIE, -2, 1, -2);
			c.spawn(EntityTypes.ZOMBIE, 2, 1, -2);
			c.spawn(EntityTypes.SKELETON, 0, 1, -7);
			c.spawn(EntityTypes.SKELETON, -3, 6, -4);
			c.spawn(EntityTypes.SKELETON, 3, 6, -4);
		}
	}

	private static void courtyard(Canvas c) {
		Style s = c.style;
		boolean ruin = c.ruined;
		// vegetable garden against the west wall
		Block crop = c.pick(CROPS);
		for (int x = -11; x <= -7; x++) {
			for (int z = 4; z <= 10; z++) {
				if (x == -9 && z == 7) {
					c.set(x, 0, z, st(Blocks.WATER));
				} else if (ruin) {
					c.set(x, 0, z, st(Blocks.COARSE_DIRT));
					c.set(x, 1, z, c.rng.nextFloat() < 0.25f ? st(Blocks.DEAD_BUSH) : AIR);
				} else {
					c.set(x, 0, z, farmland());
					c.set(x, 1, z, crop(crop, 2 + c.rng.nextInt(6)));
				}
			}
		}
		// well
		c.set(9, 0, 6, st(Blocks.WATER));
		c.set(9, -1, 6, st(Blocks.WATER));
		for (int x = 8; x <= 10; x++) {
			for (int z = 5; z <= 7; z++) {
				if (x != 9 || z != 6) {
					c.set(x, 1, z, c.worn(st(s.bricks), 0.2f));
				}
			}
		}
		c.set(9, 1, 10, st(Blocks.HAY_BLOCK));
		c.set(10, 1, 10, st(Blocks.HAY_BLOCK));
		c.set(10, 2, 10, st(Blocks.HAY_BLOCK));
		c.set(10, 1, 9, st(Blocks.HAY_BLOCK));

		if (!ruin) {
			c.set(-6, 1, 4, st(Blocks.COMPOSTER));
			c.set(2, 1, 5, st(Blocks.BELL));
			for (int[] p : new int[][] {{-3, 3}, {3, 3}, {-3, 10}, {3, 10}}) {
				c.set(p[0], 1, p[1], st(s.fence));
				c.set(p[0], 2, p[1], st(Blocks.LANTERN));
			}
			c.villager(-6, 1, 6, false);
			c.spawn(EntityTypes.IRON_GOLEM, 4, 1, 8);
			if (c.rng.nextFloat() < 0.6f) {
				c.spawn(EntityTypes.CAT, -4, 1, 8);
			}
		} else {
			// bandits moved in and made themselves at home
			c.set(6, 1, 7, st(Blocks.CAMPFIRE));
			// fallen masonry, kept off the path and away from where the bandits stand
			for (int i = 0; i < 14; i++) {
				int x = -12 + c.rng.nextInt(25);
				int z = 3 + c.rng.nextInt(9);
				BlockState rubble = c.worn(st(s.bricks), 0f);
				boolean taken = Math.abs(x) <= 1 || (x == 5 && z == 5) || (x == 7 && z == 9) || (x == -4 && z == 7) || (x == 6 && z == 7);
				if (!taken) {
					c.set(x, 1, z, rubble);
				}
			}
			c.spawn(EntityTypes.PILLAGER, 5, 1, 5);
			c.spawn(EntityTypes.PILLAGER, 7, 1, 9);
			c.spawn(EntityTypes.VINDICATOR, -4, 1, 7);
		}
	}

	// ---------------------------------------------------------------- dungeon

	private enum RoomKind {
		PRISON, CRYPT, TREASURY, LIBRARY
	}

	/** Stair cells around the shaft's centre pillar, going down. */
	private static final int[][] RING = {{0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}};

	private static void dungeon(Canvas c) {
		Style s = c.style;
		int f = DUNGEON_FLOOR;
		BlockState brick = st(s.bricks);

		// the crypt on the surface
		c.clear(-3, 1, -3, 3, 7, 3);
		c.foundation(-3, -3, 3, 3, brick);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
				for (int y = 0; y <= 4; y++) {
					if (edge || y == 0) {
						c.set(x, y, z, c.worn(brick, 0f));
					}
				}
				c.set(x, 5, z, c.worn(slab(s.brickSlab, false), 0.05f));
			}
		}
		c.clear(0, 1, 3, 0, 2, 3);
		c.set(0, 5, 3, brick);
		c.set(0, 6, 3, facing(Blocks.SKELETON_SKULL, NORTH));
		c.set(0, 4, 0, hanging(Blocks.SOUL_LANTERN));

		// underground: brick shells first, then hollow them out, so caves and water stay outside
		List<int[]> parts = new ArrayList<>();
		parts.add(new int[] {-4, f + 1, -4, 4, f + 4, 4}); // hub
		int[][] outs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int[] o : outs) {
			Room corridor = new Room(o[0], o[1]);
			parts.add(corridor.box(-3, -1, -1, 1, f + 1, f + 3));
			parts.add(corridor.box(0, -4, 6, 4, f + 1, f + 4));
		}
		for (int[] p : parts) {
			c.wornFill(p[0] - 1, p[1] - 1, p[2] - 1, p[3] + 1, p[4] + 1, p[5] + 1, brick, 0f);
		}
		for (int[] p : parts) {
			c.clear(p[0], p[1], p[2], p[3], p[4], p[5]);
		}

		// the shaft: a spiral stair round a centre pillar
		for (int y = -1; y >= f + 5; y--) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2) {
						c.set(x, y, z, c.worn(brick, 0f));
					}
				}
			}
		}
		c.clear(-1, -1, -1, 1, f + 1, 1);
		c.fill(0, -1, 0, 0, f + 1, 0, brick);
		for (int i = 0; -1 - i >= f + 1; i++) {
			int[] cell = RING[i % RING.length];
			int[] prev = i == 0 ? new int[] {0, 2} : RING[(i - 1) % RING.length];
			c.set(cell[0], -1 - i, cell[1], stairs(s.brickStairs, Canvas.dir(prev[0] - cell[0], prev[1] - cell[1]), false));
		}
		c.clear(RING[0][0], 0, RING[0][1], RING[0][0], 0, RING[0][1]);
		c.clear(RING[1][0], 0, RING[1][1], RING[1][0], 0, RING[1][1]);
		c.set(0, 0, 0, brick);

		// the hub
		for (int[] corner : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			c.set(corner[0], f + 4, corner[1], st(Blocks.COBWEB));
			if (c.rng.nextBoolean()) {
				c.set(corner[0], f + 1, corner[1], st(Blocks.BONE_BLOCK));
			}
		}
		c.spawn(EntityTypes.ZOMBIE, 3, f + 1, -2);

		List<RoomKind> kinds = new ArrayList<>(List.of(RoomKind.values()));
		Collections.shuffle(kinds, new Random(c.rng.nextLong()));
		for (int i = 0; i < 4; i++) {
			Room room = new Room(outs[i][0], outs[i][1]);
			room.torch(c, -2, f + 2);
			switch (kinds.get(i)) {
				case PRISON -> prison(c, room, f);
				case CRYPT -> crypt(c, room, f);
				case TREASURY -> treasury(c, room, f);
				case LIBRARY -> library(c, room, f);
			}
		}
	}

	/**
	 * A room off the hub, in its own frame: u is the depth (0 at the corridor, 6 at the far wall),
	 * v runs sideways from -4 to 4. Negative u is the corridor leading in.
	 */
	private record Room(int dx, int dz) {
		int x(int u, int v) {
			return dx * (8 + u) - dz * v;
		}

		int z(int u, int v) {
			return dz * (8 + u) + dx * v;
		}

		/** Towards the hub. */
		Direction back() {
			return Canvas.dir(-dx, -dz);
		}

		/** Towards +v. */
		Direction side() {
			return Canvas.dir(-dz, dx);
		}

		int[] box(int u1, int v1, int u2, int v2, int y1, int y2) {
			return new int[] {
				Math.min(x(u1, v1), x(u2, v2)), y1, Math.min(z(u1, v1), z(u2, v2)),
				Math.max(x(u1, v1), x(u2, v2)), y2, Math.max(z(u1, v1), z(u2, v2))};
		}

		void set(Canvas c, int u, int y, int v, BlockState state) {
			c.set(x(u, v), y, z(u, v), state);
		}

		void spawn(Canvas c, EntityType<?> type, int u, int y, int v) {
			c.spawn(type, x(u, v), y, z(u, v));
		}

		void chest(Canvas c, int u, int y, int v, Direction facing, ResourceKey<LootTable> loot) {
			c.chest(x(u, v), y, z(u, v), facing, loot);
		}

		/** A soul torch on the corridor wall. */
		void torch(Canvas c, int u, int y) {
			set(c, u, y, -1, facing(Blocks.SOUL_WALL_TORCH, side()));
		}
	}

	private static void prison(Canvas c, Room r, int f) {
		BlockState brick = st(c.style.bricks);
		for (int y = f + 1; y <= f + 4; y++) {
			for (int u = 5; u <= 6; u++) {
				r.set(c, u, y, -2, c.worn(brick, 0f));
				r.set(c, u, y, 2, c.worn(brick, 0f));
			}
			for (int v = -4; v <= 4; v++) {
				r.set(c, 4, y, v, Math.abs(v) == 2 ? c.worn(brick, 0f) : st(Blocks.IRON_BARS));
			}
		}
		Direction out = r.back();
		for (int v : new int[] {-3, 0, 3}) {
			BlockState door = facing(Blocks.IRON_DOOR, out);
			r.set(c, 4, f + 1, v, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			r.set(c, 4, f + 2, v, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			r.set(c, 3, f + 1, v, facing(Blocks.STONE_BUTTON, out)); // press to free them
			r.set(c, 5, f + 4, v, hanging(Blocks.SOUL_LANTERN)); // a little light, so nothing spawns in the cells
		}
		// the prisoners: always someone in the middle cell
		r.spawn(c, EntityTypes.VILLAGER, 5, f + 1, 0);
		if (c.rng.nextFloat() < 0.6f) {
			r.spawn(c, EntityTypes.VILLAGER, 5, f + 1, -3);
		}
		r.spawn(c, c.rng.nextFloat() < 0.4f ? EntityTypes.ZOMBIE_VILLAGER : EntityTypes.VILLAGER, 5, f + 1, 3);
		// the guards
		r.spawn(c, EntityTypes.ZOMBIE, 1, f + 1, -2);
		r.spawn(c, EntityTypes.SKELETON, 1, f + 1, 2);
		r.chest(c, 0, f + 1, 4, r.back(), BuiltInLootTables.SIMPLE_DUNGEON);
	}

	private static void crypt(Canvas c, Room r, int f) {
		BlockState brick = st(c.style.bricks);
		for (int u : new int[] {1, 4}) {
			for (int side : new int[] {-1, 1}) {
				r.set(c, u, f + 1, side * 4, c.worn(brick, 0f));
				r.set(c, u + 1, f + 1, side * 4, c.worn(brick, 0f));
				if (c.rng.nextFloat() < 0.4f) {
					r.set(c, u, f + 2, side * 4, facing(Blocks.SKELETON_SKULL, NORTH));
				}
			}
		}
		List<EntityType<?>> mobs = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER);
		c.spawner(r.x(3, 0), f + 1, r.z(3, 0), mobs.get(c.rng.nextInt(mobs.size())));
		r.chest(c, 6, f + 1, -3, r.back(), BuiltInLootTables.SIMPLE_DUNGEON);
		r.chest(c, 6, f + 1, 3, r.back(), BuiltInLootTables.SIMPLE_DUNGEON);
		r.set(c, 6, f + 4, -4, st(Blocks.COBWEB));
		r.set(c, 6, f + 4, 4, st(Blocks.COBWEB));
		r.spawn(c, EntityTypes.SKELETON, 2, f + 1, -2);
		r.spawn(c, EntityTypes.SKELETON, 2, f + 1, 2);
	}

	private static void treasury(Canvas c, Room r, int f) {
		BlockState brick = st(c.style.bricks);
		for (int u : new int[] {1, 5}) {
			for (int v : new int[] {-2, 2}) {
				for (int y = f + 1; y <= f + 4; y++) {
					r.set(c, u, y, v, c.worn(brick, 0f));
				}
			}
		}
		r.chest(c, 6, f + 1, -2, r.back(), BuiltInLootTables.STRONGHOLD_CORRIDOR);
		r.chest(c, 6, f + 1, 0, r.back(), BuiltInLootTables.BURIED_TREASURE);
		r.chest(c, 6, f + 1, 2, r.back(), BuiltInLootTables.SIMPLE_DUNGEON);
		if (c.rng.nextFloat() < 0.5f) {
			r.set(c, 6, f + 1, -4, st(Blocks.GOLD_BLOCK));
		}
		r.spawn(c, EntityTypes.VINDICATOR, 3, f + 1, 0); // the treasurer
		r.spawn(c, EntityTypes.SKELETON, 2, f + 1, -3);
		r.spawn(c, EntityTypes.ZOMBIE, 2, f + 1, 3);
	}

	private static void library(Canvas c, Room r, int f) {
		for (int u = 0; u <= 6; u++) {
			for (int y = f + 1; y <= f + 3; y++) {
				r.set(c, u, y, -4, c.rng.nextFloat() < 0.15f ? st(Blocks.COBWEB) : st(Blocks.BOOKSHELF));
				r.set(c, u, y, 4, c.rng.nextFloat() < 0.15f ? st(Blocks.COBWEB) : st(Blocks.BOOKSHELF));
			}
		}
		r.set(c, 6, f + 1, 0, facing(Blocks.LECTERN, r.back()));
		r.set(c, 6, f + 1, -2, st(Blocks.BREWING_STAND));
		r.set(c, 6, f + 1, 2, st(Blocks.CAULDRON));
		r.chest(c, 6, f + 1, 1, r.back(), BuiltInLootTables.STRONGHOLD_LIBRARY);
		r.spawn(c, EntityTypes.WITCH, 3, f + 1, 0);
		r.spawn(c, EntityTypes.SKELETON, 2, f + 1, 2);
	}
}
