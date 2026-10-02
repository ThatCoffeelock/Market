package com.thatcoffeelock.havana;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Where the tobacco plants are (a tobacco plant is a vanilla potato crop, then a large fern, at a remembered spot)
 * and how far along each curing barrel's batches are. Saved to <world>/havana.json.
 */
final class HavanaStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** One stack curing (or aging) in one barrel slot. */
	static final class Batch {
		String kind;
		int count;
		/** Ticks spent in the barrel towards the next stage. */
		long progress;

		Batch() {
		}

		Batch(String kind, int count) {
			this.kind = kind;
			this.count = count;
		}
	}

	static final class Barrel {
		/** Game time the barrel was last brought up to date. */
		long last;
		/** By slot number. */
		Map<String, Batch> slots = new HashMap<>();
	}

	private static final class Snapshot {
		Map<String, List<Long>> crops = new HashMap<>();
		Map<String, Map<String, Barrel>> barrels = new HashMap<>();
	}

	/** By dimension, then BlockPos#asLong. */
	private static final Map<String, Set<Long>> CROPS = new HashMap<>();
	private static final Map<String, Map<Long, Barrel>> BARRELS = new HashMap<>();
	private static @Nullable Path file;
	private static boolean dirty;

	private HavanaStore() {
	}

	private static String dim(Level level) {
		return level.dimension().toString();
	}

	// ---------------------------------------------------------------- crops

	static boolean hasCrop(Level level, BlockPos pos) {
		Set<Long> crops = CROPS.get(dim(level));
		return crops != null && crops.contains(pos.asLong());
	}

	static void addCrop(Level level, BlockPos pos) {
		if (CROPS.computeIfAbsent(dim(level), k -> new HashSet<>()).add(pos.asLong())) {
			changed();
		}
	}

	static void removeCrop(Level level, BlockPos pos) {
		Set<Long> crops = CROPS.get(dim(level));
		if (crops != null && crops.remove(pos.asLong())) {
			changed();
		}
	}

	/** A copy, so the caller can remove while walking it. */
	static List<Long> crops(Level level) {
		Set<Long> crops = CROPS.get(dim(level));
		return crops == null ? Collections.emptyList() : new ArrayList<>(crops);
	}

	// ---------------------------------------------------------------- barrels

	static @Nullable Barrel barrel(Level level, BlockPos pos) {
		Map<Long, Barrel> barrels = BARRELS.get(dim(level));
		return barrels == null ? null : barrels.get(pos.asLong());
	}

	/** Starts tracking a curing barrel (if it isn't already) and returns its state. */
	static Barrel addBarrel(Level level, BlockPos pos) {
		Map<Long, Barrel> barrels = BARRELS.computeIfAbsent(dim(level), k -> new HashMap<>());
		Barrel barrel = barrels.get(pos.asLong());
		if (barrel == null) {
			barrel = new Barrel();
			barrel.last = level.getGameTime();
			barrels.put(pos.asLong(), barrel);
			changed();
		}
		return barrel;
	}

	static void removeBarrel(Level level, BlockPos pos) {
		Map<Long, Barrel> barrels = BARRELS.get(dim(level));
		if (barrels != null && barrels.remove(pos.asLong()) != null) {
			changed();
		}
	}

	static List<Long> barrels(Level level) {
		Map<Long, Barrel> barrels = BARRELS.get(dim(level));
		return barrels == null ? Collections.emptyList() : new ArrayList<>(barrels.keySet());
	}

	// ---------------------------------------------------------------- persistence

	static void changed() {
		dirty = true;
	}

	static void load(MinecraftServer server) {
		CROPS.clear();
		BARRELS.clear();
		dirty = false;
		file = server.getWorldPath(LevelResource.ROOT).resolve("havana.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap == null) {
				return;
			}
			if (snap.crops != null) {
				snap.crops.forEach((dim, list) -> CROPS.put(dim, new HashSet<>(list)));
			}
			if (snap.barrels != null) {
				snap.barrels.forEach((dim, map) -> {
					Map<Long, Barrel> barrels = new HashMap<>();
					map.forEach((pos, barrel) -> {
						if (barrel.slots == null) {
							barrel.slots = new HashMap<>();
						}
						barrels.put(Long.parseLong(pos), barrel);
					});
					BARRELS.put(dim, barrels);
				});
			}
		} catch (Exception e) {
			HavanaMod.LOG.error("Could not read havana.json! Tobacco fields and barrels start fresh; the old file is kept as havana.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("havana.json.broken"), StandardCopyOption.REPLACE_EXISTING);
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
		CROPS.forEach((dim, set) -> snap.crops.put(dim, new ArrayList<>(set)));
		BARRELS.forEach((dim, map) -> {
			Map<String, Barrel> out = new HashMap<>();
			map.forEach((pos, barrel) -> out.put(Long.toString(pos), barrel));
			snap.barrels.put(dim, out);
		});
		try {
			Path tmp = file.resolveSibling("havana.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			HavanaMod.LOG.error("Could not save havana.json", e);
		}
	}
}
