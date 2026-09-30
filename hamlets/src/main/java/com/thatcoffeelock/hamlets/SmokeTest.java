package com.thatcoffeelock.hamlets;

import java.util.List;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * Only runs with -Dhamlets.smokeTest=true (CI). Boots a real server, builds every variant on test
 * platforms and checks who moved in, then finds each structure in real terrain and generates it.
 */
final class SmokeTest {
	private static final List<String> NAMES = List.of("cottage", "haunted_cottage", "castle", "ruined_castle", "dungeon");
	private static final int Y = 150;

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -32 -32 352 32");
		HamletsMod.later(100, () -> step(server, () -> builds(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			HamletsMod.LOG.error("HAMLETS SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void builds(ServerLevel level) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String name : NAMES) {
			check(structures.getValue(HamletsMod.id(name)) instanceof HamletStructure, "structure hamlets:" + name + " is registered");
		}

		Survey cottage = site(level, 0, Plan.COTTAGE, Folk.VILLAGERS);
		check(cottage.villagers >= 2, "cottage has a family (" + cottage.villagers + " villagers)");
		check(cottage.beds >= 2, "cottage has beds (" + cottage.beds + ")");
		check(cottage.loot >= 1, "cottage has a loot chest");
		check(cottage.monsters == 0, "no monsters in the cottage");

		Survey haunted = site(level, 1, Plan.COTTAGE, Folk.MONSTERS);
		check(haunted.monsters >= 3, "haunted cottage has monsters (" + haunted.monsters + ")");
		check(haunted.count(EntityType.ZOMBIE_VILLAGER) == 1, "haunted cottage has a zombie villager to cure");
		check(haunted.villagers == 0, "no villagers in the haunted cottage");

		Survey castle = site(level, 2, Plan.CASTLE, Folk.VILLAGERS);
		check(castle.villagers >= 6, "castle has villagers (" + castle.villagers + ")");
		check(castle.count(EntityType.IRON_GOLEM) == 1, "castle has an iron golem");
		check(castle.beds >= 6, "castle has beds (" + castle.beds + ")");
		check(castle.has(Blocks.BELL), "castle has a bell");

		Survey ruin = site(level, 3, Plan.CASTLE, Folk.MONSTERS);
		check(ruin.monsters >= 8, "ruined castle has monsters (" + ruin.monsters + ")");
		check(ruin.count(EntityType.PILLAGER) == 2, "bandits camp in the ruined castle");
		check(ruin.has(Blocks.SPAWNER), "ruined castle has a spawner");
		check(ruin.villagers == 0, "no villagers in the ruined castle");

		Survey dungeon = site(level, 4, Plan.DUNGEON, Folk.MONSTERS);
		check(dungeon.has(Blocks.SPAWNER), "dungeon has a spawner");
		check(dungeon.has(Blocks.IRON_DOOR), "dungeon has prison cells");
		check(dungeon.villagers + dungeon.count(EntityType.ZOMBIE_VILLAGER) >= 2, "dungeon has prisoners (" + dungeon.villagers + " villagers)");
		check(dungeon.monsters >= 8, "dungeon has monsters (" + dungeon.monsters + ")");
		check(dungeon.loot >= 4, "dungeon has loot (" + dungeon.loot + " chests)");
		check(!level.getBlockState(new BlockPos(4 * 72 + 2, Y + Builders.DUNGEON_FLOOR, 2)).isAir(), "dungeon hub has a floor");
		check(level.getBlockState(new BlockPos(4 * 72 + 2, Y + Builders.DUNGEON_FLOOR + 1, 2)).isAir(), "dungeon hub is hollow");

		HamletsMod.later(20, () -> step(level.getServer(), () -> worldgen(level)));
	}

	/** Builds one variant on a stone platform and counts what's inside. */
	private static Survey site(ServerLevel level, int i, Plan plan, Folk folk) {
		BlockPos origin = new BlockPos(i * 72, Y, 0);
		int r = plan.radius;
		Cmd.run(level, "fill " + (origin.getX() - r) + " " + Y + " " + (-r) + " " + (origin.getX() + r) + " " + Y + " " + r + " minecraft:stone");
		Canvas canvas = HamletsMod.buildNow(level, plan, folk, origin, Rotation.values()[i % 4], 1234L + i);
		BoundingBox box = plan.box(origin);
		Survey s = new Survey(level, box);
		HamletsMod.LOG.info("[smoke] {} for {} ({}): {} spawned, {} villagers, {} monsters, {} beds, {} loot containers",
			plan.id, folk.id, canvas.style, canvas.spawned, s.villagers, s.monsters, s.beds, s.loot);
		check(canvas.spawned > 0, plan.id + " for " + folk.id + " has inhabitants");
		return s;
	}

	private static void worldgen(ServerLevel level) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String name : NAMES) {
			Holder<Structure> holder = structures.get(ResourceKey.create(Registries.STRUCTURE, HamletsMod.id(name))).orElseThrow();
			Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 100, false);
			check(found != null, "worldgen places hamlets:" + name);
			BlockPos at = found.getFirst();
			ChunkAccess chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			StructureStart start = chunk.getStartForStructure(holder.value());
			check(start != null && start.isValid(), "hamlets:" + name + " starts at " + at.toShortString());
			check(start.getPieces().get(0) instanceof HamletPiece, "hamlets:" + name + " is one of our pieces");
			HamletPiece piece = (HamletPiece) start.getPieces().get(0);
			BlockState centre = level.getBlockState(piece.origin);
			check(centre.is(piece.style.planks) || centre.is(piece.style.bricks),
				"hamlets:" + name + " was built at " + piece.origin.toShortString() + " (" + piece.style + ", found " + centre + ")");
		}
		HamletsMod.LOG.info("HAMLETS SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static final class Survey {
		final List<Entity> entities;
		final ServerLevel level;
		final BoundingBox box;
		int villagers;
		int monsters;
		int beds;
		int loot;

		Survey(ServerLevel level, BoundingBox box) {
			this.level = level;
			this.box = box;
			this.entities = level.getEntities((Entity) null, AABB.of(box).inflate(1), e -> e.isAlive());
			for (Entity e : entities) {
				if (e.getType() == EntityType.VILLAGER) {
					villagers++;
				} else if (e.getType().getCategory() == MobCategory.MONSTER) {
					monsters++;
				}
			}
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof BedBlock) {
					beds++;
				}
				if (level.getBlockEntity(pos) instanceof RandomizableContainer container && container.getLootTable() != null) {
					loot++;
				}
			}
			beds /= 2;
		}

		int count(EntityType<?> type) {
			int n = 0;
			for (Entity e : entities) {
				if (e.getType() == type) {
					n++;
				}
			}
			return n;
		}

		boolean has(net.minecraft.world.level.block.Block block) {
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(pos).is(block)) {
					return true;
				}
			}
			return false;
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		HamletsMod.LOG.info("[smoke] ok: {}", what);
	}
}
