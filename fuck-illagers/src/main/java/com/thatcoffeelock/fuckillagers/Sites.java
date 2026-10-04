package com.thatcoffeelock.fuckillagers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import static com.thatcoffeelock.fuckillagers.Canvas.facing;
import static com.thatcoffeelock.fuckillagers.Canvas.hanging;
import static com.thatcoffeelock.fuckillagers.Canvas.st;

/**
 * The six hideouts. Built on the spot when a player comes near (see {@link Bounties#tick}), on levelled ground,
 * turned a random way. Local coordinates: y 0 is the first block above the ground, the front faces +z.
 */
final class Sites {
	private static final Random RANDOM = new Random();

	/** What got built: the ground height, the boss, and where the loot chests are (for the tests). */
	record Built(int y, @Nullable Mob boss, List<BlockPos> chests) {
	}

	private Sites() {
	}

	/** How far a site reaches from its middle, in blocks. */
	static int radius(Tier.Site site) {
		return switch (site) {
			case WAGON -> 7;
			case TOWER -> 5;
			case CAMP, FORTRESS -> 11;
			case DUNGEON -> 15;
			case CASTLE -> 17;
		};
	}

	/** Is the whole area loaded (so building doesn't generate or load chunks)? */
	static boolean loaded(ServerLevel level, Contract c) {
		int r = radius(c.site()) + 1;
		int[][] corners = {{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}};
		for (int[] d : corners) {
			if (!level.isLoaded(new BlockPos(c.x + d[0], 64, c.z + d[1]))) {
				return false;
			}
		}
		return true;
	}

