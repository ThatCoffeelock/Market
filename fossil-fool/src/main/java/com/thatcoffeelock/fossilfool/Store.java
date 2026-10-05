package com.thatcoffeelock.fossilfool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** Saves rigs, tanks, refineries and opened oil pockets to {@code <world>/fossilfool.json}. */
final class Store {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static @Nullable MinecraftServer server;
	private static boolean dirty;

	private Store() {
	}

	static void changed() {
		dirty = true;
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("fossilfool.json");
	}

	private static DynamicOps<JsonElement> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static void load(MinecraftServer srv) {
		server = srv;
		Rigs.reset();
		Machines.reset();
		Machines.start(srv);
		Pockets.reset();
		Labels.reset();
		dirty = false;
		Path file = file(srv);
		if (!Files.exists(file)) {
			return;
		}
		try {
			fromJson(srv, Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			FossilFoolMod.LOG.error("Could not read {}; starting without rigs and tanks (the file is kept as .broken)", file, e);
			Rigs.reset();
			Machines.reset();
			Machines.start(srv);
			Pockets.reset();
			try {
				Files.copy(file, file.resolveSibling("fossilfool.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
		}
	}

	/** Firebox and hold contents change through screens without telling us, so machines get saved every time. */
	static void saveIfDirty() {
		if (dirty || !Rigs.BY_ID.isEmpty() || !Machines.REFINERIES.isEmpty()) {
			save();
		}
	}

	static void save() {
		if (server == null) {
			return;
		}
		Path file = file(server);
		try {
			Path tmp = file.resolveSibling("fossilfool.json.tmp");
			Files.writeString(tmp, toJson(server), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			FossilFoolMod.LOG.error("Could not save {}", file, e);
		}
	}

	static void shutdown() {
		save();
		server = null;
	}

	// ---------------------------------------------------------------- to JSON

	private static JsonArray items(DynamicOps<JsonElement> ops, Container box) {
		JsonArray list = new JsonArray();
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack stack = box.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			int slot = i;
			ItemStack.CODEC.encodeStart(ops, stack).result().ifPresentOrElse(json -> {
				JsonObject o = new JsonObject();
				o.addProperty("slot", slot);
				o.add("item", json);
				list.add(o);
			}, () -> FossilFoolMod.LOG.warn("Could not save {}", stack));
		}
		return list;
	}

	private static void readItems(DynamicOps<JsonElement> ops, @Nullable JsonElement json, Container box) {
		if (json == null) {
			return;
		}
		for (JsonElement e : json.getAsJsonArray()) {
			JsonObject o = e.getAsJsonObject();
			int slot = o.get("slot").getAsInt();
			if (slot < 0 || slot >= box.getContainerSize()) {
				continue;
			}
			ItemStack.CODEC.parse(ops, o.get("item")).result().ifPresentOrElse(stack -> box.setItem(slot, stack),
				() -> FossilFoolMod.LOG.warn("Dropped an item that no longer exists: {}", o.get("item")));
		}
	}

	private static void pos(JsonObject o, BlockPos pos) {
		o.addProperty("x", pos.getX());
		o.addProperty("y", pos.getY());
		o.addProperty("z", pos.getZ());
	}

	private static BlockPos pos(JsonObject o) {
		return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) ? o.get(key).getAsString() : fallback;
	}

	private static int num(JsonObject o, String key, int fallback) {
		return o.has(key) ? o.get(key).getAsInt() : fallback;
	}

	private static double dbl(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsDouble() : 0;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		return o.has(key) ? o.get(key).getAsBoolean() : fallback;
	}

	static String toJson(MinecraftServer server) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = new JsonObject();

		JsonArray rigs = new JsonArray();
		for (Rig rig : Rigs.BY_ID.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", rig.id);
			o.addProperty("owner", rig.owner);
			o.addProperty("owner_name", rig.ownerName);
			o.addProperty("dimension", rig.dimension);
			pos(o, rig.center());
			o.addProperty("layer", rig.layer);
			o.addProperty("cell", rig.cell);
			o.addProperty("on", rig.on);
			o.addProperty("keep_stone", rig.keepStone);
			o.addProperty("bedrock", rig.bedrock);
			o.addProperty("done", rig.state == Rig.State.DONE);
			o.addProperty("energy", rig.energy);
			o.addProperty("burning", rig.burning == null ? "" : rig.burning.name());
			o.addProperty("crude", rig.crude);
			if (rig.struck != null) {
				o.addProperty("struck", rig.struck);
			}
			o.add("firebox", items(ops, rig.firebox));
			o.add("ores", items(ops, rig.ores));
			o.add("stone", items(ops, rig.stone));
			rigs.add(o);
		}
		root.add("rigs", rigs);

		JsonArray tanks = new JsonArray();
		for (Tank t : Machines.TANKS.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("dimension", t.dimension);
			pos(o, t.pos);
			o.addProperty("fluid", t.fluid.name());
			o.addProperty("amount", t.amount);
			tanks.add(o);
		}
		root.add("tanks", tanks);

		JsonArray refineries = new JsonArray();
		for (Refinery r : Machines.REFINERIES.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("dimension", r.dimension);
			pos(o, r.pos);
			o.addProperty("owner", r.owner);
			o.addProperty("on", r.on);
			o.addProperty("crude", r.crude);
			o.addProperty("diesel", r.diesel);
			o.addProperty("energy", r.energy);
			o.addProperty("burning", r.burning == null ? "" : r.burning.name());
			o.addProperty("progress", r.progress);
			o.add("firebox", items(ops, r.firebox));
			refineries.add(o);
		}
		root.add("refineries", refineries);

		JsonArray pockets = new JsonArray();
		for (Map.Entry<Long, Pockets.Pocket> e : Pockets.OPENED.entrySet()) {
			Pockets.Pocket p = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("key", p.key());
			pos(o, p.center());
			o.addProperty("rx", p.rx());
			o.addProperty("ry", p.ry());
			o.addProperty("rz", p.rz());
			o.addProperty("gusher", p.gusher());
			JsonArray oil = new JsonArray();
			Set<Long> set = Pockets.OIL.get(p.key());
			if (set != null) {
				set.forEach(oil::add);
			}
			o.add("oil", oil);
			pockets.add(o);
		}
		root.add("pockets", pockets);
		return GSON.toJson(root);
	}

	static void fromJson(MinecraftServer server, String json) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (root.has("rigs")) {
			for (JsonElement e : root.getAsJsonArray("rigs")) {
				JsonObject o = e.getAsJsonObject();
				BlockPos c = pos(o);
				Rig rig = new Rig(o.get("id").getAsString(), o.get("dimension").getAsString(), c.getX(), c.getY(), c.getZ());
				rig.owner = str(o, "owner", "");
				rig.ownerName = str(o, "owner_name", "");
				rig.layer = num(o, "layer", c.getY());
				rig.cell = num(o, "cell", 0);
				rig.on = bool(o, "on", true);
				rig.keepStone = bool(o, "keep_stone", true);
				rig.bedrock = bool(o, "bedrock", false);
				rig.state = bool(o, "done", false) ? Rig.State.DONE : Rig.State.DRILLING;
				rig.energy = dbl(o, "energy");
				rig.burning = Fuel.byName(str(o, "burning", ""));
				rig.crude = num(o, "crude", 0);
				rig.struck = o.has("struck") ? o.get("struck").getAsLong() : null;
				readItems(ops, o.get("firebox"), rig.firebox);
				readItems(ops, o.get("ores"), rig.ores);
				readItems(ops, o.get("stone"), rig.stone);
				Rigs.BY_ID.put(rig.id, rig);
			}
		}
		if (root.has("tanks")) {
			for (JsonElement e : root.getAsJsonArray("tanks")) {
				JsonObject o = e.getAsJsonObject();
				Tank t = new Tank(o.get("dimension").getAsString(), pos(o));
				t.fluid = Fluid.byName(str(o, "fluid", ""));
				t.amount = num(o, "amount", 0);
				if (t.amount <= 0) {
					t.fluid = Fluid.NONE;
				}
				Machines.TANKS.put(Machines.key(t.dimension, t.pos), t);
			}
		}
		if (root.has("refineries")) {
			for (JsonElement e : root.getAsJsonArray("refineries")) {
				JsonObject o = e.getAsJsonObject();
				Refinery r = new Refinery(o.get("dimension").getAsString(), pos(o));
				r.owner = str(o, "owner", "");
				r.on = bool(o, "on", true);
				r.crude = num(o, "crude", 0);
				r.diesel = num(o, "diesel", 0);
				r.energy = dbl(o, "energy");
				r.burning = Fuel.byName(str(o, "burning", ""));
				r.progress = dbl(o, "progress");
				readItems(ops, o.get("firebox"), r.firebox);
				Machines.REFINERIES.put(Machines.key(r.dimension, r.pos), r);
			}
		}
		if (root.has("pockets")) {
			for (JsonElement e : root.getAsJsonArray("pockets")) {
				JsonObject o = e.getAsJsonObject();
				BlockPos c = pos(o);
				Pockets.Pocket p = new Pockets.Pocket(o.get("key").getAsLong(), c.getX(), c.getY(), c.getZ(),
					num(o, "rx", 2), num(o, "ry", 1), num(o, "rz", 2), bool(o, "gusher", false));
				Set<Long> oil = new LinkedHashSet<>();
				if (o.has("oil")) {
					for (JsonElement l : o.getAsJsonArray("oil")) {
						oil.add(l.getAsLong());
					}
				}
				Pockets.restore(p, oil);
			}
		}
		dirty = false;
	}
}
