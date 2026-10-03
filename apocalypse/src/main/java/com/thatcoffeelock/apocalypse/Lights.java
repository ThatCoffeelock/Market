package com.thatcoffeelock.apocalypse;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The dead hate the light. Light is what keeps them from rising near you, so they put it out: an idle horde goes
 * looking for torches, lanterns and lamps nearby and smashes them, and any zombie that passes a light it can reach
 * takes a swing. "Can reach" matters: from its feet to just above its head, and it has to be able to walk there.
 * Torches on top of a wall, lights inside a sealed base and lights built into the floor are safe.
 */
final class Lights {
	/** Glowing things that aren't really "your lights": natural, dangerous, or not worth wrecking someone's day over. */
	private static final String[] LEAVE_ALONE = {"lava", "magma", "portal", "end_gateway", "_ore", "lichen", "sea_pickle", "amethyst",
		"cave_vines", "sculk", "firefly", "dragon_egg", "respawn_anchor", "crying_obsidian", "creaking"};

	/** Loose zombies (not in a horde) and the light each one is after. */
	private static final Map<UUID, BlockPos> LOOSE = new HashMap<>();
	private static final Map<UUID, Integer> LOOSE_SINCE = new HashMap<>();

	private Lights() {
	}

	static void forget() {
		LOOSE.clear();
		LOOSE_SINCE.clear();
	}

