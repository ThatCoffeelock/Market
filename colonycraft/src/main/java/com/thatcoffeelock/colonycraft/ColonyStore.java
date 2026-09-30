package com.thatcoffeelock.colonycraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/** Saves and loads every colony to <world>/colonycraft.json. */
final class ColonyStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ColonyStore() {
	}

	static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("colonycraft.json");
	}

	private static DynamicOps<JsonElement> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static String toJson(MinecraftServer server, List<Colony> colonies, long clock) {
		DynamicOps<JsonElement> ops = ops(server);
		JsonObject root = new JsonObject();
		root.addProperty("clock", clock);
		JsonArray list = new JsonArray();
		for (Colony c : colonies) {
			JsonObject o = new JsonObject();
			o.addProperty("id", c.id);
			o.addProperty("name", c.name);
			o.addProperty("owner", c.owner.toString());
			o.addProperty("owner_name", c.ownerName);
			o.addProperty("dimension", c.dimension);
			o.addProperty("striking", c.striking);
			JsonArray buildings = new JsonArray();
			for (Colony.Building b : c.buildings) {
				JsonObject bo = new JsonObject();
				bo.addProperty("id", b.id);
				bo.addProperty("type", b.type.id);
				bo.addProperty("tier", b.tier);
				bo.addProperty("x", b.origin.getX());
				bo.addProperty("y", b.origin.getY());
				bo.addProperty("z", b.origin.getZ());
				bo.addProperty("quarter", b.quarter);
				bo.addProperty("spent", b.spent);
				bo.addProperty("autosell", b.autosell);
				JsonArray villagers = new JsonArray();
				for (UUID v : b.villagers) {
					villagers.add(v == null ? "" : v.toString());
				}
				bo.add("villagers", villagers);
				JsonArray items = new JsonArray();
				for (int i = 0; i < b.storage.getContainerSize(); i++) {
					ItemStack stack = b.storage.getItem(i);
					if (stack.isEmpty()) {
						continue;
					}
					int slot = i;
					ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(json -> {
						JsonObject entry = new JsonObject();
						entry.addProperty("slot", slot);
						entry.add("item", json);
						items.add(entry);
					});
				}
				bo.add("storage", items);
				buildings.add(bo);
			}
			o.add("buildings", buildings);
			list.add(o);
		}
		root.add("colonies", list);
		return GSON.toJson(root);
	}

	static long clock;

	static List<Colony> fromJson(MinecraftServer server, String json) {
		DynamicOps<JsonElement> ops = ops(server);
		List<Colony> colonies = new ArrayList<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		clock = root.has("clock") ? root.get("clock").getAsLong() : 0;
		for (JsonElement e : root.getAsJsonArray("colonies")) {
			JsonObject o = e.getAsJsonObject();
			Colony c = new Colony(o.get("id").getAsString(), o.get("name").getAsString(), UUID.fromString(o.get("owner").getAsString()),
				o.get("owner_name").getAsString(), o.get("dimension").getAsString());
			c.striking = o.has("striking") && o.get("striking").getAsBoolean();
			for (JsonElement be : o.getAsJsonArray("buildings")) {
				JsonObject bo = be.getAsJsonObject();
				BuildingType type = BuildingType.byId(bo.get("type").getAsString());
				if (type == null) {
					continue;
				}
				Colony.Building b = new Colony.Building(bo.get("id").getAsString(), type,
					new BlockPos(bo.get("x").getAsInt(), bo.get("y").getAsInt(), bo.get("z").getAsInt()), bo.get("quarter").getAsInt());
				b.tier = bo.get("tier").getAsInt();
				b.spent = bo.get("spent").getAsLong();
				b.autosell = bo.has("autosell") && bo.get("autosell").getAsBoolean();
				for (JsonElement v : bo.getAsJsonArray("villagers")) {
					String s = v.getAsString();
					b.villagers.add(s.isEmpty() ? null : UUID.fromString(s));
				}
				b.resizeStorage();
				for (JsonElement ie : bo.getAsJsonArray("storage")) {
					JsonObject entry = ie.getAsJsonObject();
					int slot = entry.get("slot").getAsInt();
					ItemStack.CODEC.parse(ops, entry.get("item")).result().ifPresent(stack -> {
						if (slot >= 0 && slot < b.storage.getContainerSize()) {
							b.storage.setItem(slot, stack);
						}
					});
				}
				b.colony = c;
				c.buildings.add(b);
			}
			colonies.add(c);
		}
		return colonies;
	}

	static List<Colony> load(MinecraftServer server) {
		Path file = file(server);
		if (!Files.exists(file)) {
			clock = 0;
			return new ArrayList<>();
		}
		try {
			return fromJson(server, Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			ColonycraftMod.LOG.error("Could not read {}; starting without colonies (the file is kept as .broken)", file, e);
			try {
				Files.copy(file, file.resolveSibling("colonycraft.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
			return new ArrayList<>();
		}
	}

	static void save(MinecraftServer server, List<Colony> colonies, long clock) {
		Path file = file(server);
		try {
			Path tmp = file.resolveSibling("colonycraft.json.tmp");
			Files.writeString(tmp, toJson(server, colonies, clock), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			ColonycraftMod.LOG.error("Could not save {}", file, e);
		}
	}
}
