package com.thatcoffeelock.proost;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/proost.json: what the Market pays for mash and bottles, in Marks. Created with defaults on first start,
 * read again on the next one.
 *
 * A bottle takes 8 mash (8 crops) and a glass bottle, so a young bottle is worth a few times its crops, and two
 * days in a cask more than doubles it, like Havana's tobacco.
 */
public final class ProostConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static ProostConfig current = new ProostConfig();

	public double mashSellPrice = 0.25;
	public double geneverSellPrice = 6;
	public double whiskySellPrice = 5;
	public double rumSellPrice = 5;
	public double brandySellPrice = 7;
	public double oudeGeneverSellPrice = 16;
	public double agedWhiskySellPrice = 14;
	public double darkRumSellPrice = 14;
	public double brandyVsopSellPrice = 18;
	/** An opened bottle sells for its share of swigs left, times this. */
	public double openedFactor = 0.5;

	public static ProostConfig get() {
		return current;
	}

	/** What a full bottle of this sells for, in Marks. */
	double price(ProostItems.Drink drink, boolean aged) {
		return switch (drink) {
			case GENEVER -> aged ? oudeGeneverSellPrice : geneverSellPrice;
			case WHISKY -> aged ? agedWhiskySellPrice : whiskySellPrice;
			case RUM -> aged ? darkRumSellPrice : rumSellPrice;
			case BRANDY -> aged ? brandyVsopSellPrice : brandySellPrice;
		};
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("proost.json");
		ProostConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, ProostConfig.class);
			} catch (Exception e) {
				ProostMod.LOG.error("Could not read config/proost.json, using defaults", e);
			}
		}
		ProostConfig cfg = loaded != null ? loaded : new ProostConfig();
		cfg.openedFactor = Math.max(0, Math.min(1, cfg.openedFactor));
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			ProostMod.LOG.error("Could not write config/proost.json", e);
		}
	}
}
