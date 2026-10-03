package com.thatcoffeelock.apocalypse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Horde zombies stuck in front of a door (or a window) while they can see you start chewing through it.
 * The more of them pile up at the same door, the faster it goes. Iron and copper hold.
 */
final class Doors {
	private static final class Chewing {
		int progress;
		int breaker;
		int lastSecond;
	}

	private static final Map<ServerLevel, Map<Long, Chewing>> CHEWING = new HashMap<>();

	private Doors() {
	}

	static void forget() {
		CHEWING.clear();
	}

	static boolean breakable(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.contains("iron") || path.contains("copper")) {
			return false;
		}
		if (path.endsWith("_door") || path.endsWith("_trapdoor") || path.endsWith("_fence_gate")) {
			return true;
		}
		if (path.equals("glass") || path.endsWith("_glass") || path.endsWith("glass_pane")) {
			return true;
		}
		return ApocalypseConfig.get().breakPlanks && path.endsWith("_planks");
	}

	/** Called once a second for a horde that has a target. */
	static void chew(Hordes.Horde h, List<Mob> mobs, LivingEntity target) {
		ServerLevel level = h.level;
		Map<Long, Integer> chewers = new HashMap<>();
		Map<Long, Integer> breakers = new HashMap<>();
		for (Mob m : mobs) {
			Vec3 last = h.lastPos.get(m.getUUID());
			if (last == null || last.distanceToSqr(m.position()) > 0.4 * 0.4) {
				continue; // still getting somewhere on its own
			}
			double d = m.distanceToSqr(target);
			if (d < 2.5 * 2.5 || d > 24 * 24) {
				continue;
			}
			BlockPos door = blocking(level, m, target);
			if (door != null) {
				chewers.merge(door.asLong(), 1, Integer::sum);
				breakers.putIfAbsent(door.asLong(), m.getId());
			}
		}
		int now = ApocalypseMod.seconds();
		for (Map.Entry<Long, Integer> e : chewers.entrySet()) {
			chewOnce(level, BlockPos.of(e.getKey()), e.getValue(), breakers.get(e.getKey()), now);
		}
	}

	/** The breakable block between a stuck zombie and its target, if there is one. */
	private static @Nullable BlockPos blocking(ServerLevel level, Mob m, LivingEntity target) {
		double dx = target.getX() - m.getX();
		double dz = target.getZ() - m.getZ();
		Direction main = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Direction side = Math.abs(dx) > Math.abs(dz) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
		BlockPos feet = m.blockPosition();
		for (Direction dir : new Direction[] {main, side}) {
			BlockPos ahead = feet.relative(dir);
			for (BlockPos p : new BlockPos[] {ahead, ahead.above()}) {
				if (breakable(level.getBlockState(p))) {
					return p;
				}
			}
		}
		// a trapdoor or a window overhead
		BlockPos up = feet.above(2);
		return breakable(level.getBlockState(up)) ? up : null;
	}

	/** One second of chewing on a door or window. Returns true when it gives way. */
	static boolean chewOnce(ServerLevel level, BlockPos pos, int zombies, int breaker, int now) {
		return chewOnce(level, pos, zombies, breaker, now, ApocalypseConfig.get().breakSeconds, true);
	}

	/**
	 * One second of some zombies working on a block that takes `needed` zombie-seconds to break.
	 * Doors get door sounds; anything else (a lantern, say) just gets smashed. Returns true when it breaks.
	 */
	static boolean chewOnce(ServerLevel level, BlockPos pos, int zombies, int breaker, int now, int needed, boolean door) {
		Map<Long, Chewing> map = CHEWING.computeIfAbsent(level, l -> new HashMap<>());
		Chewing c = map.computeIfAbsent(pos.asLong(), k -> new Chewing());
		c.breaker = breaker;
		c.lastSecond = now;
		c.progress += zombies;
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		if (c.progress >= needed) {
			map.remove(pos.asLong());
			level.destroyBlockProgress(c.breaker, pos, -1);
			level.destroyBlock(pos, true);
			if (door) {
				Cmd.sound(level, "minecraft:entity.zombie.break_wooden_door", x, y, z, 1.0f, 0.9f + Hordes.RANDOM.nextFloat() * 0.2f);
			}
			return true;
		}
		level.destroyBlockProgress(c.breaker, pos, Math.min(9, c.progress * 10 / needed));
		Cmd.sound(level, door ? "minecraft:entity.zombie.attack_wooden_door" : "minecraft:entity.zombie.attack_iron_door", x, y, z,
			door ? 1.0f : 0.5f, 0.8f + Hordes.RANDOM.nextFloat() * 0.4f);
		return false;
	}

	/** Doors nobody's chewed on for a while heal up. */
	static void tick(int now) {
		for (Map.Entry<ServerLevel, Map<Long, Chewing>> lvl : CHEWING.entrySet()) {
			Iterator<Map.Entry<Long, Chewing>> it = lvl.getValue().entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Long, Chewing> e = it.next();
				if (now - e.getValue().lastSecond > 15) {
					lvl.getKey().destroyBlockProgress(e.getValue().breaker, BlockPos.of(e.getKey()), -1);
					it.remove();
				}
			}
		}
	}
}
