package com.thatcoffeelock.warehouse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Every warehouse, rack and dock in the world, saved to <world>/warehouses.json.
 *
 * Which rack belongs to which warehouse isn't saved, it's worked out: racks connect by touching (the core, or a rack
 * that's already connected). A rack that two warehouses reach at the same time is "disputed" and counts for neither,
 * so building a rack between two warehouses never quietly merges them.
 */
final class Warehouses {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
	static final String LABEL_TAG = "warehouse_label";

	private static final Map<String, Warehouse> BY_ID = new LinkedHashMap<>();
	/** Dimension -> rack positions. */
	private static final Map<String, Set<Long>> RACKS = new HashMap<>();
	/** Dimension -> dock positions. */
	private static final Map<String, Set<Long>> DOCKS = new HashMap<>();

	// worked out from the above, see network()
	private static final Map<String, String> CLAIMS = new HashMap<>();
	private static final Map<String, List<Long>> RACKS_OF = new HashMap<>();
	private static final Set<String> DISPUTED = new HashSet<>();
	private static boolean networkDirty = true;

	private static final Map<String, String> LABELS = new HashMap<>();
	private static @Nullable MinecraftServer server;
	private static boolean dirty;
	private static int ticks;

	private Warehouses() {
	}

	// ---------------------------------------------------------------- keys & lookups

	static String dim(Level level) {
		return level.dimension().toString();
	}

	private static String key(String dim, long pos) {
		return dim + "@" + pos;
	}

	static @Nullable ServerLevel level(String dim) {
		if (server == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (dim(level).equals(dim)) {
				return level;
			}
		}
		return null;
	}

	static void markDirty() {
		dirty = true;
	}

	static void networkChanged() {
		networkDirty = true;
		dirty = true;
	}

	static Collection<Warehouse> all() {
		return BY_ID.values();
	}

	static @Nullable Warehouse byId(String id) {
		return BY_ID.get(id);
	}

	/** The warehouse whose core stands here. */
	static @Nullable Warehouse coreAt(Level level, BlockPos pos) {
		String dim = dim(level);
		for (Warehouse w : BY_ID.values()) {
			if (!w.packed && w.pos.equals(pos) && w.dimension.equals(dim)) {
				return w;
			}
		}
		return null;
	}

	/** The warehouse a core or a counted rack at this spot belongs to. */
	static @Nullable Warehouse at(Level level, BlockPos pos) {
		network();
		String id = CLAIMS.get(key(dim(level), pos.asLong()));
		return id == null ? null : BY_ID.get(id);
	}

	static boolean isRack(Level level, BlockPos pos) {
		Set<Long> racks = RACKS.get(dim(level));
		return racks != null && racks.contains(pos.asLong());
	}

	static boolean isDisputed(Level level, BlockPos pos) {
		network();
		return DISPUTED.contains(key(dim(level), pos.asLong()));
	}

	static boolean isDock(Level level, BlockPos pos) {
		Set<Long> docks = DOCKS.get(dim(level));
		return docks != null && docks.contains(pos.asLong());
	}

	static List<Long> racksOf(Warehouse w) {
		network();
		return RACKS_OF.getOrDefault(w.id, List.of());
	}

	/** A warehouse whose core or counted rack touches this spot (one of its six sides). */
	static @Nullable Warehouse touching(Level level, BlockPos pos) {
		for (int[] d : SIDES) {
			Warehouse w = at(level, pos.offset(d[0], d[1], d[2]));
			if (w != null && !w.packed) {
				return w;
			}
		}
		return null;
	}

	/** Warehouses (unpacked) with their core within {@code radius} blocks of pos, nearest first. */
	static List<Warehouse> near(Level level, BlockPos pos, int radius) {
		String dim = dim(level);
		List<Warehouse> found = new ArrayList<>();
		for (Warehouse w : BY_ID.values()) {
			if (!w.packed && w.dimension.equals(dim) && w.pos.distSqr(pos) <= (double) radius * radius) {
				found.add(w);
			}
		}
		found.sort(Comparator.comparingDouble(w -> w.pos.distSqr(pos)));
		return found;
	}

	/** The dock nearest to pos within radius, if any. */
	static @Nullable BlockPos nearestDock(Level level, double x, double y, double z, int radius) {
		Set<Long> docks = DOCKS.get(dim(level));
		if (docks == null) {
			return null;
		}
		BlockPos best = null;
		double bestDist = (double) radius * radius;
		for (long l : docks) {
			BlockPos p = BlockPos.of(l);
			double dx = p.getX() + 0.5 - x;
			double dy = p.getY() + 0.5 - y;
			double dz = p.getZ() + 0.5 - z;
			double dist = dx * dx + dy * dy + dz * dz;
			if (dist <= bestDist) {
				bestDist = dist;
				best = p;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- changes

	static Warehouse create(ServerLevel level, BlockPos pos, String owner, String ownerName, String name) {
		Warehouse w = new Warehouse(UUID.randomUUID().toString(), name, owner, ownerName, dim(level), pos.immutable());
		BY_ID.put(w.id, w);
		networkChanged();
		return w;
	}

	static void unpack(Warehouse w, ServerLevel level, BlockPos pos) {
		w.packed = false;
		w.dimension = dim(level);
		w.pos = pos.immutable();
		w.changed();
		networkChanged();
	}

	/** Picks the warehouse up: the stock stays in the file, the returned core item knows which warehouse it is. */
	static ItemStack pack(Warehouse w) {
		ServerLevel level = level(w.dimension);
		if (level != null) {
			removeLabel(level, labelTag(w.dimension, w.pos));
		}
		w.packed = true;
		w.changed();
		networkChanged();
		return Parts.packedCore(w);
	}

	static void addRack(Level level, BlockPos pos) {
		RACKS.computeIfAbsent(dim(level), k -> new HashSet<>()).add(pos.asLong());
		networkChanged();
	}

	static void removeRack(Level level, BlockPos pos) {
		Set<Long> racks = RACKS.get(dim(level));
		if (racks != null && racks.remove(pos.asLong())) {
			networkChanged();
		}
	}

	static void addDock(ServerLevel level, BlockPos pos) {
		DOCKS.computeIfAbsent(dim(level), k -> new HashSet<>()).add(pos.asLong());
		dirty = true;
		String tag = labelTag(dim(level), pos);
		removeLabel(level, tag);
		summonLabel(level, tag, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, "{text:\"Loading Dock\",color:\"yellow\",bold:true}");
	}

	static void removeDock(ServerLevel level, BlockPos pos) {
		Set<Long> docks = DOCKS.get(dim(level));
		if (docks != null && docks.remove(pos.asLong())) {
			dirty = true;
		}
		removeLabel(level, labelTag(dim(level), pos));
	}

	// ---------------------------------------------------------------- which racks count for which warehouse

	/**
	 * Spreads out from every core at once, one rack per step. A rack reached by one warehouse joins it; a rack two
	 * warehouses reach in the same step is disputed and blocks the way. Racks too far from their core, or beyond a
	 * warehouse's rack limit, don't count.
	 */
	private static void network() {
		if (!networkDirty) {
			return;
		}
		networkDirty = false;
		CLAIMS.clear();
		RACKS_OF.clear();
		DISPUTED.clear();
		WarehouseConfig config = WarehouseConfig.get();
		Map<String, List<Warehouse>> byDim = new HashMap<>();
		for (Warehouse w : BY_ID.values()) {
			w.racks = 0;
			w.disputed = 0;
			if (!w.packed) {
				byDim.computeIfAbsent(w.dimension, k -> new ArrayList<>()).add(w);
				CLAIMS.put(key(w.dimension, w.pos.asLong()), w.id);
				RACKS_OF.put(w.id, new ArrayList<>());
			}
		}
		for (Map.Entry<String, List<Warehouse>> entry : byDim.entrySet()) {
			String dim = entry.getKey();
			Set<Long> racks = RACKS.getOrDefault(dim, Set.of());
			if (racks.isEmpty()) {
				continue;
			}
			Map<Long, Warehouse> claimed = new HashMap<>();
			Set<Long> disputed = new HashSet<>();
			Map<Long, Warehouse> frontier = new LinkedHashMap<>();
			for (Warehouse w : entry.getValue()) {
				frontier.put(w.pos.asLong(), w);
			}
			while (!frontier.isEmpty()) {
				Map<Long, Set<Warehouse>> reached = new LinkedHashMap<>();
				for (Map.Entry<Long, Warehouse> f : frontier.entrySet()) {
					BlockPos from = BlockPos.of(f.getKey());
					Warehouse w = f.getValue();
					for (int[] d : SIDES) {
						BlockPos to = from.offset(d[0], d[1], d[2]);
						long l = to.asLong();
						if (!racks.contains(l) || claimed.containsKey(l) || disputed.contains(l)
							|| Math.abs(to.getX() - w.pos.getX()) > config.rackReach
							|| Math.abs(to.getY() - w.pos.getY()) > config.rackReach
							|| Math.abs(to.getZ() - w.pos.getZ()) > config.rackReach) {
							continue;
						}
						reached.computeIfAbsent(l, k -> new LinkedHashSet<>()).add(w);
					}
				}
				Map<Long, Warehouse> next = new LinkedHashMap<>();
				for (Map.Entry<Long, Set<Warehouse>> r : reached.entrySet()) {
					long l = r.getKey();
					if (r.getValue().size() > 1) {
						disputed.add(l);
						DISPUTED.add(key(dim, l));
						for (Warehouse w : r.getValue()) {
							w.disputed++;
						}
						continue;
					}
					Warehouse w = r.getValue().iterator().next();
					if (w.racks >= config.maxRacks) {
						continue;
					}
					w.racks++;
					claimed.put(l, w);
					CLAIMS.put(key(dim, l), w.id);
					RACKS_OF.get(w.id).add(l);
					next.put(l, w);
				}
				frontier = next;
			}
		}
		for (Warehouse w : BY_ID.values()) {
			w.version++;
		}
	}

	/** Racks always count right away, even before the next tick. */
	static void refresh() {
		network();
	}

	// ---------------------------------------------------------------- every tick

	static void tick() {
		if (server == null) {
			return;
		}
		ticks++;
		network();
		if (ticks % 20 == 0) {
			sweep();
		}
		if (ticks % 100 == 0) {
			validate();
			labels();
		}
		if (ticks % 600 == 0 && dirty) {
			save();
		}
	}

	private static boolean isIntake(@Nullable Component name) {
		if (name == null) {
			return false;
		}
		String s = name.getString().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		return s.contains("intake") || (s.contains("station") && s.contains("dropoff"));
	}

	/** Moves everything the warehouse takes out of a container. Returns how many items moved. */
	static int pull(Warehouse w, Container from) {
		int moved = 0;
		for (int i = 0; i < from.getContainerSize(); i++) {
			ItemStack stack = from.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			int n = w.deposit(stack);
			if (n > 0) {
				moved += n;
				if (stack.isEmpty()) {
					from.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		if (moved > 0) {
			from.setChanged();
		}
		return moved;
	}

	/**
	 * Hoppers can fill a rack's barrel, and players or trains can fill a chest named "Warehouse Intake" (or a Cargo
	 * Train "Drop-off Station") that touches the warehouse. Once a second, all of that goes onto the shelves.
	 */
	static void sweep() {
		for (Warehouse w : BY_ID.values()) {
			if (w.packed) {
				continue;
			}
			ServerLevel level = level(w.dimension);
			if (level == null || !level.isLoaded(w.pos)) {
				continue;
			}
			List<BlockPos> parts = new ArrayList<>();
			parts.add(w.pos);
			for (long l : racksOf(w)) {
				parts.add(BlockPos.of(l));
			}
			Set<Long> seen = new HashSet<>();
			for (BlockPos part : parts) {
				if (!level.isLoaded(part)) {
					continue;
				}
				if (!part.equals(w.pos) && level.getBlockEntity(part) instanceof Container barrel) {
					pull(w, barrel);
				}
				for (int[] d : SIDES) {
					BlockPos side = part.offset(d[0], d[1], d[2]);
					if (!seen.add(side.asLong()) || !level.isLoaded(side) || isRack(level, side)) {
						continue;
					}
					BlockEntity entity = level.getBlockEntity(side);
					if (entity instanceof Container box && entity instanceof Nameable named && isIntake(named.getCustomName())) {
						pull(w, box);
					}
				}
			}
		}
	}

	/**
	 * Racks, docks and cores that aren't there any more (blown up, burnt, pushed by a piston) are forgotten. A core
	 * that vanished drops a packed-up core on the spot, so the stock is never lost.
	 */
	static void validate() {
		for (Map.Entry<String, Set<Long>> entry : RACKS.entrySet()) {
			ServerLevel level = level(entry.getKey());
			if (level == null) {
				continue;
			}
			if (entry.getValue().removeIf(l -> level.isLoaded(BlockPos.of(l)) && !Parts.isRackBlock(level.getBlockState(BlockPos.of(l))))) {
				networkChanged();
			}
		}
		for (Map.Entry<String, Set<Long>> entry : DOCKS.entrySet()) {
			ServerLevel level = level(entry.getKey());
			if (level == null) {
				continue;
			}
			List<Long> gone = new ArrayList<>();
			for (long l : entry.getValue()) {
				BlockPos p = BlockPos.of(l);
				if (level.isLoaded(p) && !Parts.isDockBlock(level.getBlockState(p))) {
					gone.add(l);
				}
			}
			for (long l : gone) {
				removeDock(level, BlockPos.of(l));
			}
		}
		for (Warehouse w : new ArrayList<>(BY_ID.values())) {
			if (w.packed) {
				continue;
			}
			ServerLevel level = level(w.dimension);
			if (level != null && level.isLoaded(w.pos) && !Parts.isCoreBlock(level.getBlockState(w.pos))) {
				WarehouseMod.LOG.info("The core of warehouse '{}' at {} is gone; dropping it packed up so nothing is lost", w.name, w.pos.toShortString());
				Block.popResource(level, w.pos, pack(w));
			}
		}
	}

	// ---------------------------------------------------------------- floating name labels

	private static String labelTag(String dim, BlockPos pos) {
		return "whl_" + Integer.toHexString(dim.hashCode()) + "_" + Long.toHexString(pos.asLong());
	}

	static String clean(String text) {
		String cleaned = text.replaceAll("[\"\\\\§\\n\\r]", "").trim();
		return cleaned.substring(0, Math.min(40, cleaned.length()));
	}

	private static void removeLabel(ServerLevel level, String tag) {
		LABELS.remove(tag);
		Cmd.run(level, "kill @e[type=minecraft:text_display,tag=" + tag + "]");
	}

	private static void summonLabel(ServerLevel level, String tag, double x, double y, double z, String text) {
		Cmd.run(level, "summon minecraft:text_display " + Cmd.pos(x, y, z) + " {Tags:[\"" + LABEL_TAG + "\",\"" + tag
			+ "\"],billboard:\"center\",background:0,shadow:1b,text:" + text + "}");
	}

	/** "Iron Depot" and "12,340 / 40,960 items" floating over each core. Only redrawn when it changes. */
	static void labels() {
		for (Warehouse w : BY_ID.values()) {
			if (w.packed) {
				continue;
			}
			ServerLevel level = level(w.dimension);
			if (level == null || !level.isLoaded(w.pos)) {
				continue;
			}
			String tag = labelTag(w.dimension, w.pos);
			String name = "{text:\"" + clean(w.name) + "\",color:\"gold\",bold:true}";
			String fill = "{text:\"" + Gui.n(w.total()) + " / " + Gui.n(w.capacity()) + " items\",color:\""
				+ (w.space() == 0 ? "red" : "gray") + "\"}";
			String both = name + fill;
			if (both.equals(LABELS.get(tag))) {
				continue;
			}
			removeLabel(level, tag);
			double x = w.pos.getX() + 0.5;
			double z = w.pos.getZ() + 0.5;
			summonLabel(level, tag, x, w.pos.getY() + 1.55, z, name);
			summonLabel(level, tag, x, w.pos.getY() + 1.3, z, fill);
			LABELS.put(tag, both);
		}
	}

	// ---------------------------------------------------------------- saving

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("warehouses.json");
	}

	private static DynamicOps<JsonElement> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static void load(MinecraftServer srv) {
		server = srv;
		BY_ID.clear();
		RACKS.clear();
		DOCKS.clear();
		LABELS.clear();
		networkDirty = true;
		dirty = false;
		ticks = 0;
		Path file = file(srv);
		if (!Files.exists(file)) {
			return;
		}
		try {
			fromJson(srv, Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			WarehouseMod.LOG.error("Could not read {}; starting without warehouses (the file is kept as .broken)", file, e);
			BY_ID.clear();
			RACKS.clear();
			DOCKS.clear();
			try {
				Files.copy(file, file.resolveSibling("warehouses.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
		}
	}

	static void save() {
		if (server == null) {
			return;
		}
		Path file = file(server);
		try {
			Path tmp = file.resolveSibling("warehouses.json.tmp");
			Files.writeString(tmp, toJson(server), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			WarehouseMod.LOG.error("Could not save {}", file, e);
		}
	}

	static void shutdown() {
		save();
		server = null;
		BY_ID.clear();
		RACKS.clear();
		DOCKS.clear();
		CLAIMS.clear();
		RACKS_OF.clear();
		DISPUTED.clear();
		LABELS.clear();
	}

	static String toJson(MinecraftServer server) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = new JsonObject();
		JsonArray list = new JsonArray();
		for (Warehouse w : BY_ID.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", w.id);
			o.addProperty("name", w.name);
			o.addProperty("owner", w.owner);
			o.addProperty("owner_name", w.ownerName);
			o.addProperty("locked", w.locked);
			o.addProperty("filter", w.filter.name());
			o.addProperty("dimension", w.dimension);
			o.addProperty("x", w.pos.getX());
			o.addProperty("y", w.pos.getY());
			o.addProperty("z", w.pos.getZ());
			o.addProperty("packed", w.packed);
			JsonArray items = new JsonArray();
			for (Map.Entry<Warehouse.Key, Long> e : w.items.entrySet()) {
				ItemStack.CODEC.encodeStart(ops, e.getKey().stack).result().ifPresentOrElse(json -> {
					JsonObject entry = new JsonObject();
					entry.add("item", json);
					entry.addProperty("count", e.getValue());
					items.add(entry);
				}, () -> WarehouseMod.LOG.warn("Could not save {} x {} in warehouse '{}'", e.getValue(), e.getKey().stack, w.name));
			}
			o.add("items", items);
			list.add(o);
		}
		root.add("warehouses", list);
		root.add("racks", positions(RACKS));
		root.add("docks", positions(DOCKS));
		return GSON.toJson(root);
	}

	private static JsonObject positions(Map<String, Set<Long>> map) {
		JsonObject o = new JsonObject();
		for (Map.Entry<String, Set<Long>> e : map.entrySet()) {
			JsonArray list = new JsonArray();
			for (long l : e.getValue()) {
				BlockPos p = BlockPos.of(l);
				JsonArray xyz = new JsonArray();
				xyz.add(p.getX());
				xyz.add(p.getY());
				xyz.add(p.getZ());
				list.add(xyz);
			}
			o.add(e.getKey(), list);
		}
		return o;
	}

	private static void readPositions(JsonObject root, String name, Map<String, Set<Long>> into) {
		if (!root.has(name)) {
			return;
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(name).entrySet()) {
			Set<Long> set = into.computeIfAbsent(e.getKey(), k -> new HashSet<>());
			for (JsonElement p : e.getValue().getAsJsonArray()) {
				JsonArray xyz = p.getAsJsonArray();
				set.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()).asLong());
			}
		}
	}

	static void fromJson(MinecraftServer server, String json) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (root.has("warehouses")) {
			for (JsonElement e : root.getAsJsonArray("warehouses")) {
				JsonObject o = e.getAsJsonObject();
				Warehouse w = new Warehouse(o.get("id").getAsString(), o.get("name").getAsString(), o.get("owner").getAsString(),
					o.get("owner_name").getAsString(), o.get("dimension").getAsString(),
					new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
				w.locked = o.has("locked") && o.get("locked").getAsBoolean();
				w.filter = o.has("filter") ? Category.byName(o.get("filter").getAsString()) : Category.ALL;
				w.packed = o.has("packed") && o.get("packed").getAsBoolean();
				for (JsonElement ie : o.getAsJsonArray("items")) {
					JsonObject entry = ie.getAsJsonObject();
					long count = entry.get("count").getAsLong();
					ItemStack.CODEC.parse(ops, entry.get("item")).result().ifPresentOrElse(stack -> w.restore(stack, count),
						() -> WarehouseMod.LOG.warn("Warehouse '{}': dropped {} of an item that no longer exists: {}", w.name, count, entry.get("item")));
				}
				BY_ID.put(w.id, w);
			}
		}
		readPositions(root, "racks", RACKS);
		readPositions(root, "docks", DOCKS);
		networkDirty = true;
	}
}
