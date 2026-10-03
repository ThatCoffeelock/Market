package com.thatcoffeelock.warehouse;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** config/warehouse.json. Written with defaults on first launch. */
final class WarehouseConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static WarehouseConfig instance = new WarehouseConfig();

	/** Items the Warehouse Core holds on its own, without any racks. */
	int coreCapacity = 2048;
	/** Items each connected Storage Rack adds. A double chest holds 3,456. */
	int rackCapacity = 4096;
	/** Racks one warehouse can use. Racks beyond this still stand there, they just don't count. */
	int maxRacks = 64;
	/** Racks further than this (in blocks, along any axis) from their core don't count. */
	int rackReach = 32;
	/** A Loading Dock serves every warehouse whose core is within this many blocks. */
	int dockReach = 48;
	/** Ships within this many blocks of a Loading Dock can unload and load there. */
	int shipReach = 16;

	static WarehouseConfig get() {
		return instance;
	}

	static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("warehouse.json");
		WarehouseConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, WarehouseConfig.class);
			} catch (Exception e) {
				WarehouseMod.LOG.error("Could not read config/warehouse.json, using the defaults", e);
			}
		}
		instance = loaded == null ? new WarehouseConfig() : loaded;
		instance.coreCapacity = Math.max(0, instance.coreCapacity);
		instance.rackCapacity = Math.max(1, instance.rackCapacity);
		instance.maxRacks = Math.max(0, instance.maxRacks);
		instance.rackReach = Math.max(1, instance.rackReach);
		instance.dockReach = Math.max(1, instance.dockReach);
		instance.shipReach = Math.max(1, instance.shipReach);
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(instance, writer);
		} catch (Exception e) {
			WarehouseMod.LOG.error("Could not write config/warehouse.json", e);
		}
	}
}
