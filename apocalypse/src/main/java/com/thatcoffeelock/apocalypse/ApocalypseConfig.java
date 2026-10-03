package com.thatcoffeelock.apocalypse;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** config/apocalypse.json. Created with defaults on first start; /apocalypse admin reload re-reads it. */
public final class ApocalypseConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static ApocalypseConfig current = new ApocalypseConfig();

	// ---- hordes
	/** Spawn zombie hordes around survivors in the overworld. */
	public boolean hordes = true;
	/** Seconds between hordes for each player at night. */
	public int nightHordeSeconds = 75;
	/** Seconds between hordes for each player during the day. 0 = no daytime hordes. */
	public int dayHordeSeconds = 240;
	/** Smallest and largest horde on day 1. */
	public int hordeSizeMin = 4;
	public int hordeSizeMax = 8;
	/** Hordes grow by this many zombies per day survived... */
	public double hordeGrowthPerDay = 0.5;
	/** ...up to this many. */
	public int hordeSizeCap = 24;
	/** No new hordes while this many zombies are already within 64 blocks of the player. */
	public int maxZombiesNearPlayer = 40;
	/** How far from the player hordes appear, in blocks. */
	public int spawnDistanceMin = 24;
	public int spawnDistanceMax = 44;
	/** Idle hordes smell survivors this far away and shamble toward them. */
	public int scentRange = 80;
	/** One zombie sees you, every zombie in its horde within this many blocks sees you. */
	public int shareAggroRange = 32;
	/** Loose zombies this close to a horde leader join the horde. */
	public int joinRange = 10;
	/** Hordes stop recruiting and merging at this size. */
	public int maxHordeSize = 40;
	/** Horde members move this much faster than a lone zombie (0.15 = +15%). */
	public double hordeSpeedBonus = 0.10;
	/** Horde members track targets this far (vanilla zombies: 35). */
	public double hordeFollowRange = 48;

	// ---- bases
	/** Default radius of a player base (/apocalypse base set). No zombies spawn inside. */
	public int baseRadius = 48;
	/** Biggest base anyone can claim. */
	public int maxBaseRadius = 96;
	/** You can't claim a base with zombies this close: it's a home, not a panic button. */
	public int baseClearRange = 24;

	// ---- horde night
	/** Every Nth night is Horde Night. 0 = never. */
	public int hordeNightEvery = 7;
	/** Horde Night: hordes come this many times as often... */
	public double hordeNightFrequency = 3.0;
	/** ...this many times bigger... */
	public double hordeNightSize = 1.75;
	/** ...and with this extra speed. */
	public double hordeNightSpeedBonus = 0.20;
	/** Horde Night zombie cap near each player. */
	public int hordeNightMaxZombies = 70;

	// ---- noise
	/** Noise (breaking blocks, fighting, sprinting, bells) attracts zombies. */
	public boolean noise = true;
	/** Ringing a bell lures every zombie this far away to it. */
	public int bellRadius = 64;
	public int bellSeconds = 40;
	public int breakNoiseRadius = 14;
	public int fightNoiseRadius = 20;
	public int sprintNoiseRadius = 10;

	// ---- infection
	/** Zombie bites can infect players. A golden apple cures it. */
	public boolean infection = true;
	/** Chance a zombie hit infects you (doubled on Horde Night). */
	public double infectionChance = 0.12;
	/** Minutes from bite to turning. */
	public int infectionMinutes = 20;
	/** Players who die infected, or are killed by a zombie, rise as a zombie wearing their name (and soon their gear). */
	public boolean riseAsZombie = true;

	// ---- doors
	/** Horde zombies chew through doors, trapdoors, fence gates and glass to reach you. Iron and copper hold. */
	public boolean breakDoors = true;
	/** ...and wooden planks too. Off by default because it's a bit much. */
	public boolean breakPlanks = false;
	/** Seconds for one zombie to get through. Every extra zombie at the same door makes it faster. */
	public int breakSeconds = 10;

	public static ApocalypseConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("apocalypse.json");
		ApocalypseConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, ApocalypseConfig.class);
			} catch (Exception e) {
				ApocalypseMod.LOG.error("Could not read config/apocalypse.json, using defaults", e);
			}
		}
		ApocalypseConfig cfg = loaded != null ? loaded : new ApocalypseConfig();
		cfg.nightHordeSeconds = Math.max(5, cfg.nightHordeSeconds);
		cfg.dayHordeSeconds = Math.max(0, cfg.dayHordeSeconds);
		cfg.hordeSizeMin = Math.max(1, cfg.hordeSizeMin);
		cfg.hordeSizeMax = Math.max(cfg.hordeSizeMin, cfg.hordeSizeMax);
		cfg.hordeSizeCap = Math.max(cfg.hordeSizeMax, cfg.hordeSizeCap);
		cfg.spawnDistanceMin = Math.max(8, cfg.spawnDistanceMin);
		cfg.spawnDistanceMax = Math.max(cfg.spawnDistanceMin + 1, cfg.spawnDistanceMax);
		cfg.maxHordeSize = Math.max(2, cfg.maxHordeSize);
		cfg.hordeNightEvery = Math.max(0, cfg.hordeNightEvery);
		cfg.hordeNightFrequency = Math.max(1, cfg.hordeNightFrequency);
		cfg.hordeNightSize = Math.max(1, cfg.hordeNightSize);
		cfg.infectionChance = Math.max(0, Math.min(1, cfg.infectionChance));
		cfg.infectionMinutes = Math.max(1, cfg.infectionMinutes);
		cfg.breakSeconds = Math.max(1, cfg.breakSeconds);
		cfg.maxBaseRadius = Math.max(8, cfg.maxBaseRadius);
		cfg.baseRadius = Math.max(8, Math.min(cfg.maxBaseRadius, cfg.baseRadius));
		cfg.baseClearRange = Math.max(0, cfg.baseClearRange);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			ApocalypseMod.LOG.error("Could not write config/apocalypse.json", e);
		}
	}
}