	/** A light a zombie would smash. Nothing with an inventory or block entity (furnaces, campfires, beacons). */
	static boolean isLight(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.getLightEmission() <= 0 || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null
			|| state.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.equals("fire") || path.equals("soul_fire") || path.equals("light")) {
			return false;
		}
		if (path.startsWith("redstone_") && !path.equals("redstone_lamp")) {
			return false; // leave people's contraptions alone
		}
		for (String s : LEAVE_ALONE) {
			if (path.contains(s)) {
				return false;
			}
		}
		return true;
	}

	/** Zombie-seconds to smash it: a torch goes in two, a lantern takes a while longer. */
	static int breakSeconds(ServerLevel level, BlockPos pos, BlockState state) {
		return Math.min(20, 2 + (int) Math.ceil(Math.max(0, state.getDestroySpeed(level, pos)) * 2));
	}

	/** Within arm's reach: from its feet to just above its head, and right next to it. */
	static boolean reaches(Mob m, BlockPos pos) {
		int dy = pos.getY() - m.getBlockY();
		if (dy < 0 || dy > 2) {
			return false;
		}
		double dx = pos.getX() + 0.5 - m.getX();
		double dz = pos.getZ() + 0.5 - m.getZ();
		return dx * dx + dz * dz <= 1.9 * 1.9;
	}

	/** The closest light around a spot, at about head height, that hasn't been given up on. */
	static @Nullable BlockPos nearest(ServerLevel level, BlockPos from, int range, Set<Long> ignored) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > range * range || d2 >= bestD) {
					continue;
				}
				for (int dy = 0; dy <= 2; dy++) {
					p.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
					if (!level.isLoaded(p) || ignored.contains(p.asLong())) {
						continue;
					}
					BlockState state = level.getBlockState(p);
					if (isLight(level, p, state)) {
						best = p.immutable();
						bestD = d2;
						break;
					}
				}
			}
		}
		return best;
	}

	private static @Nullable BlockPos withinReach(ServerLevel level, Mob m) {
		BlockPos feet = m.blockPosition();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					p.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
					if (reaches(m, p) && isLight(level, p, level.getBlockState(p))) {
						return p.immutable();
					}
				}
			}
		}
		return null;
	}

	/**
	 * An idle horde (nobody in sight) goes after the nearest light. Returns true while it's busy with one,
	 * so it doesn't wander off following its nose at the same time.
	 */
	static boolean seek(Hordes.Horde h, List<Mob> mobs, Mob leader, int now) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		if (!cfg.smashLights) {
			return false;
		}
		ServerLevel level = h.level;
		if (h.light != null) {
			if (!isLight(level, h.light, level.getBlockState(h.light))) {
				h.light = null; // someone else got it, or the player took it down
			} else if (now - h.lightSince > cfg.lightGiveUpSeconds) {
				h.ignoredLights.add(h.light.asLong()); // can't get to it. Next.
				h.light = null;
			}
		}
		if (h.light == null && (now + h.id) % 3 == 0) {
			if (h.ignoredLights.size() > 64) {
				h.ignoredLights.clear();
			}
			h.light = nearest(level, leader.blockPosition(), cfg.lightSearchRange, h.ignoredLights);
			h.lightSince = now;
		}
		BlockPos light = h.light;
		if (light == null) {
			return false;
		}
		int smashing = 0;
		Mob breaker = null;
		Vec3 c = Vec3.atBottomCenterOf(light);
		for (Mob m : mobs) {
			if (Undead.busy(m)) {
				continue;
			}
			if (reaches(m, light)) {
				smashing++;
				breaker = m;
			} else if (m.getNavigation().isDone() || (now + m.getId()) % 3 == 0) {
				Undead.walkNear(m, c.x, c.y, c.z, 1.1);
			}
		}
		if (breaker != null) {
			h.lightSince = now; // making progress, don't give up
			BlockState state = level.getBlockState(light);
			if (Doors.chewOnce(level, light, smashing, breaker.getId(), now, breakSeconds(level, light, state), false)) {
				h.light = null;
			}
		}
		return true;
	}

	/** Chasing someone: any light within arm's reach on the way gets a swing too. */
	static void onTheWay(Hordes.Horde h, List<Mob> mobs, LivingEntity target, int now) {
		if (!ApocalypseConfig.get().smashLights) {
			return;
		}
		ServerLevel level = h.level;
		Map<Long, Integer> smashing = new HashMap<>();
		Map<Long, Integer> breakers = new HashMap<>();
		for (Mob m : mobs) {
			if (m.distanceToSqr(target) < 3 * 3) {
				continue; // busy biting
			}
			BlockPos light = withinReach(level, m);
			if (light != null) {
				smashing.merge(light.asLong(), 1, Integer::sum);
				breakers.putIfAbsent(light.asLong(), m.getId());
			}
		}
		for (Map.Entry<Long, Integer> e : smashing.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			Doors.chewOnce(level, pos, e.getValue(), breakers.get(e.getKey()), now, breakSeconds(level, pos, level.getBlockState(pos)), false);
		}
	}

	/** Once a second: zombies that aren't in a horde do it too, on a smaller scale. */
	static void tickLoose(MinecraftServer server, int now) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		if (!cfg.smashLights) {
			return;
		}
		Set<Mob> loose = new LinkedHashSet<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) {
				continue;
			}
			ServerLevel level = (ServerLevel) p.level();
			loose.addAll(level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48),
				m -> Undead.isZombie(m) && Hordes.of(m) == null && !Undead.busy(m)));
		}
		LOOSE.keySet().removeIf(id -> loose.stream().noneMatch(m -> m.getUUID().equals(id)));
		LOOSE_SINCE.keySet().retainAll(LOOSE.keySet());
		for (Mob m : loose) {
			ServerLevel level = (ServerLevel) m.level();
			UUID id = m.getUUID();
			BlockPos light = LOOSE.get(id);
			if (light != null && (!isLight(level, light, level.getBlockState(light)) || now - LOOSE_SINCE.getOrDefault(id, now) > cfg.lightGiveUpSeconds)) {
				LOOSE.remove(id);
				light = null;
			}
			if (light == null && (now + m.getId()) % 4 == 0) {
				light = nearest(level, m.blockPosition(), Math.min(8, cfg.lightSearchRange), Set.of());
				if (light != null) {
					LOOSE.put(id, light);
					LOOSE_SINCE.put(id, now);
				}
			}
			if (light == null) {
				continue;
			}
			if (reaches(m, light)) {
				LOOSE_SINCE.put(id, now);
				if (Doors.chewOnce(level, light, 1, m.getId(), now, breakSeconds(level, light, level.getBlockState(light)), false)) {
					LOOSE.remove(id);
				}
			} else if (m.getNavigation().isDone() || (now + m.getId()) % 3 == 0) {
				Vec3 c = Vec3.atBottomCenterOf(light);
				Undead.walkNear(m, c.x, c.y, c.z, 1.0);
			}
		}
	}
}
