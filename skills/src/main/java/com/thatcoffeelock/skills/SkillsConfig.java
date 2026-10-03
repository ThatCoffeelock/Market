package com.thatcoffeelock.skills;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** config/skills.json. Created with defaults on first start; /skills admin reload re-reads it. */
public final class SkillsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static SkillsConfig current = new SkillsConfig();

	/** Multiplies all skill XP. 2.0 = level twice as fast. */
	public double xpMultiplier = 1.0;
	/** Per-skill XP multipliers on top of xpMultiplier, by skill id (e.g. "mining": 0.5). Missing = 1.0. */
	public Map<String, Double> skillXpMultipliers = new LinkedHashMap<>();
	/** Vanilla XP levels it costs to reset one skill's perks and get the points back. 0 = free. */
	public int respecCostLevels = 5;
	/** Show "+4 Mining" in the action bar when you earn skill XP. */
	public boolean xpPopups = true;
	/** Tell everyone in chat when someone reaches level 50 and 100 in a skill. */
	public boolean announceMilestones = true;
	/** Minutes without moving the camera before travel and fishing XP stops (anti-AFK). 0 = never stops. */
	public int afkMinutes = 3;

	public static SkillsConfig get() {
		return current;
	}

	public double multiplier(Skill skill) {
		Double m = skillXpMultipliers == null ? null : skillXpMultipliers.get(skill.id());
		return Math.max(0, xpMultiplier) * (m == null ? 1.0 : Math.max(0, m));
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("skills.json");
		SkillsConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, SkillsConfig.class);
			} catch (Exception e) {
				SkillsMod.LOG.error("Could not read config/skills.json, using defaults", e);
			}
		}
		SkillsConfig cfg = loaded != null ? loaded : new SkillsConfig();
		if (cfg.skillXpMultipliers == null) {
			cfg.skillXpMultipliers = new LinkedHashMap<>();
		}
		cfg.respecCostLevels = Math.max(0, cfg.respecCostLevels);
		cfg.afkMinutes = Math.max(0, cfg.afkMinutes);
		current = cfg;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			SkillsMod.LOG.error("Could not write config/skills.json", e);
		}
	}
}
