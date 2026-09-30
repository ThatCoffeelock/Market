package com.thatcoffeelock.cannon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A ball in flight. It's an item display we move ourselves every tick (the client interpolates between
 * positions), with our own gravity and a swept collision check, so it can't tunnel through walls at speed.
 * It blows up (without breaking any blocks) on the first block or mob it touches, fizzles in water, and quietly vanishes if it flies
 * into chunks nobody has loaded.
 */
final class Cannonball {
	static final double GRAVITY = 0.05;
	static final double DRAG = 0.99;
	/** A creeper is 3, TNT is 4. Hurts mobs and players, never breaks blocks. */
	static final float POWER = 3.0f;
	static final int MAX_AGE = 400;
	static final String TAG = "cannon_ball";
	/** Collision is checked at points this far apart along the path. */
	private static final double STEP = 0.2;

	private static final List<Cannonball> FLYING = new ArrayList<>();

	final ServerLevel level;
	final Entity display;
	Vec3 pos;
	Vec3 vel;
	private final @Nullable UUID shooter;
	private int age;
	private boolean done;

	private Cannonball(ServerLevel level, Entity display, Vec3 pos, Vec3 vel, @Nullable Entity shooter) {
		this.level = level;
		this.display = display;
		this.pos = pos;
		this.vel = vel;
		this.shooter = shooter == null ? null : shooter.getUUID();
	}

	static @Nullable Cannonball launch(ServerLevel level, Vec3 pos, Vec3 vel, @Nullable Entity shooter) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:item_display " + Cmd.pos(pos.x, pos.y, pos.z) + " {" + Cmd.uuidNbt(id) + ",Tags:[\"" + TAG
			+ "\"],billboard:\"center\",teleport_duration:1,item:{id:\"minecraft:firework_star\",count:1},"
			+ "transformation:" + Cannon.transformation(0, 0, 0, 0, 0.8, 0.8, 0.8) + "}");
		Entity display = level.getEntity(id);
		if (display == null) {
			CannonMod.LOG.error("Could not summon a cannonball at {}", pos);
			return null;
		}
		display.setAttached(CannonMod.BALL, true);
		Cannonball ball = new Cannonball(level, display, pos, vel, shooter);
		FLYING.add(ball);
		return ball;
	}

	/** Next tick's velocity. */
	static Vec3 step(Vec3 vel) {
		return vel.scale(DRAG).add(0, -GRAVITY, 0);
	}

	static List<Cannonball> flying() {
		return new ArrayList<>(FLYING);
	}

	static boolean isFlying(Entity entity) {
		for (Cannonball ball : FLYING) {
			if (ball.display == entity) {
				return true;
			}
		}
		return false;
	}

	static void tickAll() {
		for (Cannonball ball : flying()) {
			try {
				ball.tick();
			} catch (RuntimeException e) {
				CannonMod.LOG.error("Cannonball crashed mid-air; removing it", e);
				ball.remove();
			}
		}
	}

	static void clear() {
		for (Cannonball ball : flying()) {
			ball.remove();
		}
		FLYING.clear();
	}

	private void remove() {
		done = true;
		FLYING.remove(this);
		if (!display.isRemoved()) {
			display.discard();
		}
	}

	private enum Kind { BLOCK, ENTITY, WATER, OUT }

	private record Hit(Kind kind, Vec3 at) {
	}

	private void tick() {
		if (done || display.isRemoved()) {
			remove();
			return;
		}
		age++;
		Vec3 to = pos.add(vel);
		Hit hit = trace(pos, to);
		if (hit != null) {
			switch (hit.kind) {
				case BLOCK, ENTITY -> explode(hit.at);
				case WATER -> splash(hit.at);
				case OUT -> remove();
			}
			return;
		}
		pos = to;
		vel = step(vel);
		if (age > MAX_AGE) {
			remove();
			return;
		}
		display.setPos(pos.x, pos.y, pos.z);
		Cmd.particles(level, "minecraft:smoke", pos.x, pos.y, pos.z, 0.05, 0.0, 2);
	}

	/** The first thing between from and to, or null if the way is clear. */
	private @Nullable Hit trace(Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		double entityDist = Double.MAX_VALUE;
		Vec3 entityHit = null;
		AABB sweep = new AABB(from, to).inflate(1.0);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !e.isSpectator())) {
			if (age <= 3 && shooter != null && entity.getUUID().equals(shooter)) {
				continue; // don't clip the gunner on the way out
			}
			Optional<Vec3> clip = entity.getBoundingBox().inflate(0.3).clip(from, to);
			if (clip.isPresent() && clip.get().distanceTo(from) < entityDist) {
				entityDist = clip.get().distanceTo(from);
				entityHit = clip.get();
			}
		}

		int steps = Math.max(1, (int) Math.ceil(length / STEP));
		Vec3 last = from;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			if (entityHit != null && t * length >= entityDist) {
				return new Hit(Kind.ENTITY, entityHit);
			}
			Vec3 p = from.add(path.scale(t));
			BlockPos bp = BlockPos.containing(p);
			if (level.isOutsideBuildHeight(bp)) {
				if (bp.getY() < level.getSeaLevel()) {
					return new Hit(Kind.OUT, p); // into the void
				}
				last = p;
				continue; // above the build limit: nothing up there to hit
			}
			if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(bp.getX()), SectionPos.blockToSectionCoord(bp.getZ()))) {
				return new Hit(Kind.OUT, p); // don't load chunks just for a stray ball
			}
			BlockState state = level.getBlockState(bp);
			if (!state.getCollisionShape(level, bp).isEmpty()) {
				return new Hit(Kind.BLOCK, last);
			}
			if (!state.getFluidState().isEmpty()) {
				return new Hit(Kind.WATER, p);
			}
			last = p;
		}
		return entityHit != null ? new Hit(Kind.ENTITY, entityHit) : null;
	}

	private void explode(Vec3 at) {
		remove();
		level.explode(null, at.x, at.y, at.z, POWER, Level.ExplosionInteraction.NONE);
	}

	private void splash(Vec3 at) {
		remove();
		Cmd.sound(level, "minecraft:entity.generic.splash", at.x, at.y, at.z, 1.5f, 0.8f);
		Cmd.particles(level, "minecraft:splash", at.x, at.y + 0.5, at.z, 0.5, 0.2, 40);
		Cmd.particles(level, "minecraft:bubble_column_up", at.x, at.y, at.z, 0.3, 0.1, 15);
	}
}
