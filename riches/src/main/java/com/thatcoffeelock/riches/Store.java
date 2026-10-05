package com.thatcoffeelock.riches;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** Saves vaults, doors, display cases, relic finds and trust to {@code <world>/riches.json}. */
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
		return server.getWorldPath(LevelResource.ROOT).resolve("riches.json");
	}

	private static DynamicOps<JsonElement> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static void load(MinecraftServer srv) {
		server = srv;
		Places.reset();
		Places.start(srv);
		Relics.reset();
		dirty = false;
		Path file = file(srv);
		if (!Files.exists(file)) {
			return;
		}
		try {
			fromJson(srv, Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			RichesMod.LOG.error("Could not read {}; starting without vaults and cases (the file is kept as .broken)", file, e);
			Places.reset();
			Relics.reset();
			try {
				Files.copy(file, file.resolveSibling("riches.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
		}
	}

	static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	static void save() {
		if (server == null) {
			return;
		}
		Path file = file(server);
		try {
			Path tmp = file.resolveSibling("riches.json.tmp");
			Files.writeString(tmp, toJson(server), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			RichesMod.LOG.error("Could not save {}", file, e);
		}
	}

	static void shutdown() {
		save();
		server = null;
	}

	private static JsonObject at(String dim, BlockPos pos, String owner, String ownerName) {
		JsonObject o = new JsonObject();
		o.addProperty("dimension", dim);
		o.addProperty("x", pos.getX());
		o.addProperty("y", pos.getY());
		o.addProperty("z", pos.getZ());
		o.addProperty("owner", owner);
		o.addProperty("owner_name", ownerName);
		return o;
	}

	private static BlockPos pos(JsonObject o) {
		return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsString() : "";
	}

	static String toJson(MinecraftServer server) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = new JsonObject();
		JsonArray vaults = new JsonArray();
		for (Places.Vault v : Places.VAULTS.values()) {
			vaults.add(at(v.dimension, v.pos, v.owner, v.ownerName));
		}
		root.add("vaults", vaults);
		JsonArray doors = new JsonArray();
		for (Places.Door d : Places.DOORS.values()) {
			doors.add(at(d.dimension, d.pos, d.owner, d.ownerName));
		}
		root.add("doors", doors);
		JsonArray shows = new JsonArray();
		for (Places.Showcase s : Places.SHOWCASES.values()) {
			JsonObject o = at(s.dimension, s.pos, s.owner, s.ownerName);
			o.addProperty("kind", s.kind.name());
			o.addProperty("shown_by", s.shownBy);
			o.addProperty("shown_on", s.shownOn);
			if (!s.item.isEmpty()) {
				ItemStack.CODEC.encodeStart(ops, s.item).result().ifPresentOrElse(json -> o.add("item", json),
					() -> RichesMod.LOG.warn("Could not save the {} on show at {}", s.item, s.pos));
			}
			shows.add(o);
		}
		root.add("showcases", shows);
		JsonObject found = new JsonObject();
		for (Map.Entry<String, Relics.Found> e : Relics.FOUND.entrySet()) {
			JsonObject f = new JsonObject();
			f.addProperty("uuid", e.getValue().uuid());
			f.addProperty("name", e.getValue().name());
			f.addProperty("date", e.getValue().date());
			found.add(e.getKey(), f);
		}
		root.add("found", found);
		JsonArray rewarded = new JsonArray();
		Relics.REWARDED.forEach(rewarded::add);
		root.add("rewarded", rewarded);
		JsonObject trust = new JsonObject();
		for (Map.Entry<String, Set<String>> e : Places.TRUST.entrySet()) {
			JsonArray list = new JsonArray();
			e.getValue().forEach(list::add);
			trust.add(e.getKey(), list);
		}
		root.add("trust", trust);
		return GSON.toJson(root);
	}

	static void fromJson(MinecraftServer server, String json) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (root.has("vaults")) {
			for (JsonElement e : root.getAsJsonArray("vaults")) {
				JsonObject o = e.getAsJsonObject();
				Places.Vault v = new Places.Vault(str(o, "dimension"), pos(o));
				v.owner = str(o, "owner");
				v.ownerName = str(o, "owner_name");
				Places.VAULTS.put(Places.key(v.dimension, v.pos), v);
			}
		}
		if (root.has("doors")) {
			for (JsonElement e : root.getAsJsonArray("doors")) {
				JsonObject o = e.getAsJsonObject();
				Places.Door d = new Places.Door(str(o, "dimension"), pos(o));
				d.owner = str(o, "owner");
				d.ownerName = str(o, "owner_name");
				Places.DOORS.put(Places.key(d.dimension, d.pos), d);
			}
		}
		if (root.has("showcases")) {
			for (JsonElement e : root.getAsJsonArray("showcases")) {
				JsonObject o = e.getAsJsonObject();
				Places.Kind kind = "PEDESTAL".equals(str(o, "kind")) ? Places.Kind.PEDESTAL : Places.Kind.CASE;
				Places.Showcase s = new Places.Showcase(str(o, "dimension"), pos(o), kind);
				s.owner = str(o, "owner");
				s.ownerName = str(o, "owner_name");
				s.shownBy = str(o, "shown_by");
				s.shownOn = str(o, "shown_on");
				if (o.has("item")) {
					ItemStack.CODEC.parse(ops, o.get("item")).result().ifPresentOrElse(stack -> s.item = stack,
						() -> RichesMod.LOG.warn("Dropped an item on show that no longer exists: {}", o.get("item")));
				}
				Places.SHOWCASES.put(Places.key(s.dimension, s.pos), s);
			}
		}
		if (root.has("found")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("found").entrySet()) {
				JsonObject f = e.getValue().getAsJsonObject();
				Relics.FOUND.put(e.getKey(), new Relics.Found(str(f, "uuid"), str(f, "name"), str(f, "date")));
			}
		}
		if (root.has("rewarded")) {
			for (JsonElement e : root.getAsJsonArray("rewarded")) {
				Relics.REWARDED.add(e.getAsString());
			}
		}
		if (root.has("trust")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("trust").entrySet()) {
				Set<String> set = new HashSet<>();
				for (JsonElement t : e.getValue().getAsJsonArray()) {
					set.add(t.getAsString());
				}
				Places.TRUST.put(e.getKey(), set);
			}
		}
		dirty = false;
	}
}
