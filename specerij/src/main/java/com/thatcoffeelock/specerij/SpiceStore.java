package com.thatcoffeelock.specerij;

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
 * Where the spice plants are and how far along each one is, and how far along each Drying Rack's batches are.
 * Saved to <world>/specerij.json.
 */
final class SpiceStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** One spice plant. */
	static final class Plant {
		String spice;
		/** Ticks of growing towards ripe (a Hortus adds them at half speed). */
		double progress;
		/** Game time the plant was last brought up to date. */
		long last;

		Plant() {
		}

		Plant(Spice spice, long now) {
			this.spice = spice.id();
			this.last = now;
		}

		Spice spice() {
			Spice s = Spice.byId(spice == null ? "" : spice);
			return s == null ? Spice.PEPPER : s;
		}
	}

	/** One stack drying in one rack slot. */
	static final class Batch {
		String kind;
		int count;
		/** Ticks spent in the rack towards the next stage. */
		long progress;

		Batch() {
		}

		Batch(String kind, int count) {
			this.kind = kind;
			this.count = count;
		}
	}

	static final class Rack {
		/** Game time the rack was last brought up to date. */
		long last;
		/** By slot number. */
		Map<String, Batch> slots = new HashMap<>();
	}

	private static final class Snapshot {
		Map<String, Map<String, Plant>> plants = new HashMap<>();
		Map<String, Map<String, Rack>> racks = new HashMap<>();
	}

	/** By dimension, then BlockPos#asLong. */
	private static final Map<String, Map<Long, Plant>> PLANTS = new HashMap<>();
	private static final Map<String, Map<Long, Rack>> RACKS = new HashMap<>();
	private static @Nullable Path file;
	private static boolean dirty;

	private SpiceStore() {
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

	// ---------------------------------------------------------------- racks

	static @Nullable Rack rack(Level level, BlockPos pos) {
		Map<Long, Rack> racks = RACKS.get(dim(level));
		return racks == null ? null : racks.get(pos.asLong());
	}

	/** Starts tracking a drying rack (if it isn't already) and returns its state. */
	static Rack addRack(Level level, BlockPos pos) {
		Map<Long, Rack> racks = RACKS.computeIfAbsent(dim(level), k -> new HashMap<>());
		Rack rack = racks.get(pos.asLong());
		if (rack == null) {
			rack = new Rack();
			rack.last = level.getGameTime();
			racks.put(pos.asLong(), rack);
			changed();
		}
		return rack;
	}

	static void removeRack(Level level, BlockPos pos) {
		Map<Long, Rack> racks = RACKS.get(dim(level));
		if (racks != null && racks.remove(pos.asLong()) != null) {
			changed();
		}
	}

	static List<Long> racks(Level level) {
		Map<Long, Rack> racks = RACKS.get(dim(level));
		return racks == null ? Collections.emptyList() : new ArrayList<>(racks.keySet());
	}

	// ---------------------------------------------------------------- persistence

	static void changed() {
		dirty = true;
	}

	static void load(MinecraftServer server) {
		PLANTS.clear();
		RACKS.clear();
		dirty = false;
		file = server.getWorldPath(LevelResource.ROOT).resolve("specerij.json");
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
			if (snap.racks != null) {
				snap.racks.forEach((dim, map) -> {
					Map<Long, Rack> racks = new HashMap<>();
					map.forEach((pos, rack) -> {
						if (rack.slots == null) {
							rack.slots = new HashMap<>();
						}
						racks.put(Long.parseLong(pos), rack);
					});
					RACKS.put(dim, racks);
				});
			}
		} catch (Exception e) {
			SpiceMod.LOG.error("Could not read specerij.json! Spice gardens and racks start fresh; the old file is kept as specerij.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("specerij.json.broken"), StandardCopyOption.REPLACE_EXISTING);
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
		RACKS.forEach((dim, map) -> {
			Map<String, Rack> out = new HashMap<>();
			map.forEach((pos, rack) -> out.put(Long.toString(pos), rack));
			snap.racks.put(dim, out);
		});
		try {
			Path tmp = file.resolveSibling("specerij.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			SpiceMod.LOG.error("Could not save specerij.json", e);
		}
	}
}
