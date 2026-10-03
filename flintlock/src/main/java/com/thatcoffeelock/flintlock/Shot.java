package com.thatcoffeelock.flintlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One ball in flight. There's no entity: it's a point we move ourselves every tick with a swept collision check (so a
 * ball doing 6 blocks a tick can't skip through a wall or a zombie), drawn with a few particles. It stops at the first
 * block or mob it touches and never breaks anything.
 * <p>
 * Hits are collected during the tick and dealt at the end of it, one lump per target: a blunderbuss puts eight
 * pellets into the same zombie at once, and vanilla would ignore seven of them as "already hurt this tick".
 */
final class Shot {
	/** Collision is checked at points this far apart along the path. */
	private static final double STEP = 0.2;
	/** Particles along the trail are about this far apart. */
	private static final double TRAIL_EVERY = 1.5;

	private static final List<Shot> FLYING = new ArrayList<>();
	private static final Map<LivingEntity, Impact> PENDING = new LinkedHashMap<>();
	/** The last hits dealt, for the smoke test. */
	static final List<Impact> RECENT = new ArrayList<>();

	final ServerLevel level;
	final Gun gun;
	Vec3 pos;
	Vec3 vel;
	private final @Nullable UUID shooter;
	/** The shooter and whatever they're riding: the ball starts inside them, so it ignores them for its first ticks. */
	private final Set<UUID> ignore;
	private double travelled;
	private int age;
	private boolean done;

	private Shot(ServerLevel level, Gun gun, Vec3 pos, Vec3 vel, @Nullable Entity shooter) {
		this.level = level;
		this.gun = gun;
		this.pos = pos;
		this.vel = vel;
		this.shooter = shooter == null ? null : shooter.getUUID();
		this.ignore = new HashSet<>();
		for (Entity e = shooter; e != null; e = e.getVehicle()) {
			ignore.add(e.getUUID());
		}
	}

	/** Damage and knockback dealt to one target in one tick. */
	static final class Impact {
		final LivingEntity target;
		final @Nullable UUID shooter;
		final Gun gun;
		float damage;
		Vec3 push = Vec3.ZERO;
		int balls;
		/** Whether the damage landed (not blocked, not invulnerable). Set once it's dealt. */
		boolean landed;
		/** The target's velocity right after the shove. Set once it's dealt. */
		Vec3 velocityAfter = Vec3.ZERO;

		Impact(LivingEntity target, @Nullable UUID shooter, Gun gun) {
			this.target = target;
			this.shooter = shooter;
			this.gun = gun;
		}
	}

	/**
	 * Fires one shot of this gun from {@code from} towards {@code dir}: one ball, or a spray of pellets.
	 * {@code spread} false aims every ball dead straight (for tests).
	 */
	static List<Shot> fire(ServerLevel level, Gun gun, Vec3 from, Vec3 dir, @Nullable Entity shooter, boolean spread) {
		RandomSource random = level.getRandom();
		Vec3 forward = dir.normalize();
		List<Shot> shots = new ArrayList<>();
		for (int i = 0; i < gun.pellets; i++) {
			Vec3 d = spread ? scatter(forward, gun.spreadDegrees, random) : forward;
			Shot shot = new Shot(level, gun, from, d.scale(gun.speed), shooter);
			FLYING.add(shot);
			shots.add(shot);
		}
		return shots;
	}

	/** Tilts a direction by a random angle (normally distributed, {@code degrees} standard deviation). */
	static Vec3 scatter(Vec3 dir, double degrees, RandomSource random) {
		if (degrees <= 0) {
			return dir;
		}
		Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 right = dir.cross(up).normalize();
		Vec3 down = dir.cross(right).normalize();
		double rad = Math.toRadians(degrees);
		double a = random.nextGaussian() * rad;
		double b = random.nextGaussian() * rad;
		return dir.add(right.scale(Math.tan(a))).add(down.scale(Math.tan(b))).normalize();
	}

	static List<Shot> flying() {
		return new ArrayList<>(FLYING);
	}

	static void tickAll() {
		for (Shot shot : flying()) {
			try {
				shot.tick();
			} catch (RuntimeException e) {
				FlintlockMod.LOG.error("A {} ball crashed mid-air; removing it", shot.gun.id, e);
				shot.remove();
			}
		}
		dealHits();
	}

	static void clear() {
		FLYING.clear();
		PENDING.clear();
		RECENT.clear();
	}

	private void remove() {
		done = true;
		FLYING.remove(this);
	}

	private enum Kind { BLOCK, ENTITY, WATER, OUT }

	private record Hit(Kind kind, Vec3 at, @Nullable LivingEntity entity) {
	}

	private void tick() {
		if (done) {
			remove();
			return;
		}
		age++;
		Vec3 to = pos.add(vel);
		Hit hit = trace(pos, to);
		if (hit != null) {
			travelled += hit.at.distanceTo(pos);
			switch (hit.kind) {
				case ENTITY -> hitEntity(hit.entity, hit.at);
				case BLOCK -> {
					Cmd.particles(level, "minecraft:crit", hit.at.x, hit.at.y, hit.at.z, 0.05, 0.1, 4);
					Cmd.particles(level, "minecraft:smoke", hit.at.x, hit.at.y, hit.at.z, 0.05, 0.01, 2);
				}
				case WATER -> Cmd.particles(level, "minecraft:splash", hit.at.x, hit.at.y + 0.1, hit.at.z, 0.1, 0.1, 6);
				case OUT -> {
				}
			}
			remove();
			return;
		}
		trail(pos, to);
		travelled += vel.length();
		pos = to;
		vel = vel.scale(gun.drag).add(0, -gun.gravity, 0);
		if (age >= gun.maxAge) {
			remove();
		}
	}

	private void trail(Vec3 from, Vec3 to) {
		if (gun.pellets > 1) {
			return; // eight trails at once is just noise
		}
		Vec3 path = to.subtract(from);
		int n = Math.max(1, (int) (path.length() / TRAIL_EVERY));
		for (int i = 0; i < n; i++) {
			Vec3 p = from.add(path.scale((i + 0.5) / n));
			Cmd.particles(level, "minecraft:crit", p.x, p.y, p.z, 0, 0, 1);
		}
	}

	/** The first thing between from and to, or null if the way is clear. */
	private @Nullable Hit trace(Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		double entityDist = Double.MAX_VALUE;
		Vec3 entityHit = null;
		LivingEntity hitEntity = null;
		AABB sweep = new AABB(from, to).inflate(1.0);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !e.isSpectator())) {
			if (age <= 2 && ignore.contains(entity.getUUID())) {
				continue; // don't shoot yourself (or your horse) on the way out of the barrel
			}
			AABB box = entity.getBoundingBox().inflate(0.1);
			// point-blank: the muzzle is already inside them, and clip() only finds a way in from outside
			Optional<Vec3> clip = box.contains(from) ? Optional.of(from) : box.clip(from, to);
			if (clip.isPresent() && clip.get().distanceTo(from) < entityDist) {
				entityDist = clip.get().distanceTo(from);
				entityHit = clip.get();
				hitEntity = entity;
			}
		}

		int steps = Math.max(1, (int) Math.ceil(length / STEP));
		Vec3 last = from;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			if (entityHit != null && t * length >= entityDist) {
				return new Hit(Kind.ENTITY, entityHit, hitEntity);
			}
			Vec3 p = from.add(path.scale(t));
			BlockPos bp = BlockPos.containing(p);
			if (level.isOutsideBuildHeight(bp)) {
				if (bp.getY() < level.getSeaLevel()) {
					return new Hit(Kind.OUT, p, null); // into the void
				}
				last = p;
				continue; // above the build limit: nothing up there to hit
			}
			if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(bp.getX()), SectionPos.blockToSectionCoord(bp.getZ()))) {
				return new Hit(Kind.OUT, p, null); // don't load chunks just for a stray ball
			}
			BlockState state = level.getBlockState(bp);
			if (!state.getCollisionShape(level, bp).isEmpty()) {
				return new Hit(Kind.BLOCK, last, null);
			}
			if (!state.getFluidState().isEmpty()) {
				return new Hit(Kind.WATER, p, null);
			}
			last = p;
		}
		return entityHit != null ? new Hit(Kind.ENTITY, entityHit, hitEntity) : null;
	}

	private void hitEntity(LivingEntity target, Vec3 at) {
		double keep = gun.falloff(travelled);
		Impact impact = PENDING.computeIfAbsent(target, t -> new Impact(t, shooter, gun));
		impact.damage += (float) (gun.damage * keep);
		Vec3 flat = new Vec3(vel.x, 0, vel.z);
		Vec3 away = flat.lengthSqr() < 1.0e-6 ? Vec3.ZERO : flat.normalize();
		impact.push = impact.push.add(away.scale(gun.knockback * keep)).add(0, gun.lift * keep, 0);
		impact.balls++;
		Cmd.particles(level, "minecraft:damage_indicator", at.x, at.y, at.z, 0.1, 0.1, 2);
	}

	/** Deals this tick's hits: damage first, then the extra shove if the damage landed. */
	private static void dealHits() {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Impact> impacts = new ArrayList<>(PENDING.values());
		PENDING.clear();
		for (Impact impact : impacts) {
			LivingEntity target = impact.target;
			if (target.isRemoved() || !target.isAlive() || !(target.level() instanceof ServerLevel level)) {
				continue;
			}
			UUID by = impact.shooter != null && level.getServer().getPlayerList().getPlayer(impact.shooter) != null ? impact.shooter : null;
			// a gun is slow enough that it shouldn't be swallowed by the half second of invulnerability after a sword hit
			target.setInvulnerableTime(0);
			Cmd.damage(level, target.getUUID(), impact.damage, by);
			impact.landed = target.getInvulnerableTime() > 0;
			if (impact.landed && !(target instanceof Player player && player.isCreative())) {
				shove(target, impact.push);
			}
			impact.velocityAfter = target.getDeltaMovement();
			RECENT.add(impact);
			if (RECENT.size() > 32) {
				RECENT.remove(0);
			}
		}
	}

	/** Knocks the target along, softened by knockback resistance (so a ravager barely budges). */
	private static void shove(LivingEntity target, Vec3 push) {
		double resist = Math.max(0, Math.min(1, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
		Vec3 flat = new Vec3(push.x, 0, push.z);
		if (flat.length() > Gun.MAX_KNOCKBACK) {
			flat = flat.normalize().scale(Gun.MAX_KNOCKBACK);
		}
		double up = Math.min(Gun.MAX_LIFT, push.y);
		Vec3 add = flat.add(0, up, 0).scale(1 - resist);
		if (add.lengthSqr() < 1.0e-6) {
			return;
		}
		// the hit just marked the target as hurt, which sends players their new velocity at the end of the tick, shove included
		target.setDeltaMovement(target.getDeltaMovement().add(add));
	}
}
