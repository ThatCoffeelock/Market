package com.thatcoffeelock.specerij;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/specerij.json: growing and drying times, the Hortus, wild cuttings and prices. Created with defaults on first
 * start, read again on the next one.
 *
 * Balanced against Havana: a ripe plant gives a handful of fresh spice a day, worth about what a tobacco plant's
 * leaves are once dried. Spice plants are perennial (pick them and they grow back), but the cutting has to come from
 * the far side of the world, and a Hortus at home only grows at half speed.
 */
public final class SpiceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static SpiceConfig current = new SpiceConfig();

	/** Ticks of growing (in the home climate) from planting, or from the last harvest, to ripe. 24000 = one day. */
	public int growTicks = 24000;
	/** A Hortus grows this many times slower than the home climate. */
	public double hortusSlowdown = 2.0;
	/** Chance that breaking grass, ferns or bushes in a spice's home biome turns up a cutting of it. */
	public double wildCuttingChance = 0.04;
	/** Ticks in a Drying Rack: fresh to dried, and green tea to black. */
	public int dryTicks = 24000;
	public int blackTeaTicks = 48000;
	/** Multiplies every Market price (see each spice's README row for the defaults). */
	public double priceMultiplier = 1.0;
	/** What the Market pays for a cutting. */
	public double cuttingSellPrice = 5;

	public static SpiceConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("specerij.json");
		SpiceConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, SpiceConfig.class);
			} catch (Exception e) {
				SpiceMod.LOG.error("Could not read config/specerij.json, using defaults", e);
			}
		}
		SpiceConfig cfg = loaded != null ? loaded : new SpiceConfig();
		cfg.growTicks = Math.max(20, cfg.growTicks);
		cfg.hortusSlowdown = Math.max(1, cfg.hortusSlowdown);
		cfg.wildCuttingChance = Math.max(0, Math.min(1, cfg.wildCuttingChance));
		cfg.dryTicks = Math.max(20, cfg.dryTicks);
		cfg.blackTeaTicks = Math.max(20, cfg.blackTeaTicks);
		cfg.priceMultiplier = Math.max(0, cfg.priceMultiplier);
		cfg.cuttingSellPrice = Math.max(0, cfg.cuttingSellPrice);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			SpiceMod.LOG.error("Could not write config/specerij.json", e);
		}
	}
}
