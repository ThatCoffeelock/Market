package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Optional link to the Warehouse mod, through Fabric's ObjectShare ({@code warehouse:api}): neither mod needs the other
 * to compile or run. With Warehouse installed, every storehouse is a real warehouse (a core and its racks) and the
 * harbor's pier has a working Loading Dock. Without it, storehouses keep their own 27 or 54 slots.
 */
final class WarehouseLink {
	private static boolean warned;

	private WarehouseLink() {
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Object call(String op, Object... args) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("warehouse:api");
		if (!(hook instanceof BiFunction<?, ?, ?> api)) {
			return null;
		}
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i + 1 < args.length; i += 2) {
			map.put((String) args[i], args[i + 1]);
		}
		try {
			return ((BiFunction<String, Map<String, Object>, Object>) api).apply(op, map);
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				ColonycraftMod.LOG.warn("The Warehouse link failed; carrying on without warehouses", e);
			}
			return null;
		}
	}

	static boolean present() {
		return FabricLoader.getInstance().getObjectShare().get("warehouse:api") instanceof BiFunction<?, ?, ?>;
	}

	/** Registers the core standing at pos as a (locked) warehouse. Returns its id, or null without Warehouse. */
	static @Nullable String create(ServerLevel level, BlockPos pos, UUID owner, String ownerName, String name) {
		return call("create", "level", level, "pos", pos, "owner", owner.toString(), "owner_name", ownerName, "name", name, "locked", true)
			instanceof String id && !id.isEmpty() ? id : null;
	}

	static void rack(ServerLevel level, BlockPos pos) {
		call("rack", "level", level, "pos", pos);
	}

	static void dock(ServerLevel level, BlockPos pos) {
		call("dock", "level", level, "pos", pos);
	}

	static void undock(ServerLevel level, BlockPos pos) {
		call("undock", "level", level, "pos", pos);
	}

	static boolean exists(@Nullable String id) {
		return id != null && !id.isEmpty() && Boolean.TRUE.equals(call("exists", "id", id));
	}

	/** Puts as much of the stack in as fits (shrinking it). */
	static void deposit(String id, ItemStack stack) {
		call("deposit", "id", id, "stack", stack);
	}

	static long count(String id, ItemStack kind) {
		return call("count", "id", id, "stack", kind) instanceof Number n ? n.longValue() : 0;
	}

	static long take(String id, ItemStack kind, long amount) {
		return call("take", "id", id, "stack", kind, "amount", amount) instanceof Number n ? n.longValue() : 0;
	}

	/** One of each kind it holds, with how many. */
	@SuppressWarnings("unchecked")
	static List<Map.Entry<ItemStack, Long>> stock(String id) {
		return call("stock", "id", id) instanceof List<?> list ? (List<Map.Entry<ItemStack, Long>>) list : List.of();
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> info(String id) {
		return call("info", "id", id) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	static boolean open(ServerPlayer player, String id) {
		return Boolean.TRUE.equals(call("open", "player", player, "id", id));
	}

	/** Packs it up: the core item with the stock inside. Empty if there's nothing to pack. */
	static ItemStack pack(String id) {
		return call("pack", "id", id) instanceof ItemStack stack ? stack : ItemStack.EMPTY;
	}
}
