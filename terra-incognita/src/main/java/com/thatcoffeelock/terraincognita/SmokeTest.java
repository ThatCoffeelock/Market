package com.thatcoffeelock.terraincognita;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Only runs with -Dterraincognita.smokeTest=true (CI). Boots a real server and checks Terra Incognita: the dimension
 * loads with its raised sea, the terrain is an archipelago with real mountains (a text map of 4096 × 4096 blocks goes
 * to the log, to judge the shapes by), vanilla structures find their way in, an entity can cross over and back, the
 * open-water search works, illager bands spawn on dry land, and the plunder fills a chest.
 */
final class SmokeTest {
	/** The map: 128 × 128 samples, 32 blocks apart. */
	private static final int CELLS = 128;
	private static final int STEP = 32;
	private static final UUID TRAVELLER = UUID.fromString("16050000-0000-4000-8000-000000000001");

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		TerraMod.later(100, () -> step(server, () -> start(server)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void start(MinecraftServer server) {
		ServerLevel terra = Terra.terra(server);
		check(terra != null, "the dimension terra_incognita:terra loaded");
		check(Terra.isTerra(terra) && !Terra.isTerra(server.overworld()) && Terra.across(server.overworld()) == terra
			&& Terra.across(terra) == server.overworld(), "a chart takes you there from home, and home from there");
		check(terra.getSeaLevel() == 76, "the sea is raised: sea level " + terra.getSeaLevel() + " (the Overworld's is " + server.overworld().getSeaLevel() + ")");
		check(Charts.isChart(Charts.chart()) && !Charts.isChart(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER)),
			"the Sea Chart is recognised, plain paper isn't");
		map(terra);
		structures(terra);
		crossing(server, terra);
	}

	// ---------------------------------------------------------------- the lie of the land

	private static void map(ServerLevel level) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		int sea = level.getSeaLevel();
		int land = 0;
		int peaks = 0;
		int highest = Integer.MIN_VALUE;
		Map<String, Integer> biomes = new TreeMap<>();
		StringBuilder out = new StringBuilder("[smoke] map of Terra Incognita, 4096 x 4096 blocks centred on 0,0 (~ deep sea, - shallows, letters land):\n");
		int half = CELLS * STEP / 2;
		for (int row = 0; row < CELLS; row++) {
			StringBuilder line = new StringBuilder();
			for (int col = 0; col < CELLS; col++) {
				int x = -half + col * STEP;
				int z = -half + row * STEP;
				int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
				highest = Math.max(highest, h);
				if (h < sea) {
					line.append(h < sea - 15 ? '~' : '-');
					continue;
				}
				land++;
				if (h >= 150) {
					peaks++;
				}
				String biome = level.getBiome(new BlockPos(x, h, z)).getRegisteredName();
				biomes.merge(biome, 1, Integer::sum);
				line.append(h >= 150 ? '^' : letter(biome));
			}
			out.append(line).append('\n');
		}
		TerraMod.LOG.info(out.toString());
		double share = land / (double) (CELLS * CELLS);
		TerraMod.LOG.info("[smoke] land {}%, peaks over y=150: {}, highest {}, land biomes {}", Math.round(share * 100), peaks, highest, biomes);
		check(share > 0.08 && share < 0.75, "it's an archipelago: " + Math.round(share * 100) + "% land");
		check(highest >= 150, "with real mountains: the highest point is y=" + highest);
		check(biomes.size() >= 6, "and plenty of different lands: " + biomes.size() + " land biomes");
	}

	private static char letter(String biome) {
		String b = biome.substring(biome.indexOf(':') + 1);
		return switch (b) {
			case "jungle" -> 'J';
			case "sparse_jungle" -> 'j';
			case "bamboo_jungle" -> 'B';
			case "savanna" -> 'S';
			case "savanna_plateau" -> 'P';
			case "windswept_savanna" -> 'W';
			case "desert" -> 'D';
			case "badlands" -> 'A';
			case "wooded_badlands" -> 'a';
			case "eroded_badlands" -> 'E';
			case "flower_forest" -> 'f';
			case "meadow" -> 'M';
			case "dark_forest" -> 'F';
			case "cherry_grove" -> 'C';
			case "mangrove_swamp" -> 'm';
			case "beach" -> 'b';
			case "stony_peaks", "jagged_peaks" -> 'K';
			default -> b.contains("ocean") ? 'o' : '?';
		};
	}

	// ---------------------------------------------------------------- what spawns there

