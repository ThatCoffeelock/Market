package com.thatcoffeelock.fossilfool;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/fossilfool.json. Created with defaults on first start; /fossilfool admin reload re-reads it.
 *
 * Fuel is the balancing knob: every fuel is worth a number of drilled blocks, and each step up the ladder
 * (coal, lava, crude, diesel) is worth more per item and burns faster than the one before.
 */
public final class FossilConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static FossilConfig current = new FossilConfig();

	// ---------------------------------------------------------------- oil underground

	/** Chance that an overworld chunk hides an oil pocket. */
	public double pocketChance = 0.12;
	public int pocketMinY = -50;
	public int pocketMaxY = 16;
	/** Share of pockets that are big gushers. */
	public double gusherChance = 0.1;
	/** How far (in blocks) a Dowsing Rod feels oil, before the Dowser perk. */
	public int dowsingRange = 48;

	// ---------------------------------------------------------------- fuel: drilled blocks per item, and speed

	public double coalBlocks = 2;
	public double lavaBlocks = 40;
	public double crudeBlocks = 60;
	public double dieselBlocks = 200;
	public double coalSpeed = 0.5;
	public double lavaSpeed = 0.75;
	public double crudeSpeed = 1.0;
	public double dieselSpeed = 1.5;

	// ---------------------------------------------------------------- the Drill Rig

	/** Ticks per drilled block at speed 1.0 (crude). 20 = one block a second, about 25 seconds a layer. */
	public double ticksPerBlock = 20;
	/** Ticks per bucket pumped out of a struck pocket, at speed 1.0. */
	public double ticksPerBucket = 40;
	/** Fuel (in drilled-block units) it costs to pump one bucket. */
	public double pumpCost = 0.25;
	/** Buckets of crude the rig's own tank holds. */
	public int rigTank = 64;
	/** Running rigs keep their chunk loaded, so they drill while nobody's around. */
	public boolean keepChunksLoaded = true;
	/** How far a rig, or a Refinery, reaches to fill or drain Tanks without any pipe ("the assumed pipes"). */
	public int pipeReach = 6;
	/** The most Pipe blocks one pipeline follows. */
	public int pipeLength = 512;
	/** Rigs and Refineries with an empty firebox burn diesel, crude or lava from the tanks they reach. */
	public boolean fuelFromTanks = true;
	/** How far a rig looks for a Warehouse to unload into (needs the Warehouse mod). */
	public int warehouseReach = 16;

	// ---------------------------------------------------------------- tanks and refining

	/** Buckets a Tank holds. */
	public int tankCapacity = 1000;
	/** Buckets of crude and of diesel a Refinery holds. */
	public int refineryCapacity = 64;
	/** Buckets of crude per bucket of diesel. */
	public int crudePerDiesel = 2;
	/** Ticks per bucket of diesel at speed 1.0. */
	public double refineTicks = 300;
	/** Fuel (in drilled-block units) it costs to refine one bucket of diesel. */
	public double refineHeat = 20;

	// ---------------------------------------------------------------- Market sell prices (needs the Market mod)

	public double crudeSellPrice = 25;
	public double dieselSellPrice = 45;

	public static FossilConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("fossilfool.json");
		FossilConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, FossilConfig.class);
			} catch (Exception e) {
				FossilFoolMod.LOG.error("Could not read config/fossilfool.json, using defaults", e);
			}
		}
		FossilConfig cfg = loaded != null ? loaded : new FossilConfig();
		cfg.pocketChance = Math.max(0, Math.min(1, cfg.pocketChance));
		cfg.gusherChance = Math.max(0, Math.min(1, cfg.gusherChance));
		cfg.ticksPerBlock = Math.max(1, cfg.ticksPerBlock);
		cfg.ticksPerBucket = Math.max(1, cfg.ticksPerBucket);
		cfg.refineTicks = Math.max(1, cfg.refineTicks);
		cfg.crudePerDiesel = Math.max(1, cfg.crudePerDiesel);
		cfg.rigTank = Math.max(1, cfg.rigTank);
		cfg.tankCapacity = Math.max(1, cfg.tankCapacity);
		cfg.pipeLength = Math.max(1, cfg.pipeLength);
		cfg.refineryCapacity = Math.max(cfg.crudePerDiesel, cfg.refineryCapacity);
		if (cfg.pocketMaxY < cfg.pocketMinY) {
			cfg.pocketMaxY = cfg.pocketMinY;
		}
		current = cfg;
		Pockets.clearCache();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			FossilFoolMod.LOG.error("Could not write config/fossilfool.json", e);
		}
	}
}
