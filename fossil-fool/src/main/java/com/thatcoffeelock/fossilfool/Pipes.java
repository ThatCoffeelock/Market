package com.thatcoffeelock.fossilfool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Real pipes, for when "everything within 6 blocks" isn't far enough. A Pipe is a copper lightning rod with our tag
 * on the item; where it was placed is remembered (like a tank), so a plain lightning rod on your roof stays a
 * lightning rod. Pipes that touch, touch: a line of them is a pipeline, up to {@code pipeLength} pipes long.
 *
 * A pipeline links whatever it touches: a Drill Rig (a pipe in the ring around its shaft, where hoppers go), Tanks,
 * Refineries, Industrial Ovens, and chests, barrels and shulker boxes. Rigs and refineries treat every tank on their pipeline as if it
 * stood next to them, rigs push their ore and stone holds into the chests on it, and a Warehouse near the far end of
 * a pipeline counts as near the rig.
 *
 * The pipes themselves are just remembered positions, so a pipeline reaches tanks in chunks nobody's in.
 */
final class Pipes {
	/** Dimension -> every pipe in it, as {@link BlockPos#asLong()}. */
	static final Map<String, Set<Long>> BY_DIM = new HashMap<>();
	/** Goes up whenever a pipe, tank or refinery comes or goes, so cached pipelines get traced again. */
	private static int version;

	private Pipes() {
	}

	static void reset() {
		BY_DIM.clear();
		version++;
	}

	static void changed() {
		version++;
	}

	static boolean isPipeBlock(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		// copper weathers and gets waxed: exposed_lightning_rod, waxed_oxidized_lightning_rod, ... are all still pipe
		return id.endsWith("lightning_rod") || id.equals("end_rod");
	}

	static boolean has(Level level, BlockPos pos) {
		Set<Long> set = BY_DIM.get(Machines.dim(level));
		return set != null && set.contains(pos.asLong());
	}

	static void add(Level level, BlockPos pos) {
		BY_DIM.computeIfAbsent(Machines.dim(level), d -> new HashSet<>()).add(pos.asLong());
		version++;
		Store.changed();
	}

	static void remove(String dim, BlockPos pos) {
		Set<Long> set = BY_DIM.get(dim);
		if (set != null && set.remove(pos.asLong())) {
			version++;
			Store.changed();
		}
	}

	static int count() {
		int n = 0;
		for (Set<Long> set : BY_DIM.values()) {
			n += set.size();
		}
		return n;
	}

	/** Chests, barrels and shulker boxes. Not hoppers, furnaces or our own machines. */
	static boolean isStorage(ServerLevel level, BlockPos pos) {
		String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
		return (id.endsWith("chest") || id.endsWith("barrel") || id.endsWith("shulker_box")) && level.getBlockEntity(pos) instanceof Container;
	}

	/** What one pipeline links: its tanks, its Industrial Ovens, its storage blocks, and its loose ends. */
	record Network(int pipes, List<Tank> tanks, List<Oven> ovens, List<BlockPos> storage, List<BlockPos> ends) {
		static final Network NONE = new Network(0, List.of(), List.of(), List.of(), List.of());
	}

	/**
	 * Follows the pipes that start at any of these spots. Tanks are found even in unloaded chunks (they're only
	 * data); storage blocks only where the chunk is loaded. Nearest (along the pipe) first.
	 */
	static Network trace(ServerLevel level, Collection<BlockPos> from) {
		String dim = Machines.dim(level);
		Set<Long> all = BY_DIM.get(dim);
		if (all == null || all.isEmpty()) {
			return Network.NONE;
		}
		ArrayDeque<Long> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		for (BlockPos pos : from) {
			long l = pos.asLong();
			if (all.contains(l) && seen.add(l)) {
				queue.add(l);
			}
		}
		if (queue.isEmpty()) {
			return Network.NONE;
		}
		int max = FossilConfig.get().pipeLength;
		List<Tank> tanks = new ArrayList<>();
		List<Oven> ovens = new ArrayList<>();
		List<BlockPos> storage = new ArrayList<>();
		List<BlockPos> ends = new ArrayList<>();
		Set<Long> found = new HashSet<>();
		while (!queue.isEmpty()) {
			BlockPos pos = BlockPos.of(queue.poll());
			int links = 0;
			for (Direction d : Direction.values()) {
				BlockPos next = pos.relative(d);
				long l = next.asLong();
				if (all.contains(l)) {
					links++;
					if (seen.size() < max && seen.add(l)) {
						queue.add(l);
					}
					continue;
				}
				Tank tank = Machines.TANKS.get(Machines.key(dim, next));
				Oven oven = tank == null ? Machines.OVENS.get(Machines.key(dim, next)) : null;
				if (tank != null) {
					if (found.add(l)) {
						tanks.add(tank);
					}
				} else if (oven != null) {
					if (found.add(l)) {
						ovens.add(oven);
					}
				} else if (level.isLoaded(next) && isStorage(level, next) && found.add(l)) {
					storage.add(next.immutable());
				}
			}
			if (links <= 1) {
				ends.add(pos);
			}
		}
		return new Network(seen.size(), tanks, ovens, storage, ends);
	}

	/** A machine's view of its pipeline, traced again when pipes change and every few seconds (chests come and go). */
	static final class Link {
		private Network net = Network.NONE;
		private int traced = -1;
		private long at;

		Network get(ServerLevel level, Supplier<Collection<BlockPos>> from) {
			long now = level.getGameTime();
			if (traced != version || now - at >= 100 || now < at) {
				traced = version;
				at = now;
				net = BY_DIM.isEmpty() ? Network.NONE : trace(level, from.get());
			}
			return net;
		}
	}
}