	private static void structures(ServerLevel level) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		int found = 0;
		for (String name : new String[] {"village_savanna", "pillager_outpost", "jungle_pyramid", "shipwreck", "ocean_ruin_warm"}) {
			var holder = registry.get(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(name)));
			if (holder.isEmpty()) {
				TerraMod.LOG.info("[smoke] no structure minecraft:{} in this version", name);
				continue;
			}
			Holder<Structure> structure = holder.get();
			Pair<BlockPos, Holder<Structure>> at = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 64, false);
			TerraMod.LOG.info("[smoke] minecraft:{} in Terra Incognita: {}", name, at == null ? "none within 1024 blocks" : "at " + at.getFirst().toShortString());
			found += at == null ? 0 : 1;
		}
		check(found >= 3, "the Overworld's structures spawn over there too (" + found + " of 5 found nearby)");
	}

	// ---------------------------------------------------------------- crossing over, raiders, plunder

	private static void crossing(MinecraftServer server, ServerLevel terra) {
		BlockPos sea = Crossing.arrival(terra, 0, 0);
		check(!terra.getBlockState(sea.below()).getFluidState().isEmpty(), "open water to arrive in, near 0,0: " + sea.toShortString());

		// an Ahoy ship only comes along with Copper sheathing, and only for whoever may sail her
		check(AhoyLink.refusal(Map.of("name", "Duyfken", "rigging", 2, "mayUse", true)) == null
			&& AhoyLink.refusal(Map.of("name", "Duyfken", "rigging", 1, "mayUse", true)) != null
			&& AhoyLink.refusal(Map.of("name", "Duyfken", "rigging", 3, "mayUse", false)) != null,
			"a ship needs Copper sheathing, and her captain, to cross");

		ServerLevel home = server.overworld();
		// there are no spawn chunks any more: a player brings their own, an armor stand needs them loaded for it
		Cmd.run(home, "forceload add 0 0");
		Cmd.run(terra, "forceload add " + sea.getX() + " " + sea.getZ());
		home.getChunk(0, 0);
		TerraMod.later(20, () -> step(server, () -> {
			Entity traveller = EntityType.ARMOR_STAND.create(home, EntitySpawnReason.COMMAND);
			check(traveller != null, "an armor stand can be made");
			traveller.setUUID(TRAVELLER);
			traveller.setNoGravity(true);
			traveller.setCustomName(Component.literal("Traveller"));
			traveller.setPos(0.5, 120, 0.5);
			check(home.addFreshEntity(traveller) && home.getEntity(TRAVELLER) != null, "a traveller sets out from home");
			Cmd.run(home, "execute in " + Terra.DIMENSION + " run tp " + TRAVELLER + " " + Cmd.pos(sea.getX() + 0.5, sea.getY() + 0.2, sea.getZ() + 0.5));
			TerraMod.later(20, () -> step(server, () -> arrived(server, terra, home, sea)));
		}));
	}

	private static void arrived(MinecraftServer server, ServerLevel terra, ServerLevel home, BlockPos sea) {
		// crossing over can hand a non-player a fresh entity, so look for it by name where it should be
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(sea.getX() - 4, sea.getY() - 4, sea.getZ() - 4,
			sea.getX() + 5, sea.getY() + 5, sea.getZ() + 5);
		var arrived = terra.getEntitiesOfClass(Entity.class, box,
			e -> e.hasCustomName() && e.getCustomName().getString().equals("Traveller"));
		check(!arrived.isEmpty() && home.getEntity(TRAVELLER) == null, "the traveller crosses into Terra Incognita");
		arrived.forEach(Entity::discard);

		// a platform high above any mountain: a band of illagers turns up on dry land near it
		Cmd.run(terra, "forceload add -64 -64 64 64");
		Cmd.run(terra, "fill -60 300 -60 60 300 60 minecraft:stone");
		BlockPos camp = Raiders.band(terra, new BlockPos(0, 301, 0), 4);
		check(camp != null && camp.getY() == 301, "a band of illagers finds dry land to stand on (" + camp + ")");
		TerraMod.later(5, () -> step(server, () -> {
			int illagers = Raiders.nearby(terra, camp);
			check(illagers >= 4, "and turns up: " + illagers + " illagers");
			Cmd.run(terra, "kill @e[type=#minecraft:illager]");
			Cmd.run(terra, "kill @e[type=minecraft:ravager]");
			Cmd.run(terra, "fill -60 300 -60 60 300 60 minecraft:air");

			SimpleContainer chest = new SimpleContainer(27);
			int added = Bounty.fill(chest, Bounty.plunder(1.0, new Random(1605)));
			check(added >= 2 && chest.countItem(net.minecraft.world.item.Items.GOLD_INGOT) > 0 && chest.countItem(net.minecraft.world.item.Items.EMERALD) > 0,
				"an unopened chest over there gets extra plunder (" + added + " stacks)");
			check(Bounty.plunder(0, new Random(1)).isEmpty() || Bounty.plunder(0, new Random(1)).stream().allMatch(s -> !s.is(net.minecraft.world.item.Items.GOLD_INGOT)),
				"a chest bounty of 0 adds no gold");

			TerraMod.later(20, () -> step(server, () -> {
				TerraMod.LOG.info("TERRA INCOGNITA SMOKE TEST PASSED");
				server.halt(false);
			}));
		}));
	}

	private static void fail(MinecraftServer server, Throwable t) {
		TerraMod.LOG.error("TERRA INCOGNITA SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		TerraMod.LOG.info("[smoke] ok: {}", what);
	}
}
