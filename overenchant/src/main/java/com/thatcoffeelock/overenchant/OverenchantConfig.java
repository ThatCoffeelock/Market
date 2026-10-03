package com.thatcoffeelock.overenchant;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/overenchant.json. Created with defaults on first start; /overenchant reload re-reads it.
 * <p>
 * The new maximum level of an enchantment is {@code old max × multiplier + bonusLevels}, never above {@link #cap} and
 * never below what it was.
 */
public final class OverenchantConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile OverenchantConfig current;

	/** 2.0 doubles every maximum: Sharpness V becomes X, Unbreaking III becomes VI, Protection IV becomes VIII. */
	public double multiplier = 2.0;
	/** Levels added on top, after multiplying. */
	public int bonusLevels = 0;
	/**
	 * Nothing is raised above this level. Vanilla clients only have names for I to X, so with anything higher they show
	 * "enchantment.level.11" instead of "XI" (clients that have this mod installed show it properly).
	 */
	public int cap = 10;
	/** Also give enchantments that only have one level (Mending, Silk Touch, Infinity...) a second one. That does nothing useful. */
	public boolean raiseSingleLevel = false;

	/** The current settings. Loads the file the first time it's asked, which can be before the mod itself starts. */
	public static OverenchantConfig get() {
		OverenchantConfig config = current;
		if (config == null) {
			config = load();
		}
		return config;
	}

	/** The raised maximum for an enchantment whose vanilla maximum is {@code original}. */
	public int raise(int original) {
		if (original <= 1 && !raiseSingleLevel) {
			return original;
		}
		long raised = Math.round(original * multiplier) + bonusLevels;
		return (int) Math.max(original, Math.min(cap, raised));
	}

	public static synchronized OverenchantConfig load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("overenchant.json");
		OverenchantConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, OverenchantConfig.class);
			} catch (Exception e) {
				OverenchantMod.LOG.error("Could not read config/overenchant.json, using defaults", e);
			}
		}
		OverenchantConfig config = loaded != null ? loaded : new OverenchantConfig();
		config.multiplier = Math.max(1.0, Math.min(100.0, config.multiplier));
		config.bonusLevels = Math.max(0, config.bonusLevels);
		config.cap = Math.max(1, Math.min(255, config.cap));
		current = config;
		if (config.cap > 10) {
			OverenchantMod.LOG.warn("cap is {}: players without this mod on their client will see \"enchantment.level.N\" for levels above X", config.cap);
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (Exception e) {
			OverenchantMod.LOG.error("Could not write config/overenchant.json", e);
		}
		return config;
	}
}
