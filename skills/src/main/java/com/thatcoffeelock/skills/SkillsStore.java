package com.thatcoffeelock.skills;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** Everyone's skill XP and perk ranks. Saved to <world>/skills.json. */
public final class SkillsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type TYPE = new TypeToken<Map<String, Profile>>() { }.getType();

	/** One player's skills. XP is kept as a total per skill; the level is worked out from it. */
	public static final class Profile {
		public String name = "";
		public Map<String, Double> xp = new HashMap<>();
		public Map<String, Integer> perks = new HashMap<>();

		public double xp(Skill skill) {
			return xp.getOrDefault(skill.id(), 0.0);
		}

		public int level(Skill skill) {
			return Skill.levelFor(xp(skill));
		}

		public int rank(Perk perk) {
			return perks.getOrDefault(perk.id(), 0);
		}

		public int pointsEarned(Skill skill) {
			return level(skill) / Skill.LEVELS_PER_POINT;
		}

		public int pointsSpent(Skill skill) {
			int spent = 0;
			for (Perk perk : Perk.of(skill)) {
				spent += rank(perk);
			}
			return spent;
		}

		public int pointsFree(Skill skill) {
			return Math.max(0, pointsEarned(skill) - pointsSpent(skill));
		}

		public int totalLevel() {
			int total = 0;
			for (Skill skill : Skill.values()) {
				total += level(skill);
			}
			return total;
		}
	}

	private static final Map<UUID, Profile> PROFILES = new HashMap<>();
	private static @Nullable Path file;
	private static boolean dirty;

	private SkillsStore() {
	}

	public static Profile of(ServerPlayer player) {
		Profile profile = PROFILES.computeIfAbsent(player.getUUID(), k -> new Profile());
		String name = player.getName().getString();
		if (!name.equals(profile.name)) {
			profile.name = name;
			changed();
		}
		return profile;
	}

	public static @Nullable Profile of(UUID player) {
		return PROFILES.get(player);
	}

	/** A fresh profile for a player who isn't online (used by the smoke test). */
	static Profile create(UUID player, String name) {
		Profile profile = new Profile();
		profile.name = name;
		PROFILES.put(player, profile);
		changed();
		return profile;
	}

	/** Players with any XP in the skill, best first. */
	public static List<Profile> top(Skill skill, int limit) {
		List<Profile> list = new ArrayList<>();
		for (Profile p : PROFILES.values()) {
			if (p.xp(skill) > 0) {
				list.add(p);
			}
		}
		list.sort((a, b) -> Double.compare(b.xp(skill), a.xp(skill)));
		return list.size() > limit ? list.subList(0, limit) : list;
	}

	public static void changed() {
		dirty = true;
	}

	public static void load(MinecraftServer server) {
		PROFILES.clear();
		dirty = false;
		file = server.getWorldPath(LevelResource.ROOT).resolve("skills.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Map<String, Profile> map = GSON.fromJson(reader, TYPE);
			if (map != null) {
				map.forEach((uuid, profile) -> {
					if (profile.xp == null) {
						profile.xp = new HashMap<>();
					}
					if (profile.perks == null) {
						profile.perks = new HashMap<>();
					}
					if (profile.name == null) {
						profile.name = "";
					}
					PROFILES.put(UUID.fromString(uuid), profile);
				});
			}
		} catch (Exception e) {
			SkillsMod.LOG.error("Could not read skills.json! Skills start fresh; the old file is kept as skills.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("skills.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	public static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		Map<String, Profile> out = new HashMap<>();
		PROFILES.forEach((uuid, profile) -> out.put(uuid.toString(), profile));
		try {
			Path tmp = file.resolveSibling("skills.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(out, TYPE, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			SkillsMod.LOG.error("Could not save skills.json", e);
		}
	}
}
