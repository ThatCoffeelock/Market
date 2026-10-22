package com.thatcoffeelock.tulpengekte;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Where the tulips are planted (a tulip is a short grass sprout, then a vanilla tulip, at a remembered spot), what
 * each one is, and what each player sold the Bloemist today. Saved to <world>/tulpengekte.json.
 */
final class TulpStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** One planted bulb. */
	static final class Plant {
		String variety;
		/** Game time it went in the ground. */
		long planted;

		Plant() {
		}

		Plant(Variety variety, long planted) {
			this.variety = variety.id();
			this.planted = planted;
		}

		Variety variety() {
			Variety v = Variety.byId(variety == null ? "" : variety);
			return v == null ? Variety.GOUDA : v;
		}
	}

	private static final class Snapshot {
		Map<String, Map<String, Plant>> plants = new HashMap<>();
		long saleDay = -1;
		Map<String, Map<String, Integer>> sold = new HashMap<>();
		int semperBred;
	}

	/** By dimension, then BlockPos#asLong. */
	private static final Map<String, Map<Long, Plant>> PLANTS = new HashMap<>();
	/** The Bloemist's day these sales belong to; a new day wipes them. */
	private static long saleDay = -1;
	/** Player UUID -> variety id -> bulbs sold to the Bloemist today. */
	private static final Map<String, Map<String, Integer>> SOLD = new HashMap<>();
	/** How many Semper Augustus bulbs have ever broken on this server. */
	private static int semperBred;
	private static @Nullable Path file;
	private static boolean dirty;

	private TulpStore() {
	}

	private static String dim(Level level) {
		return level.dimension().toString();
	}

	// ---------------------------------------------------------------- plants

	static @Nullable Plant plant(Level level, BlockPos pos) {
		Map<Long, Plant> plants = PLANTS.get(dim(level));
		return plants == null ? null : plants.get(pos.asLong());
	}

	static void addPlant(Level level, BlockPos pos, Plant plant) {
		PLANTS.computeIfAbsent(dim(level), k -> new HashMap<>()).put(pos.asLong(), plant);
		changed();
	}

	static void removePlant(Level level, BlockPos pos) {
		Map<Long, Plant> plants = PLANTS.get(dim(level));
		if (plants != null && plants.remove(pos.asLong()) != null) {
			changed();
		}
	}

	/** A copy, so the caller can remove while walking it. */
	static List<Long> plants(Level level) {
		Map<Long, Plant> plants = PLANTS.get(dim(level));
		return plants == null ? Collections.emptyList() : new ArrayList<>(plants.keySet());
	}

	// ---------------------------------------------------------------- the Bloemist's books

	/** Bulbs of this variety the player sold the Bloemist on this day. */
	static int sold(long day, String player, Variety variety) {
		if (day != saleDay) {
			return 0;
		}
		Map<String, Integer> mine = SOLD.get(player);
		return mine == null ? 0 : mine.getOrDefault(variety.id(), 0);
	}

	static void addSold(long day, String player, Variety variety, int count) {
		if (day != saleDay) {
			saleDay = day;
			SOLD.clear();
		}
		SOLD.computeIfAbsent(player, k -> new HashMap<>()).merge(variety.id(), count, Integer::sum);
		changed();
	}

	/** Counts a Semper Augustus that just broke. Returns how many there have been, this one included. */
	static int semperBred() {
		semperBred++;
		changed();
		return semperBred;
	}

	// ---------------------------------------------------------------- persistence

	static void changed() {
		dirty = true;
	}

	static void load(MinecraftServer server) {
		PLANTS.clear();
		SOLD.clear();
		saleDay = -1;
		semperBred = 0;
		dirty = false;
		file = server.getWorldPath(LevelResource.ROOT).resolve("tulpengekte.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap == null) {
				return;
			}
			if (snap.plants != null) {
				snap.plants.forEach((dim, map) -> {
					Map<Long, Plant> plants = new HashMap<>();
					map.forEach((pos, plant) -> plants.put(Long.parseLong(pos), plant));
					PLANTS.put(dim, plants);
				});
			}
			saleDay = snap.saleDay;
			if (snap.sold != null) {
				snap.sold.forEach((player, map) -> SOLD.put(player, new HashMap<>(map)));
			}
			semperBred = snap.semperBred;
		} catch (Exception e) {
			TulpMod.LOG.error("Could not read tulpengekte.json! Tulip beds start fresh; the old file is kept as tulpengekte.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("tulpengekte.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	static void save() {
		if (file == null) {
			return;
		}
		Snapshot snap = new Snapshot();
		PLANTS.forEach((dim, map) -> {
			Map<String, Plant> out = new HashMap<>();
			map.forEach((pos, plant) -> out.put(Long.toString(pos), plant));
			snap.plants.put(dim, out);
		});
		snap.saleDay = saleDay;
		SOLD.forEach((player, map) -> snap.sold.put(player, new HashMap<>(map)));
		snap.semperBred = semperBred;
		try {
			Path tmp = file.resolveSibling("tulpengekte.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			TulpMod.LOG.error("Could not save tulpengekte.json", e);
		}
	}
}
