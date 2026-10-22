package com.thatcoffeelock.terraincognita;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/terra_incognita.json: the crossing, the illagers and the loot. Created with defaults on first start, read
 * again on the next one. High risk, high reward: turn both down together if your economy starts to inflate.
 */
public final class TerraConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static TerraConfig current = new TerraConfig();

	/** Seconds of holding your course at sea, chart in hand, before the fog takes you across. */
	public int voyageSeconds = 30;
	/** How far (in blocks) the rest of the crew may be from the captain and still come along. They must be aboard something. */
	public int crewRadius = 12;
	/** An Ahoy ship needs at least this rigging refit to make the crossing (2 is Copper sheathing; 0 lets any ship go). */
	public int shipRigging = 2;

	/** Every this many seconds, each player over there may be found by a band of illagers. */
	public int raidIntervalSeconds = 20;
	/** Chance that a band turns up, each time. */
	public double raidChance = 0.5;
	/** No new bands while this many illagers are already within 48 blocks of the player. */
	public int maxIllagersNearby = 12;
	/** Illagers in a band: from 2 to this many. */
	public int maxBandSize = 5;

	/** Multiplies the extra loot in every unopened chest over there (0 turns it off). */
	public double chestBounty = 1.0;
	/** Multiplies the extra emeralds and gold illagers drop over there (0 turns it off). */
	public double illagerBounty = 1.0;

	public static TerraConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("terra_incognita.json");
		TerraConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, TerraConfig.class);
			} catch (Exception e) {
				TerraMod.LOG.error("Could not read config/terra_incognita.json, using defaults", e);
			}
		}
		TerraConfig cfg = loaded != null ? loaded : new TerraConfig();
		cfg.voyageSeconds = Math.max(1, cfg.voyageSeconds);
		cfg.crewRadius = Math.max(0, cfg.crewRadius);
		cfg.shipRigging = Math.max(0, cfg.shipRigging);
		cfg.raidIntervalSeconds = Math.max(1, cfg.raidIntervalSeconds);
		cfg.raidChance = Math.max(0, Math.min(1, cfg.raidChance));
		cfg.maxIllagersNearby = Math.max(0, cfg.maxIllagersNearby);
		cfg.maxBandSize = Math.max(2, cfg.maxBandSize);
		cfg.chestBounty = Math.max(0, cfg.chestBounty);
		cfg.illagerBounty = Math.max(0, cfg.illagerBounty);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			TerraMod.LOG.error("Could not write config/terra_incognita.json", e);
		}
	}
}
