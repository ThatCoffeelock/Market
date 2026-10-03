package com.thatcoffeelock.apocalypse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hordes: zombies that move as a herd. Each horde has a leader; the rest follow it. An idle horde drifts toward
 * the nearest survivor it can smell, goes to investigate noise, and picks up stragglers on the way. When one
 * member spots you, the whole horde knows where you are.
 */
final class Hordes {
	static final RandomSource RANDOM = RandomSource.create();

	static final class Horde {
		final int id;
		final ServerLevel level;
		@Nullable UUID leader;
		final Set<UUID> members = new LinkedHashSet<>();
		/** Where it's heading: last place it saw someone, or a noise. Null = follow its nose. */
		@Nullable Vec3 goal;
		int goalUntil;
		int lastPath = -100;
		boolean gone;
		/** The light it's going to smash, since when, and the ones it couldn't get to. */
		@Nullable BlockPos light;
		int lightSince;
		final Set<Long> ignoredLights = new HashSet<>();
		/** Pyramids it's building against walls. */
		final List<Pyramids.Pile> piles = new ArrayList<>();
		/** Where each member stood a second ago, to tell who's stuck at a door. */
		final Map<UUID, Vec3> lastPos = new HashMap<>();

		Horde(int id, ServerLevel level) {
			this.id = id;
			this.level = level;
		}

		int size() {
			return members.size();
		}

		@Nullable Mob leaderMob() {
			return leader != null && level.getEntity(leader) instanceof Mob m && m.isAlive() ? m : null;
		}
	}

	private static final List<Horde> HORDES = new ArrayList<>();
	private static final Map<UUID, Horde> BY_MEMBER = new HashMap<>();
	/** Seconds until each player's next horde. */
	private static final Map<UUID, Integer> NEXT_HORDE = new HashMap<>();
	private static int nextId = 1;

	private Hordes() {
	}

	static void forget() {
		HORDES.clear();
		BY_MEMBER.clear();
		NEXT_HORDE.clear();
	}

	static List<Horde> all() {
		return HORDES;
	}

	static @Nullable Horde of(Entity entity) {
		return BY_MEMBER.get(entity.getUUID());
	}

	/** Day 1, 2, 3... of the apocalypse, counted in time survived (sleeping doesn't skip it). */
	static long day(ServerLevel level) {
		return level.getGameTime() / 24000L + 1;
	}

	// ---------------------------------------------------------------- membership

	static Horde create(ServerLevel level) {
		Horde h = new Horde(nextId++, level);
		HORDES.add(h);
		return h;
	}

	static void join(Horde h, Mob mob) {
		Horde old = BY_MEMBER.get(mob.getUUID());
		if (old == h) {
			return;
		}
		if (old != null) {
			leave(mob.getUUID());
		}
		h.members.add(mob.getUUID());
		BY_MEMBER.put(mob.getUUID(), h);
		if (h.leader == null) {
			h.leader = mob.getUUID();
		}
		mob.setAttached(ApocalypseMod.HORDE, true);
		Undead.boost(mob, HordeNight.active());
	}

	static void leave(UUID id) {
		Horde h = BY_MEMBER.remove(id);
		if (h == null) {
			return;
		}
		h.members.remove(id);
		h.lastPos.remove(id);
		if (id.equals(h.leader)) {
			h.leader = h.members.isEmpty() ? null : h.members.iterator().next();
		}
		if (h.members.isEmpty()) {
			h.gone = true;
			HORDES.remove(h);
		}
	}

