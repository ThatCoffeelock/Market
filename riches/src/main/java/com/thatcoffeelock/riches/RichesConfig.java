package com.thatcoffeelock.riches;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** config/riches.json. Created with defaults on first start; /riches admin reload re-reads it. */
public final class RichesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static RichesConfig current = new RichesConfig();

	/** How far the gold pile spreads around a Vault Ledger: 3 = a 7×7 floor. */
	public int pileRadius = 3;
	/** Balance (in Marks) where the first coins show up. */
	public double pileStart = 1000;
	/** Height of the pile's peak per tenfold of balance above pileStart: 0.9 = ₥10k is about a block, ₥1M about three. */
	public double pileHeightPerTenfold = 0.9;
	/** The pile never gets taller than this, in blocks. */
	public double pileMaxHeight = 4.0;
	/** Ticks a Vault Door stays open before it swings shut by itself. */
	public int doorOpenTicks = 100;
	/** Each relic exists once per server. Off: everyone can find their own copy. */
	public boolean uniqueRelics = true;
	/** Marks paid (once per player per collection) for showing a full collection in your own display cases. */
	public double collectionReward = 5000;
	/** Multiplies every relic's drop chance. */
	public double relicChanceMultiplier = 1.0;

	public static RichesConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("riches.json");
		RichesConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, RichesConfig.class);
			} catch (Exception e) {
				RichesMod.LOG.error("Could not read config/riches.json, using defaults", e);
			}
		}
		RichesConfig cfg = loaded != null ? loaded : new RichesConfig();
		cfg.pileRadius = Math.max(1, Math.min(6, cfg.pileRadius));
		cfg.pileStart = Math.max(1, cfg.pileStart);
		cfg.pileMaxHeight = Math.max(0.2, Math.min(8, cfg.pileMaxHeight));
		cfg.doorOpenTicks = Math.max(20, cfg.doorOpenTicks);
		cfg.relicChanceMultiplier = Math.max(0, cfg.relicChanceMultiplier);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			RichesMod.LOG.error("Could not write config/riches.json", e);
		}
	}
}
