package com.thatcoffeelock.tulpengekte;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/tulpengekte.json: prices in Marks, breeding odds and the Bloemist's appetite. Created with defaults on first
 * start, read again on the next one.
 *
 * Balanced on purpose. Common bulbs multiply fast, so they're cheap (a field of them earns about what a tobacco
 * planter does). The money is in breaking: about 1 offset in 20 is a Fine variety, 1 in 100 a Broken one and 1 in
 * 1,000 the Semper Augustus. Broken bulbs give few offsets and half of those revert, so nobody farms them.
 */
public final class TulpConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static TulpConfig current = new TulpConfig();

	/** What the Market pays for one bulb, by tier. */
	public double commonSellPrice = 1;
	public double fineSellPrice = 20;
	public double rareSellPrice = 75;
	public double legendarySellPrice = 300;

	/** The Bloemist pays this much on top of the Market price for what he wants today (0.5 = +50%). */
	public double bloemistPremium = 0.5;
	/** How many of each want he takes from one player per day, by tier. */
	public int bloemistDailyCommon = 32;
	public int bloemistDailyFine = 8;
	public int bloemistDailyRare = 2;
	public int bloemistDailyLegendary = 1;
	/** What the Bloemist charges for a common bulb. Keep it well above commonSellPrice. */
	public double bloemistBulbPrice = 5;

	/** Ticks from planting to flowering (24000 = one Minecraft day). */
	public int growTicks = 24000;
	/** Chance that breaking a wild (or any unplanted) tulip turns up a bulb. The flower is used up when it does. */
	public double wildBulbChance = 0.25;
	/** Chance that an offset of a common bulb breaks into a Fine, Broken or Legendary variety. */
	public double fineChance = 0.05;
	public double rareChance = 0.01;
	public double legendaryChance = 0.001;
	/** Chance that an offset of a Broken or Legendary bulb is the same variety. Otherwise it reverts to a common one. */
	public double brokenTrueChance = 0.5;

	public static TulpConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("tulpengekte.json");
		TulpConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, TulpConfig.class);
			} catch (Exception e) {
				TulpMod.LOG.error("Could not read config/tulpengekte.json, using defaults", e);
			}
		}
		TulpConfig cfg = loaded != null ? loaded : new TulpConfig();
		cfg.bloemistPremium = Math.max(0, cfg.bloemistPremium);
		cfg.bloemistBulbPrice = Math.max(cfg.commonSellPrice, cfg.bloemistBulbPrice);
		cfg.growTicks = Math.max(20, cfg.growTicks);
		cfg.wildBulbChance = clamp(cfg.wildBulbChance);
		cfg.fineChance = clamp(cfg.fineChance);
		cfg.rareChance = clamp(cfg.rareChance);
		cfg.legendaryChance = clamp(cfg.legendaryChance);
		cfg.brokenTrueChance = clamp(cfg.brokenTrueChance);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			TulpMod.LOG.error("Could not write config/tulpengekte.json", e);
		}
	}

	private static double clamp(double chance) {
		return Math.max(0, Math.min(1, chance));
	}

	/** The Market price of one bulb of this tier, in Marks. */
	double sellPrice(Variety.Tier tier) {
		return switch (tier) {
			case COMMON -> commonSellPrice;
			case FINE -> fineSellPrice;
			case RARE -> rareSellPrice;
			case LEGENDARY -> legendarySellPrice;
		};
	}

	/** How many of a want of this tier the Bloemist takes from one player a day. */
	int dailyCap(Variety.Tier tier) {
		return switch (tier) {
			case COMMON -> bloemistDailyCommon;
			case FINE -> bloemistDailyFine;
			case RARE -> bloemistDailyRare;
			case LEGENDARY -> bloemistDailyLegendary;
		};
	}
}
