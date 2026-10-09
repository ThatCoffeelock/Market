package com.thatcoffeelock.blimey;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/blimey.json. Created with defaults on first start; /blimey reload (ops) re-reads it.
 *
 * Diesel is the balancing knob: a bucket keeps the engines running at cruising speed for {@link #ticksPerBucket}
 * ticks. Hovering burns less, climbing and racing burn more, and the Fuel economy refits cut all of it.
 */
public final class BlimeyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static BlimeyConfig current = new BlimeyConfig();

	// ---------------------------------------------------------------- flying

	/** Ticks one bucket of diesel lasts at cruising speed (6000 = five minutes). */
	public double ticksPerBucket = 6000;
	/** Share of the cruising burn that just hovering in the air costs. */
	public double hoverBurn = 0.35;
	/** Extra burn while climbing, on top of everything else. */
	public double climbBurn = 0.3;
	/** Top speed in blocks per tick before refits (0.5 = 36 km/h). */
	public double topSpeed = 0.5;
	/** How close to the world's ceiling the top of the envelope may go. */
	public int ceilingMargin = 4;

	// ---------------------------------------------------------------- bombs: explosion power (creeper 3, TNT 4, wither 7)

	public float smallBombPower = 4.0f;
	public float bigBombPower = 7.0f;
	public float hugeBombPower = 12.0f;
	/** False makes bombs as harmless to buildings as cannonballs. */
	public boolean bombsBreakBlocks = true;
	/** Seconds a bomb set on the ground by hand hisses before it goes off. */
	public double fuseSeconds = 4;

	public static BlimeyConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("blimey.json");
		BlimeyConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, BlimeyConfig.class);
			} catch (Exception e) {
				BlimeyMod.LOG.error("Could not read config/blimey.json, using defaults", e);
			}
		}
		BlimeyConfig cfg = loaded != null ? loaded : new BlimeyConfig();
		cfg.ticksPerBucket = Math.max(20, cfg.ticksPerBucket);
		cfg.hoverBurn = Math.max(0, cfg.hoverBurn);
		cfg.climbBurn = Math.max(0, cfg.climbBurn);
		cfg.topSpeed = Math.max(0.05, Math.min(2.0, cfg.topSpeed));
		cfg.ceilingMargin = Math.max(0, cfg.ceilingMargin);
		cfg.smallBombPower = Math.max(0, Math.min(20, cfg.smallBombPower));
		cfg.bigBombPower = Math.max(0, Math.min(20, cfg.bigBombPower));
		cfg.hugeBombPower = Math.max(0, Math.min(20, cfg.hugeBombPower));
		cfg.fuseSeconds = Math.max(0.5, cfg.fuseSeconds);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			BlimeyMod.LOG.error("Could not write config/blimey.json", e);
		}
	}
}