	/** A horde zombie came back from an unloaded chunk or a restart: it finds its herd again, or starts one. */
	static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Mob mob) || !Undead.isZombie(mob) || !mob.hasAttached(ApocalypseMod.HORDE) || BY_MEMBER.containsKey(mob.getUUID())) {
			return;
		}
		Horde near = nearest(level, mob.position(), ApocalypseConfig.get().joinRange * 2.0, null);
		join(near != null ? near : create(level), mob);
	}

	static void onUnload(Entity entity) {
		if (BY_MEMBER.containsKey(entity.getUUID())) {
			leave(entity.getUUID());
		}
	}

	private static @Nullable Horde nearest(ServerLevel level, Vec3 pos, double range, @Nullable Horde except) {
		Horde best = null;
		double bestD = range * range;
		for (Horde h : HORDES) {
			if (h == except || h.level != level || h.size() >= ApocalypseConfig.get().maxHordeSize) {
				continue;
			}
			Mob leader = h.leaderMob();
			if (leader == null) {
				continue;
			}
			double d = leader.position().distanceToSqr(pos);
			if (d <= bestD) {
				bestD = d;
				best = h;
			}
		}
		return best;
	}

	/** Re-applies (or drops) the Horde Night speed boost on everyone. */
	static void reboost(boolean hordeNight) {
		for (Horde h : HORDES) {
			for (UUID id : h.members) {
				if (h.level.getEntity(id) instanceof Mob mob) {
					Undead.boost(mob, hordeNight);
				}
			}
		}
	}

	// ---------------------------------------------------------------- thinking (once a second)

	static void tick(ServerLevel level, int now) {
		for (Horde h : new ArrayList<>(HORDES)) {
			if (h.level == level && !h.gone) {
				think(h, now);
			}
		}
	}

	private static void think(Horde h, int now) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		ServerLevel level = h.level;
		List<Mob> mobs = new ArrayList<>();
		for (UUID id : new ArrayList<>(h.members)) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				mobs.add(m);
			} else {
				leave(id);
			}
		}
		if (h.gone || mobs.isEmpty()) {
			return;
		}
		Mob leader = h.leaderMob();
		if (leader == null) {
			leader = mobs.get(0);
			h.leader = leader.getUUID();
		}

		// One of them saw you. Now they all did.
		LivingEntity target = null;
		for (Mob m : mobs) {
			LivingEntity t = m.getTarget();
			if (t != null && t.isAlive() && (target == null || (t instanceof Player && !(target instanceof Player)))) {
				target = t;
			}
		}
		if (target != null) {
			double share = (double) cfg.shareAggroRange * cfg.shareAggroRange;
			for (Mob m : mobs) {
				LivingEntity t = m.getTarget();
				boolean hasPlayer = t instanceof Player && t.isAlive();
				if (t != target && !hasPlayer && m.distanceToSqr(target) <= share) {
					m.setTarget(target);
				}
			}
			h.goal = target.position();
			h.goalUntil = now + 20; // if they lose you, they search where they saw you last
			if (cfg.breakDoors) {
				Doors.chew(h, mobs, target);
			}
			Lights.onTheWay(h, mobs, target, now);
			Pyramids.climb(h, mobs, target, now);
			remember(h, mobs);
			return;
		}
		if (!h.piles.isEmpty()) {
			Pyramids.disbandAll(h); // lost them: climb down
		}
		remember(h, mobs);

		// Nobody in sight and nothing to investigate: put out the lights, so more of us can rise.
		boolean investigating = h.goal != null && now < h.goalUntil;
		if (!investigating && Lights.seek(h, mobs, leader, now)) {
			recruit(h, leader);
			merge(h, leader);
			return;
		}

		// Then: go where the noise was, or follow the scent of the living.
		Vec3 dest = null;
		if (h.goal != null && now < h.goalUntil) {
			dest = h.goal;
		} else {
			h.goal = null;
			Player p = nearestSurvivor(level, leader.position(), cfg.scentRange);
			if (p != null) {
				dest = p.position().add(RANDOM.nextInt(13) - 6, 0, RANDOM.nextInt(13) - 6);
			}
		}
		if (dest != null && (now - h.lastPath >= 5 || leader.getNavigation().isDone())) {
			Undead.walkTo(leader, dest.x, dest.y, dest.z, 1.0);
			h.lastPath = now;
		}

		// The herd follows the leader.
		for (Mob m : mobs) {
			if (m == leader || Undead.busy(m)) {
				continue;
			}
			double d = m.distanceToSqr(leader);
			if (d > 160 * 160) {
				leave(m.getUUID()); // wandered off for good
				continue;
			}
			if (d > 5 * 5 && (m.getNavigation().isDone() || (now + m.getId()) % 3 == 0)) {
				Undead.walkTo(m, leader.getX() + RANDOM.nextInt(5) - 2, leader.getY(), leader.getZ() + RANDOM.nextInt(5) - 2, d > 20 * 20 ? 1.3 : 1.1);
			}
		}

		recruit(h, leader);
		merge(h, leader);
	}

	private static void remember(Horde h, List<Mob> mobs) {
		for (Mob m : mobs) {
			h.lastPos.put(m.getUUID(), m.position());
		}
	}

	/** Loose zombies near the leader fall in line. */
	private static void recruit(Horde h, Mob leader) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		if (h.size() >= cfg.maxHordeSize) {
			return;
		}
		AABB box = leader.getBoundingBox().inflate(cfg.joinRange);
		for (Mob m : h.level.getEntitiesOfClass(Mob.class, box, m -> Undead.isZombie(m) && !BY_MEMBER.containsKey(m.getUUID()))) {
			if (h.size() >= cfg.maxHordeSize) {
				break;
			}
			join(h, m);
		}
	}

	/** Two hordes that bump into each other become one bigger problem. */
	private static void merge(Horde h, Mob leader) {
		if (h.gone) {
			return;
		}
		Horde other = nearest(h.level, leader.position(), 8, h);
		if (other == null || other.gone || h.size() + other.size() > ApocalypseConfig.get().maxHordeSize) {
			return;
		}
		Horde big = h.size() >= other.size() ? h : other;
		Horde small = big == h ? other : h;
		for (UUID id : new ArrayList<>(small.members)) {
			if (big.level.getEntity(id) instanceof Mob m) {
				join(big, m);
			} else {
				leave(id);
			}
		}
	}

	/** The closest player who isn't in creative or spectator. */
	static @Nullable ServerPlayer nearestSurvivor(ServerLevel level, Vec3 pos, double range) {
		ServerPlayer best = null;
		double bestD = range * range;
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (p.level() != level || !p.isAlive() || p.isSpectator() || p.isCreative()) {
				continue;
			}
			double d = p.position().distanceToSqr(pos);
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- noise

	/** Something loud happened here. Hordes without a target come to look, loose zombies too. */
	static int hear(ServerLevel level, Vec3 pos, double radius, int seconds, int now) {
		double r2 = radius * radius;
		int heard = 0;
		for (Horde h : HORDES) {
			Mob leader = h.leaderMob();
			if (h.level != level || leader == null || leader.position().distanceToSqr(pos) > r2 || Undead.busy(leader)) {
				continue;
			}
			h.goal = pos;
			h.goalUntil = now + seconds;
			h.lastPath = -100; // re-path right away
			heard += h.size();
		}
		AABB box = new AABB(pos, pos).inflate(radius);
		for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> Undead.isZombie(m) && !BY_MEMBER.containsKey(m.getUUID()) && !Undead.busy(m))) {
			if (m.position().distanceToSqr(pos) <= r2) {
				Undead.walkTo(m, pos.x, pos.y, pos.z, 1.0);
				heard++;
			}
		}
		return heard;
	}

	// ---------------------------------------------------------------- spawning

	static void spawnTick(MinecraftServer server) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		if (!cfg.hordes) {
			return;
		}
		boolean hordeNight = HordeNight.active();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
				continue;
			}
			ServerLevel level = (ServerLevel) p.level();
			if (level.dimension() != Level.OVERWORLD || level.getDifficulty() == Difficulty.PEACEFUL) {
				continue;
			}
			boolean night = !level.isBrightOutside();
			int interval = night ? cfg.nightHordeSeconds : cfg.dayHordeSeconds;
			if (hordeNight) {
				interval = Math.max(5, (int) (cfg.nightHordeSeconds / cfg.hordeNightFrequency));
			}
			if (interval <= 0) {
				continue;
			}
			int left = NEXT_HORDE.getOrDefault(p.getUUID(), interval / 2 + RANDOM.nextInt(interval / 2 + 1)) - 1;
			if (left > 0) {
				NEXT_HORDE.put(p.getUUID(), Math.min(left, interval));
				continue;
			}
			NEXT_HORDE.put(p.getUUID(), (int) (interval * (0.75 + RANDOM.nextDouble() * 0.5)));
			int cap = hordeNight ? cfg.hordeNightMaxZombies : cfg.maxZombiesNearPlayer;
			int near = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), Undead::isZombie).size();
			int size = Math.min(size(level, hordeNight), cap - near);
			if (size < 2) {
				continue;
			}
			boolean daylight = !night && !hordeNight;
			BlockPos spot = spotNear(level, p, daylight ? EntityTypes.HUSK : EntityTypes.ZOMBIE);
			if (spot != null) {
				Horde h = spawn(level, spot, size, daylight, true);
				if (h != null) {
					ApocalypseMod.LOG.debug("A horde of {} shambles toward {} (day {})", h.size(), p.getName().getString(), day(level));
				}
			}
		}
	}

	static int size(ServerLevel level, boolean hordeNight) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		int base = cfg.hordeSizeMin + RANDOM.nextInt(cfg.hordeSizeMax - cfg.hordeSizeMin + 1);
		int grown = (int) Math.min(cfg.hordeSizeCap, base + Math.floor((day(level) - 1) * cfg.hordeGrowthPerDay));
		return hordeNight ? (int) Math.round(grown * cfg.hordeNightSize) : grown;
	}

	/**
	 * Somewhere on the ground, out of arm's reach but within earshot, at about the player's height.
	 * With a type, only where vanilla would spawn one: dark, so never in your lit-up base.
	 */
	static @Nullable BlockPos spotNear(ServerLevel level, Entity around, @Nullable EntityType<? extends Mob> rulesFor) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		for (int i = 0; i < 16; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double dist = cfg.spawnDistanceMin + RANDOM.nextDouble() * (cfg.spawnDistanceMax - cfg.spawnDistanceMin);
			int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
			int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
			BlockPos pos = ground(level, x, z, around.getBlockY());
			if (pos != null && Math.abs(pos.getY() - around.getBlockY()) <= 16 && (rulesFor == null || Undead.allowedAt(level, rulesFor, pos))) {
				return pos;
			}
		}
		return null;
	}

	/** The surface at (x, z), if it's loaded, dry, solid and has room for a zombie. */
	static @Nullable BlockPos ground(ServerLevel level, int x, int z, int nearY) {
		BlockPos probe = new BlockPos(x, nearY, z);
		if (!level.isLoaded(probe)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, y, z);
		BlockState floor = level.getBlockState(pos.below());
		if (floor.isAir() || !floor.getFluidState().isEmpty()) {
			return null;
		}
		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
			return null;
		}
		return pos;
	}

	/**
	 * Spawns a horde around a spot. In daylight only husks come: they don't burn. Natural hordes follow
	 * vanilla's spawn rules zombie by zombie, so none of them appear in the light; admin hordes skip that.
	 */
	static @Nullable Horde spawn(ServerLevel level, BlockPos center, int size, boolean daylight, boolean natural) {
		Horde h = create(level);
		for (int i = 0; i < size; i++) {
			BlockPos at = center;
			if (i > 0) {
				BlockPos near = ground(level, center.getX() + RANDOM.nextInt(7) - 3, center.getZ() + RANDOM.nextInt(7) - 3, center.getY());
				if (near != null && Math.abs(near.getY() - center.getY()) <= 3) {
					at = near;
				}
			}
			EntityType<? extends Mob> type = pick(daylight);
			if (natural && !Undead.allowedAt(level, type, at)) {
				continue;
			}
			Entity e = Undead.spawn(level, type, at, RANDOM.nextFloat() * 360f);
			if (e instanceof Mob mob) {
				join(h, mob);
			}
		}
		if (h.members.isEmpty()) {
			h.gone = true;
			HORDES.remove(h);
			return null;
		}
		Cmd.sound(level, "minecraft:entity.zombie.ambient", center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, 2.0f, 0.6f);
		return h;
	}

	private static EntityType<? extends Mob> pick(boolean daylight) {
		if (daylight) {
			return EntityTypes.HUSK;
		}
		int roll = RANDOM.nextInt(100);
		if (roll < 78) {
			return EntityTypes.ZOMBIE;
		}
		if (roll < 90) {
			return EntityTypes.ZOMBIE_VILLAGER;
		}
		return EntityTypes.HUSK;
	}

	/** For /apocalypse: how many hordes and zombies are within range. */
	static int[] census(ServerLevel level, Vec3 pos, double range) {
		int hordes = 0;
		int zombies = 0;
		for (Horde h : HORDES) {
			Mob leader = h.leaderMob();
			if (h.level == level && leader != null && leader.position().distanceToSqr(pos) <= range * range) {
				hordes++;
				zombies += h.size();
			}
		}
		return new int[] {hordes, zombies};
	}

	/** Kills every horde (admin "clear"). */
	static int clear() {
		int n = 0;
		for (Horde h : new ArrayList<>(HORDES)) {
			for (UUID id : new ArrayList<>(h.members)) {
				Entity e = h.level.getEntity(id);
				if (e != null) {
					e.discard();
					n++;
				}
				leave(id);
			}
		}
		return n;
	}
}
