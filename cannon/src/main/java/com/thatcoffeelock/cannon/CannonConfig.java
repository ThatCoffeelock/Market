package com.thatcoffeelock.cannon;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/cannon.json. Created with defaults on first start, read again on the next one. {@code /cannon blockdamage on|off}
 * (ops) flips the switch in-game and saves it.
 */
public final class CannonConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static CannonConfig current = new CannonConfig();

	/** Cannonballs break blocks like TNT does. Off by default: they hurt mobs and players, but leave your walls alone. */
	public boolean cannonballsBreakBlocks = false;

	public static CannonConfig get() {
		return current;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("cannon.json");
	}

	public static void load() {
		Path file = file();
		CannonConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, CannonConfig.class);
			} catch (Exception e) {
				CannonMod.LOG.error("Could not read config/cannon.json, using defaults", e);
			}
		}
		current = loaded != null ? loaded : new CannonConfig();
		save();
	}

	public static void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(current, writer);
			}
		} catch (Exception e) {
			CannonMod.LOG.error("Could not write config/cannon.json", e);
		}
	}
}
