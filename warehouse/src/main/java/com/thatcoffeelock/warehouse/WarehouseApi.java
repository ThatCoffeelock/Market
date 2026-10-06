package com.thatcoffeelock.warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods (Colonycraft builds warehouses into its storehouses and a Loading Dock into its harbor), through
 * Fabric's ObjectShare, so neither mod needs the other to compile or run.
 *
 * {@code warehouse:api} is a {@code BiFunction<String, Map<String, Object>, Object>}: an operation and its arguments.
 * <ul>
 * <li>{@code create} level, pos, owner (UUID string), owner_name, name, locked: registers a core that's already standing
 * there and returns its id (or the id of the one that's already there)</li>
 * <li>{@code rack} / {@code dock} / {@code undock} level, pos: remembers a Storage Rack or Loading Dock that's standing there</li>
 * <li>{@code exists} id: is it there (and not packed up)?</li>
 * <li>{@code deposit} id, stack: puts as much in as fits (shrinking the stack), returns how many went in</li>
 * <li>{@code count} id, stack: how many of that kind it holds</li>
 * <li>{@code take} id, stack, amount: takes up to that many out, returns how many</li>
 * <li>{@code stock} id: a list of (one of each kind, count) entries</li>
 * <li>{@code info} id: name, total, capacity, racks</li>
 * <li>{@code open} player, id: opens its screen for the player</li>
 * <li>{@code pack} id: packs it up and returns the core item with the stock inside</li>
 * </ul>
 */
final class WarehouseApi {
	static final String KEY = "warehouse:api";

	private WarehouseApi() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().put(KEY, (BiFunction<String, Map<String, Object>, Object>) WarehouseApi::call);
	}

	static @Nullable Object call(String op, Map<String, Object> args) {
		if (op == null || args == null) {
			return null;
		}
		return switch (op) {
			case "create" -> create(args);
			case "rack" -> {
				if (args.get("level") instanceof ServerLevel level && args.get("pos") instanceof BlockPos pos) {
					Warehouses.addRack(level, pos.immutable());
					Warehouses.refresh();
					yield true;
				}
				yield false;
			}
			case "dock" -> {
				if (args.get("level") instanceof ServerLevel level && args.get("pos") instanceof BlockPos pos) {
					if (!Warehouses.isDock(level, pos)) {
						Warehouses.addDock(level, pos.immutable());
					}
					yield true;
				}
				yield false;
			}
			case "undock" -> {
				if (args.get("level") instanceof ServerLevel level && args.get("pos") instanceof BlockPos pos) {
					Warehouses.removeDock(level, pos);
					yield true;
				}
				yield false;
			}
			case "exists" -> {
				Warehouse w = warehouse(args);
				yield w != null && !w.packed;
			}
			case "deposit" -> {
				Warehouse w = warehouse(args);
				yield w != null && args.get("stack") instanceof ItemStack stack ? w.deposit(stack) : 0;
			}
			case "count" -> {
				Warehouse w = warehouse(args);
				yield w != null && args.get("stack") instanceof ItemStack stack && !stack.isEmpty() ? w.count(Warehouse.Key.of(stack)) : 0L;
			}
			case "take" -> {
				Warehouse w = warehouse(args);
				if (w != null && args.get("stack") instanceof ItemStack stack && !stack.isEmpty() && args.get("amount") instanceof Number n) {
					yield w.take(Warehouse.Key.of(stack), n.longValue());
				}
				yield 0L;
			}
			case "stock" -> {
				Warehouse w = warehouse(args);
				List<Map.Entry<ItemStack, Long>> list = new ArrayList<>();
				if (w != null) {
					for (Map.Entry<Warehouse.Key, Long> e : w.items.entrySet()) {
						list.add(Map.entry(e.getKey().stack.copy(), e.getValue()));
					}
				}
				yield list;
			}
			case "info" -> {
				Warehouse w = warehouse(args);
				if (w == null) {
					yield null;
				}
				Map<String, Object> info = new LinkedHashMap<>();
				info.put("name", w.name);
				info.put("total", w.total());
				info.put("capacity", w.capacity());
				info.put("racks", w.racks);
				info.put("packed", w.packed);
				yield info;
			}
			case "open" -> {
				Warehouse w = warehouse(args);
				if (w != null && !w.packed && args.get("player") instanceof ServerPlayer player) {
					WarehouseMod.nextTick(() -> WarehouseMenu.open(player, w, () -> !w.packed && player.isAlive(), null, new WarehouseMenu.View()));
					yield true;
				}
				yield false;
			}
			case "pack" -> {
				Warehouse w = warehouse(args);
				yield w == null || w.packed ? ItemStack.EMPTY : Warehouses.pack(w);
			}
			default -> null;
		};
	}

	private static @Nullable Warehouse warehouse(Map<String, Object> args) {
		return args.get("id") instanceof String id ? Warehouses.byId(id) : null;
	}

	private static String create(Map<String, Object> args) {
		if (!(args.get("level") instanceof ServerLevel level) || !(args.get("pos") instanceof BlockPos pos)) {
			return "";
		}
		Warehouse there = Warehouses.coreAt(level, pos);
		if (there != null) {
			return there.id;
		}
		String owner = args.get("owner") instanceof String s ? s : "";
		String ownerName = args.get("owner_name") instanceof String s ? s : "";
		String name = args.get("name") instanceof String s && !s.isBlank() ? Warehouses.clean(s) : "Warehouse";
		Warehouse w = Warehouses.create(level, pos, owner, ownerName, name);
		w.locked = Boolean.TRUE.equals(args.get("locked"));
		Warehouses.refresh();
		return w.id;
	}
}
