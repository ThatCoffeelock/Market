package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/**
 * For other mods (the Warehouse uses it): somewhere for a Drill Rig to unload. A rig's ore and stone holds are offered
 * to every registered unloader, every few seconds and when the owner clicks "Unload". Fossil Fool doesn't know or
 * care who's on the other end.
 */
public final class FossilFoolApi {
	/** Takes what it wants out of a rig's holds. */
	@FunctionalInterface
	public interface Unloader {
		/**
		 * @param rig   the middle of the rig's shaft, at ground level
		 * @param holds the ore hold first, then the stone hold; take items out of them directly
		 * @return how many items were taken
		 */
		long unload(ServerLevel level, BlockPos rig, List<Container> holds);
	}

	private static final List<Unloader> UNLOADERS = new ArrayList<>();

	private FossilFoolApi() {
	}

	public static void registerUnloader(Unloader unloader) {
		UNLOADERS.add(unloader);
	}

	/** How far from a rig an unloader should look, in blocks (config "warehouseReach"). */
	public static int reach() {
		return FossilConfig.get().warehouseReach;
	}

	// ---------------------------------------------------------------- fossilfool:api (no compile dependency needed)

	static final String KEY = "fossilfool:api";

	/**
	 * Publishes {@code fossilfool:api} in Fabric's ObjectShare: a {@code BiFunction<String, Map<String, Object>, Object>}
	 * for mods that don't compile against Fossil Fool (Colonycraft's Fuel Depot). Operations, all taking "level" and
	 * "pos" (or "positions"):
	 * <ul>
	 * <li>{@code tank} (set: "CRUDE", "DIESEL", "WATER", "LAVA" or absent): the cauldron at pos is a Tank.</li>
	 * <li>{@code refinery} (owner: a UUID string, optional): the blast furnace at pos is a Refinery.</li>
	 * <li>{@code pipe}: the lightning rod at pos is a Pipe.</li>
	 * <li>{@code remove}: forgets the tank, refinery or pipe at pos (whatever was in it is gone).</li>
	 * <li>{@code buckets} (positions): buckets in the tanks at these positions, all together.</li>
	 * <li>{@code info}: what's at pos, as a map ("kind": tank/refinery/pipe, and for a tank "set", "fluid", "amount"), or null.</li>
	 * </ul>
	 * Machines that already exist are kept as they are, with what's in them.
	 */
	static void publish() {
		net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().putIfAbsent(KEY,
			(java.util.function.BiFunction<String, java.util.Map<String, Object>, Object>) FossilFoolApi::call);
	}

	static @org.jetbrains.annotations.Nullable Object call(String op, java.util.Map<String, Object> args) {
		if (!(args.get("level") instanceof ServerLevel level)) {
			return null;
		}
		if (op.equals("buckets")) {
			long n = 0;
			if (args.get("positions") instanceof java.util.Collection<?> list) {
				for (Object o : list) {
					Tank t = o instanceof BlockPos p ? Machines.tankAt(level, p) : null;
					n += t == null ? 0 : t.amount;
				}
			}
			return n;
		}
		if (!(args.get("pos") instanceof BlockPos p)) {
			return null;
		}
		BlockPos pos = p.immutable();
		switch (op) {
			case "tank" -> {
				Tank t = Machines.tankAt(level, pos);
				if (t == null) {
					t = Machines.addTank(level, pos);
				}
				Fluid set = Fluid.byName(args.get("set") instanceof String s ? s : "");
				if (t.set != set && t.canSet(set)) {
					t.set = set;
					Store.changed();
				}
				return true;
			}
			case "refinery" -> {
				Refinery r = Machines.refineryAt(level, pos);
				if (r == null) {
					r = Machines.addRefinery(level, pos);
					r.owner = args.get("owner") instanceof String s ? s : "";
				}
				return true;
			}
			case "pipe" -> {
				if (!Pipes.has(level, pos)) {
					Pipes.add(level, pos);
				}
				return true;
			}
			case "info" -> {
				Tank t = Machines.tankAt(level, pos);
				if (t != null) {
					return java.util.Map.of("kind", "tank", "set", t.set.name(), "fluid", t.fluid.name(), "amount", t.amount);
				}
				if (Machines.refineryAt(level, pos) != null) {
					return java.util.Map.of("kind", "refinery");
				}
				return Pipes.has(level, pos) ? java.util.Map.of("kind", "pipe") : null;
			}
			case "remove" -> {
				Tank t = Machines.tankAt(level, pos);
				if (t != null) {
					Machines.removeTank(t);
				}
				Refinery r = Machines.refineryAt(level, pos);
				if (r != null) {
					Machines.removeRefinery(r);
				}
				Pipes.remove(Machines.dim(level), pos);
				return true;
			}
			default -> {
				return null;
			}
		}
	}

	static boolean hasUnloaders() {
		return !UNLOADERS.isEmpty();
	}

	/** Offers these holds to every unloader (what a rig does every few seconds). Returns how many items were taken. */
	public static long unload(ServerLevel level, BlockPos rig, List<Container> holds) {
		long moved = 0;
		for (Unloader unloader : UNLOADERS) {
			try {
				moved += unloader.unload(level, rig, holds);
			} catch (RuntimeException e) {
				FossilFoolMod.LOG.warn("A Drill Rig unloader from another mod failed", e);
			}
		}
		return moved;
	}
}