	/** Ground height: the middle of the heights measured across the site. */
	static int ground(ServerLevel level, int x, int z, int r) {
		List<Integer> heights = new ArrayList<>();
		for (int dx = -r; dx <= r; dx += Math.max(1, r / 2)) {
			for (int dz = -r; dz <= r; dz += Math.max(1, r / 2)) {
				heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz));
			}
		}
		heights.sort(Integer::compare);
		return heights.get(heights.size() / 2);
	}

	static Built build(ServerLevel level, Contract c) {
		Tier.Site site = c.site();
		int r = radius(site);
		int y = ground(level, c.x, c.z, r);
		BlockPos origin = new BlockPos(c.x, y, c.z);
		BoundingBox clip = new BoundingBox(c.x - r - 2, level.getMinY() + 1, c.z - r - 2, c.x + r + 2, level.getMaxY() - 1, c.z + r + 2);
		Rotation turn = Rotation.values()[RANDOM.nextInt(4)];
		Canvas cv = new Canvas(level, clip, origin, turn, RANDOM.nextLong(), false, true);
		Plan plan = new Plan(cv, c, new ArrayList<>());
		switch (site) {
			case WAGON -> wagon(plan);
			case TOWER -> tower(plan);
			case CAMP -> camp(plan);
			case FORTRESS -> fortress(plan);
			case DUNGEON -> dungeon(plan);
			case CASTLE -> castle(plan);
		}
		cv.finish();
		return new Built(y, plan.boss, plan.chests);
	}

	/** One build in progress. */
	private static final class Plan {
		final Canvas cv;
		final Contract contract;
		final List<BlockPos> chests;
		@Nullable Mob boss;

		Plan(Canvas cv, Contract contract, List<BlockPos> chests) {
			this.cv = cv;
			this.contract = contract;
			this.chests = chests;
		}

		void chest(int x, int y, int z, Direction d, ResourceKey<LootTable> loot) {
			cv.chest(x, y, z, d, loot);
			chests.add(cv.at(x, y, z));
		}

		void barrel(int x, int y, int z, ResourceKey<LootTable> loot) {
			cv.barrel(x, y, z, loot);
			chests.add(cv.at(x, y, z));
		}

		/** Walls only (no floor or roof). */
		void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
			for (int y = y1; y <= y2; y++) {
				for (int x = x1; x <= x2; x++) {
					cv.set(x, y, z1, s);
					cv.set(x, y, z2, s);
				}
				for (int z = z1; z <= z2; z++) {
					cv.set(x1, y, z, s);
					cv.set(x2, y, z, s);
				}
			}
		}

		/** Every other block along the top of a wall square. */
		void crenels(int x1, int z1, int x2, int z2, int y, BlockState s) {
			for (int x = x1; x <= x2; x += 2) {
				cv.set(x, y, z1, s);
				cv.set(x, y, z2, s);
			}
			for (int z = z1; z <= z2; z += 2) {
				cv.set(x1, y, z, s);
				cv.set(x2, y, z, s);
			}
		}

		/** An iron-bar cage with a villager inside, to be freed (or not). */
		void cage(int x, int y, int z) {
			box(x - 1, y, z - 1, x + 1, y + 2, z + 1, st(Blocks.IRON_BARS));
			cv.fill(x - 1, y + 3, z - 1, x + 1, y + 3, z + 1, st(Blocks.DARK_OAK_PLANKS));
			cv.villager(x, y, z, false);
		}

		@Nullable Mob guard(EntityType<? extends Mob> type, int x, int y, int z) {
			return cv.spawn(type, x, y, z);
		}

		/** The target: named, tougher, and tagged with the contract so its death counts. */
		void boss(EntityType<? extends Mob> type, int x, int y, int z, @Nullable ItemStack weapon) {
			Mob mob = cv.spawn(type, x, y, z);
			if (mob == null) {
				return;
			}
			Tier tier = contract.tier();
			mob.setCustomName(Component.literal(contract.target).withStyle(ChatFormatting.RED));
			mob.setCustomNameVisible(true);
			AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(tier.bossHealth);
			}
			mob.setHealth((float) tier.bossHealth);
			if (weapon != null) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			}
			if (contract.id != null) {
				mob.setAttached(FuckIllagersMod.BOSS, contract.id);
			}
			boss = mob;
		}
	}

	// ---------------------------------------------------------------- easy

	private static void wagon(Plan p) {
		Canvas cv = p.cv;
		cv.clear(-6, 0, -6, 6, 6, 6);
		cv.foundation(-6, -6, 6, 6, st(Blocks.DIRT));
		for (int dx : new int[] {-2, 2}) {
			for (int dz : new int[] {-2, 2}) {
				cv.set(dx, 0, dz, st(Blocks.DARK_OAK_LOG));
			}
		}
		cv.fill(-2, 1, -3, 2, 1, 3, st(Blocks.SPRUCE_PLANKS));
		cv.fill(-2, 2, -3, -2, 3, 3, st(Blocks.SPRUCE_FENCE));
		cv.fill(2, 2, -3, 2, 3, 3, st(Blocks.SPRUCE_FENCE));
		cv.fill(-2, 4, -3, -2, 4, 3, st(Blocks.WOOL.white()));
		cv.fill(2, 4, -3, 2, 4, 3, st(Blocks.WOOL.white()));
		cv.fill(-1, 5, -3, 1, 5, 3, st(Blocks.WOOL.white()));
		p.chest(-1, 2, -2, Direction.SOUTH, BuiltInLootTables.PILLAGER_OUTPOST);
		p.barrel(1, 2, -2, BuiltInLootTables.VILLAGE_WEAPONSMITH);
		cv.set(-1, 2, 2, st(Blocks.HAY_BLOCK));
		cv.fill(0, 1, 4, 0, 1, 5, st(Blocks.SPRUCE_FENCE));
		cv.set(4, 0, -1, st(Blocks.CAMPFIRE));
		cv.set(5, 0, 1, st(Blocks.OAK_LOG));
		cv.set(3, 0, -3, st(Blocks.OAK_LOG));
		p.boss(RANDOM.nextBoolean() ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR, 4, 0, 2, null);
		p.guard(EntityTypes.PILLAGER, -4, 0, 0);
		p.guard(EntityTypes.PILLAGER, 4, 0, -4);
	}

	private static void tower(Plan p) {
		Canvas cv = p.cv;
		cv.clear(-4, 0, -4, 4, 14, 4);
		cv.foundation(-4, -4, 4, 4, st(Blocks.COBBLESTONE));
		cv.fill(-3, -1, -3, 3, -1, 3, st(Blocks.COBBLESTONE));
		for (int dx : new int[] {-2, 2}) {
			for (int dz : new int[] {-2, 2}) {
				cv.fill(dx, 0, dz, dx, 8, dz, st(Blocks.DARK_OAK_LOG));
			}
		}
		cv.fill(0, 0, -2, 0, 8, -2, st(Blocks.DARK_OAK_PLANKS));
		cv.fill(-3, 9, -3, 3, 9, 3, st(Blocks.DARK_OAK_PLANKS));
		p.box(-3, 10, -3, 3, 10, 3, st(Blocks.OAK_FENCE));
		for (int dx : new int[] {-3, 3}) {
			for (int dz : new int[] {-3, 3}) {
				cv.fill(dx, 10, dz, dx, 12, dz, st(Blocks.DARK_OAK_LOG));
			}
		}
		cv.fill(-3, 13, -3, 3, 13, 3, st(Blocks.DARK_OAK_PLANKS));
		cv.ladder(0, 0, 9, -1, Direction.SOUTH);
		cv.set(0, 12, 0, hanging(Blocks.LANTERN));
		p.chest(2, 10, 2, Direction.NORTH, BuiltInLootTables.PILLAGER_OUTPOST);
		p.boss(EntityTypes.PILLAGER, 0, 10, 1, null);
		p.guard(EntityTypes.PILLAGER, 3, 0, 3);
		p.guard(EntityTypes.VINDICATOR, -3, 0, 3);
	}

	// ---------------------------------------------------------------- medium

	private static void tent(Canvas cv, int cx, int cz, Block wool) {
		cv.fill(cx - 2, 0, cz - 2, cx - 2, 0, cz + 2, st(wool));
		cv.fill(cx + 2, 0, cz - 2, cx + 2, 0, cz + 2, st(wool));
		cv.fill(cx - 1, 1, cz - 2, cx - 1, 1, cz + 2, st(wool));
		cv.fill(cx + 1, 1, cz - 2, cx + 1, 1, cz + 2, st(wool));
		cv.fill(cx, 2, cz - 2, cx, 2, cz + 2, st(wool));
		cv.fill(cx - 1, 0, cz - 2, cx + 1, 0, cz - 2, st(wool));
	}

	private static void camp(Plan p) {
		Canvas cv = p.cv;
		cv.clear(-10, 0, -10, 10, 6, 10);
		cv.foundation(-10, -10, 10, 10, st(Blocks.DIRT));
		cv.set(0, 0, 0, st(Blocks.CAMPFIRE));
		cv.set(2, 0, 0, st(Blocks.OAK_LOG));
		cv.set(-2, 0, 0, st(Blocks.OAK_LOG));
		cv.set(0, 0, -2, st(Blocks.OAK_LOG));
		tent(cv, -6, -5, Blocks.WOOL.white());
		tent(cv, 6, -5, Blocks.WOOL.brown());
		tent(cv, 6, 6, Blocks.WOOL.white());
		p.chest(-6, 0, -4, Direction.SOUTH, BuiltInLootTables.PILLAGER_OUTPOST);
		p.chest(6, 0, -4, Direction.SOUTH, BuiltInLootTables.PILLAGER_OUTPOST);
		p.barrel(8, 0, 1, BuiltInLootTables.VILLAGE_WEAPONSMITH);
		p.cage(-6, 0, 6);
		cv.fill(0, 0, -8, 0, 3, -8, st(Blocks.DARK_OAK_FENCE));
		cv.set(0, 4, -8, st(Blocks.WOOL.red()));
		p.boss(EntityTypes.VINDICATOR, 0, 0, 3, new ItemStack(Items.DIAMOND_AXE));
		p.guard(EntityTypes.PILLAGER, 3, 0, -2);
		p.guard(EntityTypes.PILLAGER, -3, 0, -2);
		p.guard(EntityTypes.VINDICATOR, 3, 0, 3);
		p.guard(EntityTypes.VINDICATOR, -8, 0, 0);
	}

	private static void fortress(Plan p) {
		Canvas cv = p.cv;
		BlockState cobble = st(Blocks.COBBLESTONE);
		BlockState bricks = st(Blocks.STONE_BRICKS);
		cv.clear(-10, 0, -10, 10, 10, 10);
		cv.foundation(-10, -10, 10, 10, cobble);
		cv.fill(-8, -1, -8, 8, -1, 8, st(Blocks.COARSE_DIRT));
		p.box(-8, 0, -8, 8, 4, 8, cobble);
		p.crenels(-8, -8, 8, 8, 5, cobble);
		cv.clear(-1, 0, 8, 1, 2, 8);
		for (int cx : new int[] {-8, 8}) {
			for (int cz : new int[] {-8, 8}) {
				cv.fill(cx - 1, 0, cz - 1, cx + 1, 7, cz + 1, bricks);
			}
		}
		p.box(-3, 0, -3, 3, 5, 3, bricks);
		cv.fill(-3, 6, -3, 3, 6, 3, bricks);
		cv.clear(0, 0, 3, 0, 1, 3);
		cv.set(0, 5, 0, hanging(Blocks.LANTERN));
		cv.ladder(4, 0, 4, -7, Direction.SOUTH);
		p.chest(-2, 0, -2, Direction.SOUTH, BuiltInLootTables.PILLAGER_OUTPOST);
		p.chest(2, 0, -2, Direction.SOUTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);
		p.boss(EntityTypes.VINDICATOR, 0, 0, 0, new ItemStack(Items.DIAMOND_AXE));
		p.guard(EntityTypes.PILLAGER, -8, 5, 0);
		p.guard(EntityTypes.PILLAGER, 8, 5, 0);
		p.guard(EntityTypes.VINDICATOR, -5, 0, 5);
		p.guard(EntityTypes.VINDICATOR, 5, 0, 5);
	}

	// ---------------------------------------------------------------- hard

	private static void dungeon(Plan p) {
		Canvas cv = p.cv;
		BlockState bricks = st(Blocks.STONE_BRICKS);
		// the crypt on the surface
		cv.clear(-4, 0, -4, 4, 6, 4);
		cv.foundation(-4, -4, 4, 4, bricks);
		p.box(-3, 0, -3, 3, 3, 3, st(Blocks.MOSSY_STONE_BRICKS));
		cv.fill(-3, 4, -3, 3, 4, 3, bricks);
		cv.clear(0, 0, 3, 0, 1, 3);
		// the hall, 16 blocks down
		cv.fill(-10, -17, -14, 10, -9, 6, bricks);
		cv.clear(-9, -16, -13, 9, -10, 5);
		// the boss room behind an arch
		cv.fill(-9, -16, -8, 9, -10, -8, bricks);
		cv.clear(-1, -16, -8, 1, -14, -8);
		// the cells
		cv.fill(-9, -13, -6, -6, -13, 4, bricks);
		cv.fill(-6, -16, -6, -6, -14, 4, st(Blocks.IRON_BARS));
		cv.villager(-8, -16, -3, false);
		cv.villager(-8, -16, 2, false);
		// pillars and the way down
		cv.fill(5, -16, -3, 5, -10, -3, bricks);
		cv.fill(5, -16, 2, 5, -10, 2, bricks);
		cv.fill(-1, -9, -1, 1, -1, 1, bricks);
		cv.fill(0, -16, -1, 0, -10, -1, bricks);
		cv.set(0, 0, -1, bricks);
		cv.ladder(0, -16, 0, 0, Direction.SOUTH);
		for (int[] l : new int[][] {{3, 4}, {-3, -6}, {-5, -12}, {5, -12}, {7, 0}}) {
			cv.set(l[0], -16, l[1], st(Blocks.SOUL_LANTERN));
		}
		cv.spawner(7, -16, -4, EntityTypes.SKELETON);
		p.chest(-3, -16, -13, Direction.SOUTH, BuiltInLootTables.WOODLAND_MANSION);
		p.chest(3, -16, -13, Direction.SOUTH, BuiltInLootTables.STRONGHOLD_CORRIDOR);
		p.chest(8, -16, 4, Direction.WEST, BuiltInLootTables.SIMPLE_DUNGEON);
		p.boss(EntityTypes.EVOKER, 0, -16, -11, null);
		p.guard(EntityTypes.VINDICATOR, -3, -16, -10);
		p.guard(EntityTypes.VINDICATOR, 3, -16, -10);
		p.guard(EntityTypes.VINDICATOR, 4, -16, 0);
		p.guard(EntityTypes.PILLAGER, -3, -16, 4);
		p.guard(EntityTypes.PILLAGER, 6, -16, 3);
	}

	private static void castle(Plan p) {
		Canvas cv = p.cv;
		BlockState bricks = st(Blocks.STONE_BRICKS);
		cv.clear(-16, 0, -16, 16, 16, 16);
		cv.foundation(-16, -16, 16, 16, bricks);
		// curtain walls and gate
		p.box(-13, 0, -13, 13, 7, 13, bricks);
		p.crenels(-13, -13, 13, 13, 8, bricks);
		cv.clear(-2, 0, 13, 2, 3, 13);
		// corner towers
		for (int cx : new int[] {-13, 13}) {
			for (int cz : new int[] {-13, 13}) {
				cv.fill(cx - 2, 0, cz - 2, cx + 2, 10, cz + 2, bricks);
				p.crenels(cx - 2, cz - 2, cx + 2, cz + 2, 11, bricks);
			}
		}
		// the keep: two floors
		p.box(-6, 0, -6, 6, 10, 6, bricks);
		cv.fill(-5, 5, -5, 5, 5, 5, st(Blocks.DARK_OAK_PLANKS));
		cv.fill(-6, 11, -6, 6, 11, 6, bricks);
		p.crenels(-6, -6, 6, 6, 12, bricks);
		cv.clear(0, 0, 6, 0, 2, 6);
		cv.ladder(0, 0, 5, -5, Direction.SOUTH);
		for (int[] w : new int[][] {{-6, 0}, {6, 0}, {0, -6}}) {
			cv.clear(w[0], 7, w[1], w[0], 8, w[1]);
		}
		for (int dx : new int[] {-3, 3}) {
			for (int dz : new int[] {-3, 3}) {
				cv.set(dx, 4, dz, hanging(Blocks.LANTERN));
			}
		}
		cv.set(0, 10, 0, hanging(Blocks.LANTERN));
		p.chest(-4, 0, -4, Direction.SOUTH, BuiltInLootTables.PILLAGER_OUTPOST);
		p.chest(4, 0, -4, Direction.SOUTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);
		p.chest(-4, 6, -4, Direction.SOUTH, BuiltInLootTables.WOODLAND_MANSION);
		p.chest(4, 6, -4, Direction.SOUTH, BuiltInLootTables.SIMPLE_DUNGEON);
		p.chest(4, 6, 4, Direction.NORTH, BuiltInLootTables.BURIED_TREASURE);
		// the courtyard
		cv.set(8, 0, 8, st(Blocks.CAMPFIRE));
		cv.fill(-9, 0, 8, -9, 1, 8, st(Blocks.HAY_BLOCK));
		p.cage(8, 0, -9);
		p.boss(EntityTypes.EVOKER, 0, 6, 0, null);
		p.guard(EntityTypes.VINDICATOR, -2, 0, 2);
		p.guard(EntityTypes.VINDICATOR, 2, 0, 2);
		p.guard(EntityTypes.VINDICATOR, 0, 0, 10);
		p.guard(EntityTypes.PILLAGER, -13, 8, 0);
		p.guard(EntityTypes.PILLAGER, 13, 8, 0);
		p.guard(EntityTypes.PILLAGER, 0, 8, -13);
		p.guard(EntityTypes.RAVAGER, 6, 0, 9);
	}
}
