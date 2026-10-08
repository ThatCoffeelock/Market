package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Where every tank and refinery stands, and the "pipes" between them: a machine reaches every Tank within
 * {@code pipeReach} blocks without any pipe at all (it's assumed; it's old-timey), plus every tank on a pipeline it
 * touches (see {@link Pipes}). Rigs fill tanks with crude; refineries drink crude from them and fill them with
 * diesel; and both burn diesel, crude or lava straight from the tanks when their firebox runs dry.
 */
final class Machines {
	static final Map<String, Tank> TANKS = new HashMap<>();
	static final Map<String, Refinery> REFINERIES = new HashMap<>();
	static final Map<String, Oven> OVENS = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private Machines() {
	}

	static void start(MinecraftServer srv) {
		server = srv;
	}

	static void reset() {
		TANKS.clear();
		REFINERIES.clear();
		OVENS.clear();
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

	static @Nullable Oven ovenAt(Level level, BlockPos pos) {
		return OVENS.get(key(dim(level), pos));
	}

	static Oven addOven(Level level, BlockPos pos) {
		Oven o = new Oven(dim(level), pos.immutable());
		OVENS.put(key(o.dimension, o.pos), o);
		Pipes.changed();
		Store.changed();
		return o;
	}

	static void removeOven(Oven o) {
		OVENS.remove(key(o.dimension, o.pos));
		Labels.remove(o.dimension, o.pos);
		Pipes.changed();
		Store.changed();
	}

	/** Every tank an oven reaches: within reach, and along its pipeline. */
	static List<Tank> tanksFor(ServerLevel level, Oven o) {
		return withPipeline(tanksNear(level, o.pos, 0), o.pipeline(level));
	}

	/** An oven drinks diesel from the tanks it reaches until its own tank is full. */
	static void pipeIn(ServerLevel level, Oven o) {
		for (Tank t : tanksFor(level, o)) {
			int room = Oven.capacity() - o.diesel;
			if (room <= 0) {
				return;
			}
			int n = t.drain(Fluid.DIESEL, room);
			if (n > 0) {
				o.diesel += n;
			}
		}
	}

	static Tank addTank(Level level, BlockPos pos) {
		Tank tank = new Tank(dim(level), pos.immutable());
		TANKS.put(key(tank.dimension, tank.pos), tank);
		Pipes.changed();
		Store.changed();
		return tank;
	}

	static Refinery addRefinery(Level level, BlockPos pos) {
		Refinery r = new Refinery(dim(level), pos.immutable());
		REFINERIES.put(key(r.dimension, r.pos), r);
		Pipes.changed();
		Store.changed();
		return r;
	}

	static void removeTank(Tank tank) {
		TANKS.remove(key(tank.dimension, tank.pos));
		Labels.remove(tank.dimension, tank.pos);
		Pipes.changed();
		Store.changed();
	}

	static void removeRefinery(Refinery r) {
		REFINERIES.remove(key(r.dimension, r.pos));
		Labels.remove(r.dimension, r.pos);
		Pipes.changed();
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

	/** Tanks nearby first, then the ones further along the pipeline. */
	private static List<Tank> withPipeline(List<Tank> near, Pipes.Network net) {
		for (Tank t : net.tanks()) {
			if (!near.contains(t)) {
				near.add(t);
			}
		}
		return near;
	}

	/** Every tank a rig reaches: within reach of the derrick's edge, and along its pipeline. */
	static List<Tank> tanksFor(ServerLevel level, Rig rig) {
		return withPipeline(tanksNear(level, rig.center(), 3), rig.pipeline(level));
	}

	/** Every tank a refinery reaches: within reach, and along its pipeline. */
	static List<Tank> tanksFor(ServerLevel level, Refinery r) {
		return withPipeline(tanksNear(level, r.pos, 0), r.pipeline(level));
	}

	/** The six spots touching a block, where a pipe would connect to it. */
	static List<BlockPos> around(BlockPos pos) {
		List<BlockPos> list = new ArrayList<>(6);
		for (Direction d : Direction.values()) {
			list.add(pos.relative(d));
		}
		return list;
	}

	/**
	 * The fuel line: one bucket's worth of fuel out of these tanks, the best fuel first (diesel, crude, then lava).
	 * Null if the config says no, or none of them holds anything that burns.
	 */
	static @Nullable Fuel.Burn tankFuel(List<Tank> tanks) {
		if (!FossilConfig.get().fuelFromTanks || tanks.isEmpty()) {
			return null;
		}
		for (Fluid kind : new Fluid[] {Fluid.DIESEL, Fluid.CRUDE, Fluid.LAVA}) {
			for (Tank t : tanks) {
				if (t.drain(kind, 1) == 1) {
					Fuel fuel = kind.fuel();
					return new Fuel.Burn(fuel, fuel.blocks(), ItemStack.EMPTY);
				}
			}
		}
		return null;
	}

	/** A rig empties its crude into the tanks it reaches. Its pipes reach from the edge of the derrick. */
	static void pipeOut(ServerLevel level, Rig rig) {
		for (Tank t : tanksFor(level, rig)) {
			if (rig.crude <= 0) {
				return;
			}
			rig.crude -= t.fill(Fluid.CRUDE, rig.crude);
		}
	}

	/** A refinery pipes its diesel into the tanks it reaches. */
	static void pipeOut(ServerLevel level, Refinery r) {
		for (Tank t : tanksFor(level, r)) {
			if (r.diesel <= 0) {
				return;
			}
			r.diesel -= t.fill(Fluid.DIESEL, r.diesel);
		}
	}

	/** A refinery drinks crude from the tanks it reaches until it's full. */
	static void pipeIn(ServerLevel level, Refinery r) {
		for (Tank t : tanksFor(level, r)) {
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
		for (Oven o : new ArrayList<>(OVENS.values())) {
			ServerLevel level = level(o.dimension);
			if (level == null || !level.isLoaded(o.pos)) {
				continue;
			}
			try {
				o.tick(level);
			} catch (RuntimeException e) {
				FossilFoolMod.LOG.error("Industrial Oven at {} crashed while ticking", o.pos, e);
			}
		}
		if (ticks % 10 == 0) {
			Labels.draw();
			for (Tank t : TANKS.values()) {
				ServerLevel level = level(t.dimension);
				if (level != null && level.isLoaded(t.pos)) {
					Interactions.showFluid(level, t);
				}
			}
		}
		if (ticks % 200 == 0) {
			validate();
		}
	}

	/** Forgets tanks, refineries and pipes whose block is gone (blown up, or replaced by a command). */
	static void validate() {
		for (Map.Entry<String, java.util.Set<Long>> e : Pipes.BY_DIM.entrySet()) {
			ServerLevel level = level(e.getKey());
			if (level == null) {
				continue;
			}
			List<Long> gone = new ArrayList<>();
			for (long l : e.getValue()) {
				BlockPos pos = BlockPos.of(l);
				if (level.isLoaded(pos) && !Pipes.isPipeBlock(level.getBlockState(pos))) {
					gone.add(l);
				}
			}
			for (long l : gone) {
				Pipes.remove(e.getKey(), BlockPos.of(l));
			}
		}
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
		for (Oven o : new ArrayList<>(OVENS.values())) {
			ServerLevel level = level(o.dimension);
			if (level != null && level.isLoaded(o.pos) && !Interactions.isOvenBlock(level.getBlockState(o.pos))) {
				FossilFoolMod.LOG.info("Industrial Oven at {} is gone", o.pos);
				removeOven(o);
			}
		}
	}
}
