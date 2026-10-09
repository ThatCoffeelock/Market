package com.thatcoffeelock.sellswords;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** config/sellswords.json. */
final class SellswordsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static SellswordsConfig current = new SellswordsConfig();

	/** What a recruit costs at a plain Mercenary Station, in ₥. */
	double hireCost = 250;
	/** Mercenaries anyone can have at once, before Leadership adds more (one per 25 levels). */
	int squadSize = 3;
	/** Multiplies every promotion's gold cost. */
	double promotionCostMultiplier = 1.0;
	/** Multiplies the damage mercenaries deal. */
	double damageMultiplier = 1.0;
	/** Mercenaries deal this much extra damage to illagers. They have history. */
	double illagerBonus = 0.25;
	/** How far idle mercenaries patrol around their station, in blocks. */
	int stationRadius = 50;

	static SellswordsConfig get() {
		return current;
	}

	static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("sellswords.json");
		SellswordsConfig cfg = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				cfg = GSON.fromJson(reader, SellswordsConfig.class);
			} catch (Exception e) {
				SellswordsMod.LOG.error("Could not read config/sellswords.json; using the defaults", e);
			}
		}
		current = cfg == null ? new SellswordsConfig() : cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(current, writer);
			}
		} catch (Exception e) {
			SellswordsMod.LOG.warn("Could not write config/sellswords.json", e);
		}
	}
}
