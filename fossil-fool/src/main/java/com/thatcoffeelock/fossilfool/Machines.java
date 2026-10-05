package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Where every tank and refinery stands, and the "pipes" between them: a machine reaches every Oil Tank within
 * {@code pipeReach} blocks. Rigs fill tanks with crude; refineries drink crude from them and fill them with diesel.
 * Nobody has to lay actual pipe. It's assumed. It's old-timey.
 */
final class Machines {
	static final Map<String, Tank> TANKS = new HashMap<>();
	static final Map<String, Refinery> REFINERIES = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private Machines() {
	}

	static void start(MinecraftServer srv) {
		server = srv;
	}

	static void reset() {
		TANKS.clear();
		REFINERIES.clear();
		server = null;
	}

	static String dim(Level level) {
		return level.dimension().toString();
	}

	static String key(String dim, BlockPos pos) {
		return dim + "@" + pos.asLong();
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

	static @Nullable Tank tankAt(Level level, BlockPos pos) {
		return TANKS.get(key(dim(level), pos));
	}

	static @Nullable Refinery refineryAt(Level level, BlockPos pos) {
		return REFINERIES.get(key(dim(level), pos));
	}

	static Tank addTank(Level level, BlockPos pos) {
		Tank tank = new Tank(dim(level), pos.immutable());
		TANKS.put(key(tank.dimension, tank.pos), tank);
		Store.changed();
		return tank;
	}

	static Refinery addRefinery(Level level, BlockPos pos) {
		Refinery r = new Refinery(dim(level), pos.immutable());
		REFINERIES.put(key(r.dimension, r.pos), r);
		Store.changed();
		return r;
	}

	static void removeTank(Tank tank) {
		TANKS.remove(key(tank.dimension, tank.pos));
		Labels.remove(tank.dimension, tank.pos);
		Store.changed();
	}

	static void removeRefinery(Refinery r) {
		REFINERIES.remove(key(r.dimension, r.pos));
		Labels.remove(r.dimension, r.pos);
		Store.changed();
	}

	/** Tanks within pipe reach of a spot, nearest first. */
	static List<Tank> tanksNear(Level level, BlockPos pos, int extra) {
		String dim = dim(level);
		int reach = FossilConfig.get().pipeReach + extra;
		List<Tank> list = new ArrayList<>();
		for (Tank t : TANKS.values()) {
			if (t.dimension.equals(dim) && t.pos.distSqr(pos) <= (double) reach * reach) {
				list.add(t);
			}
		}
		list.sort(Comparator.comparingDouble(t -> t.pos.distSqr(pos)));
		return list;
	}

	/** A rig empties its crude into tanks nearby. Its pipes reach from the edge of the derrick. */
	static void pipeOut(ServerLevel level, Rig rig) {
		for (Tank t : tanksNear(level, rig.center(), 3)) {
			if (rig.crude <= 0) {
				return;
			}
			rig.crude -= t.fill(Fluid.CRUDE, rig.crude);
		}
	}

	/** A refinery pipes its diesel into tanks nearby. */
	static void pipeOut(ServerLevel level, Refinery r) {
		for (Tank t : tanksNear(level, r.pos, 0)) {
			if (r.diesel <= 0) {
				return;
			}
			r.diesel -= t.fill(Fluid.DIESEL, r.diesel);
		}
	}

	/** A refinery drinks crude from tanks nearby until it's full. */
	static void pipeIn(ServerLevel level, Refinery r) {
		for (Tank t : tanksNear(level, r.pos, 0)) {
			int room = Refinery.capacity() - r.crude;
			if (room <= 0) {
				return;
			}
			r.crude += t.drain(Fluid.CRUDE, room);
		}
	}

	/** Ticks refineries and redraws labels on the tanks and refineries that are loaded. */
	static void tick(int ticks) {
		for (Refinery r : new ArrayList<>(REFINERIES.values())) {
			ServerLevel level = level(r.dimension);
			if (level == null || !level.isLoaded(r.pos)) {
				continue;
			}
			try {
				r.tick(level);
			} catch (RuntimeException e) {
				FossilFoolMod.LOG.error("Refinery at {} crashed while ticking", r.pos, e);
			}
		}
		if (ticks % 10 == 0) {
			Labels.draw();
		}
		if (ticks % 200 == 0) {
			validate();
		}
	}

	/** Forgets tanks and refineries whose block is gone (blown up, or replaced by a command). */
	private static void validate() {
		for (Tank t : new ArrayList<>(TANKS.values())) {
			ServerLevel level = level(t.dimension);
			if (level != null && level.isLoaded(t.pos) && !Interactions.isTankBlock(level.getBlockState(t.pos))) {
				FossilFoolMod.LOG.info("Oil Tank at {} is gone; forgetting {} buckets of {}", t.pos, t.amount, t.fluid.title);
				removeTank(t);
			}
		}
		for (Refinery r : new ArrayList<>(REFINERIES.values())) {
			ServerLevel level = level(r.dimension);
			if (level != null && level.isLoaded(r.pos) && !Interactions.isRefineryBlock(level.getBlockState(r.pos))) {
				FossilFoolMod.LOG.info("Refinery at {} is gone", r.pos);
				removeRefinery(r);
			}
		}
	}
}
