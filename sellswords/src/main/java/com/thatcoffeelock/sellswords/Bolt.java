package com.thatcoffeelock.sellswords;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A crossbow bolt or musket ball in flight. Not an entity: a point we move every tick with a swept collision check
 * (like Flintlock's balls), drawn with particles. It flies straight through anything friendly (so a mercenary can
 * shoot past you, your villagers and the rest of the squad) and stops at the first block or foe.
 */
final class Bolt {
	private static final double STEP = 0.2;
	private static final List<Bolt> FLYING = new ArrayList<>();
	/** Bolts and balls that hit something since the server started (for the smoke test). */
	static int hits;

	private final ServerLevel level;
	private final String merc;
	private final UUID shooter;
	private final boolean musket;
	private final float damage;
	private final double gravity;
	private final Set<UUID> pierced = new HashSet<>();
	private int pierceLeft;
	private Vec3 pos;
	private Vec3 vel;
	private int age;
	private double untilTrail;

	private Bolt(ServerLevel level, Merc m, LivingEntity brain, Vec3 pos, Vec3 vel, Rank rank) {
		this.level = level;
		this.merc = m.id;
		this.shooter = brain.getUUID();
		this.musket = rank.musket();
		this.damage = rank.ranged;
		this.gravity = musket ? 0.012 : 0.05;
		this.pierceLeft = rank.pierce();
		this.pos = pos;
		this.vel = vel;
	}

	static double speed(Rank rank) {
		return rank.musket() ? 6.0 : 3.0;
	}

	/** Spread in degrees: steadier hands further up the ranged path. */
	static double spread(Rank rank) {
		return switch (rank) {
			case CROSSBOWMAN -> 2.0;
			case MARKSMAN -> 1.5;
			case SHARPSHOOTER -> 1.0;
			case MUSKETEER -> 0.8;
			case RECRUIT -> 2.5;
			default -> 3.0;
		};
	}

	/** Looses one at the target, aiming a little ahead of where it's going and a little high (bolts drop). */
	static void fire(ServerLevel level, Merc m, LivingEntity brain, LivingEntity target) {
		Rank rank = m.rank();
		Vec3 eye = brain.getEyePosition();
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
		double v = speed(rank);
		double g = rank.musket() ? 0.012 : 0.05;
		double t = aim.distanceTo(eye) / v;
		aim = aim.add(target.getDeltaMovement().multiply(t, 0, t)).add(0, g * t * t / 2, 0);
		Vec3 dir = scatter(aim.subtract(eye).normalize(), spread(rank), level.getRandom());
		Vec3 muzzle = eye.add(dir.scale(0.7));
		FLYING.add(new Bolt(level, m, brain, muzzle, dir.scale(v), rank));
		if (rank.musket()) {
			Cmd.sound(level, "minecraft:entity.generic.explode", muzzle.x, muzzle.y, muzzle.z, 0.9f, 1.7f);
			Cmd.sound(level, "minecraft:entity.firework_rocket.blast", muzzle.x, muzzle.y, muzzle.z, 1.4f, 0.7f);
			Cmd.particles(level, "minecraft:large_smoke", muzzle.x, muzzle.y, muzzle.z, 0.15, 0.02, 6);
			Cmd.particles(level, "minecraft:flame", muzzle.x, muzzle.y, muzzle.z, 0.05, 0.02, 3);
		} else {
			Cmd.sound(level, "minecraft:item.crossbow.shoot", muzzle.x, muzzle.y, muzzle.z, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);
		}
	}

	static Vec3 scatter(Vec3 dir, double degrees, RandomSource random) {
		Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 right = dir.cross(up).normalize();
		Vec3 down = dir.cross(right).normalize();
		double rad = Math.toRadians(degrees);
		return dir.add(right.scale(Math.tan(random.nextGaussian() * rad))).add(down.scale(Math.tan(random.nextGaussian() * rad))).normalize();
	}

	static void tickAll() {
		for (Bolt bolt : new ArrayList<>(FLYING)) {
			try {
				if (bolt.tick()) {
					FLYING.remove(bolt);
				}
			} catch (RuntimeException e) {
				SellswordsMod.LOG.error("A bolt crashed mid-air; removing it", e);
				FLYING.remove(bolt);
			}
		}
	}

	static void clear() {
		FLYING.clear();
	}

	static int flying() {
		return FLYING.size();
	}

	/** Moves the bolt one tick. True when it's done. */
	private boolean tick() {
		age++;
		Vec3 to = pos.add(vel);
		Vec3 path = to.subtract(pos);
		double length = path.length();
		// the first foe the path goes through
		LivingEntity victim = null;
		double victimAt = Double.MAX_VALUE;
		Vec3 victimHit = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, to).inflate(1.0), e -> e.isAlive() && !e.isSpectator())) {
			if (e.getUUID().equals(shooter) || pierced.contains(e.getUUID()) || Combat.friendly(e)) {
				continue;
			}
			AABB box = e.getBoundingBox().inflate(0.1);
			Optional<Vec3> clip = box.contains(pos) ? Optional.of(pos) : box.clip(pos, to);
			if (clip.isPresent() && clip.get().distanceTo(pos) < victimAt) {
				victimAt = clip.get().distanceTo(pos);
				victimHit = clip.get();
				victim = e;
			}
		}
		int steps = Math.max(1, (int) Math.ceil(length / STEP));
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			if (victim != null && t * length >= victimAt) {
				break;
			}
			Vec3 p = pos.add(path.scale(t));
			BlockPos bp = BlockPos.containing(p);
			if (level.isOutsideBuildHeight(bp)
				|| !level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(bp.getX()), SectionPos.blockToSectionCoord(bp.getZ()))) {
				return true;
			}
			BlockState state = level.getBlockState(bp);
			if (!state.getCollisionShape(level, bp).isEmpty()) {
				Cmd.particles(level, "minecraft:crit", p.x, p.y, p.z, 0.05, 0.1, 3);
				Cmd.sound(level, "minecraft:entity.arrow.hit", p.x, p.y, p.z, 0.5f, 1.2f);
				return true;
			}
			trail(p, STEP * Math.max(1, length / steps / STEP));
		}
		if (victim != null) {
			strike(victim, victimHit);
			if (pierceLeft > 0) {
				pierceLeft--;
				pierced.add(victim.getUUID());
				pos = victimHit;
				vel = vel.scale(0.8);
				return false;
			}
			return true;
		}
		pos = to;
		vel = vel.scale(0.99).add(0, -gravity, 0);
		return age > 40;
	}

	private void trail(Vec3 p, double moved) {
		untilTrail -= moved;
		if (untilTrail > 0) {
			return;
		}
		untilTrail = musket ? 1.2 : 1.6;
		Cmd.particles(level, musket ? "minecraft:smoke" : "minecraft:crit", p.x, p.y, p.z, 0, 0, 1);
	}

	private void strike(LivingEntity victim, @Nullable Vec3 at) {
		Merc m = Mercs.ALL.get(merc);
		Entity brain = level.getEntity(shooter);
		if (m == null || !(brain instanceof LivingEntity b)) {
			return;
		}
		hits++;
		Combat.hit(level, m, b, victim, damage, true);
		if (at != null) {
			Cmd.particles(level, "minecraft:damage_indicator", at.x, at.y, at.z, 0.1, 0.1, musket ? 4 : 2);
		}
		Cmd.sound(level, "minecraft:entity.arrow.hit", victim.getX(), victim.getY() + 1, victim.getZ(), 0.6f, musket ? 0.7f : 1.1f);
	}
}
