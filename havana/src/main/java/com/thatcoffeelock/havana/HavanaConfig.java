package com.thatcoffeelock.havana;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/havana.json: what the Market pays for tobacco and cigars, in Marks. Created with defaults on first start,
 * read again on the next one.
 *
 * Each step is worth more than what went into it: 3 cured tobacco (₥9) roll into a ₥12 cigar, 3 aged (₥21) into a
 * ₥30 Gran Reserva. A leaf is priced so a Colonycraft tobacco planter earns about what a farmhand does (~₥18 a day).
 */
public final class HavanaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static HavanaConfig current = new HavanaConfig();

	public double seedsSellPrice = 0.25;
	public double leafSellPrice = 1.5;
	public double curedSellPrice = 3;
	public double agedSellPrice = 7;
	public double cigarSellPrice = 12;
	public double granReservaSellPrice = 30;
	/** Added to a cigar's price when it's flavored. */
	public double flavorBonus = 3;
	/** A half-smoked cigar sells for its share of puffs left, times this. Lit cigars don't sell at all. */
	public double halfSmokedFactor = 0.5;

	public static HavanaConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("havana.json");
		HavanaConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, HavanaConfig.class);
			} catch (Exception e) {
				HavanaMod.LOG.error("Could not read config/havana.json, using defaults", e);
			}
		}
		HavanaConfig cfg = loaded != null ? loaded : new HavanaConfig();
		cfg.flavorBonus = Math.max(0, cfg.flavorBonus);
		cfg.halfSmokedFactor = Math.max(0, Math.min(1, cfg.halfSmokedFactor));
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			HavanaMod.LOG.error("Could not write config/havana.json", e);
		}
	}
}
