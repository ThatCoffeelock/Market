package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A bomb, falling or fizzing. It's an item display we move ourselves every tick (the client interpolates), with our
 * own gravity and a swept collision check so it can't tunnel through a roof at speed. Unlike a Cannon's cannonball it
 * blows holes in buildings (config bombsBreakBlocks).
 *
 * Two ways to go off: dropped from an airship it goes off on the first block, mob or water it hits; set on the
 * ground by hand it sits there with a lit fuse. Bombs aren't saved: on a restart, anything still falling is gone.
 */
final class Bomb {
	static final double GRAVITY = 0.04;
	static final double DRAG = 0.98;
	static final int MAX_AGE = 1200;
	static final String TAG = "blimey_bomb";
	/** Collision is checked at points this far apart along the path. */
	private static final double STEP = 0.25;

	private static final List<Bomb> LIVE = new ArrayList<>();

	final ServerLevel level;
	final Entity display;
	final BlimeyItems.BombKind kind;
	Vec3 pos;
	Vec3 vel;
	/** Ticks left on a lit fuse; 0 means it goes off on impact instead. */
	int fuse;
	private final boolean impact;
	/** Who let it go (for Piloteering XP and the Payload perk), or null. */
	private final @Nullable UUID dropper;
	private int age;
	private boolean done;

	private Bomb(ServerLevel level, Entity display, BlimeyItems.BombKind kind, Vec3 pos, Vec3 vel, int fuse, @Nullable UUID dropper) {
		this.level = level;
		this.display = display;
		this.kind = kind;
		this.pos = pos;
		this.vel = vel;
		this.fuse = fuse;
		this.impact = fuse <= 0;
		this.dropper = dropper;
	}

	/** Lets a bomb go here. {@code fuse} in ticks; 0 = goes off on impact. {@code dropper} gets the Piloteering XP. */
	static @Nullable Bomb launch(ServerLevel level, BlimeyItems.BombKind kind, Vec3 pos, Vec3 vel, int fuse, @Nullable UUID dropper) {
		UUID id = UUID.randomUUID();
		float s = kind.scale;
		Cmd.run(level, "summon minecraft:item_display " + Cmd.pos(pos.x, pos.y, pos.z) + " {" + Cmd.uuidNbt(id) + ",Tags:[\"" + TAG
			+ "\"],teleport_duration:1,item:{id:\"" + kind.model + "\",count:1},transformation:{left_rotation:[0f,0f,0f,1f],"
			+ "right_rotation:[0f,0f,0f,1f],translation:[0f," + Cmd.f(s / 2) + "f,0f],scale:[" + Cmd.f(s) + "f," + Cmd.f(s) + "f," + Cmd.f(s) + "f]}}");
		Entity display = level.getEntity(id);
		if (display == null) {
			BlimeyMod.LOG.error("Could not summon a {} at {}", kind.title, pos);
			return null;
		}
		display.setAttached(BlimeyMod.BOMB, true);
		Bomb bomb = new Bomb(level, display, kind, pos, vel, fuse, dropper);
		LIVE.add(bomb);
		Cmd.sound(level, "minecraft:entity.tnt.primed", pos.x, pos.y, pos.z, 1.0f, impact(fuse) ? 0.6f : 1.0f);
		return bomb;
	}

	private static boolean impact(int fuse) {
		return fuse <= 0;
	}

	static List<Bomb> live() {
		return new ArrayList<>(LIVE);
	}

	static boolean isLive(Entity entity) {
		for (Bomb bomb : LIVE) {
			if (bomb.display == entity) {
				return true;
			}
		}
		return false;
	}

	static void tickAll() {
		for (Bomb bomb : live()) {
			try {
				bomb.tick();
			} catch (RuntimeException e) {
				BlimeyMod.LOG.error("A bomb crashed mid-air; removing it", e);
				bomb.remove();
			}
		}
	}

	static void clear() {
		for (Bomb bomb : live()) {
			bomb.remove();
		}
		LIVE.clear();
	}

	boolean isDone() {
		return done;
	}

	private void remove() {
		done = true;
		LIVE.remove(this);
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
		if (!impact) {
			fuse--;
			if (fuse <= 0) {
				explode(pos);
				return;
			}
			if (age % 2 == 0) {
				Cmd.particles(level, "minecraft:smoke", pos.x, pos.y + kind.scale + 0.1, pos.z, 0.05, 0.01, 2);
			}
		}
		Vec3 to = pos.add(vel);
		Hit hit = vel.lengthSqr() < 1.0E-6 ? restingHit() : trace(pos, to);
		if (hit != null) {
			if (impact) {
				if (hit.kind == Kind.OUT) {
					remove();
				} else {
					explode(hit.at);
				}
				return;
			}
			// a lit bomb just lands and sits there fizzing
			if (hit.kind == Kind.OUT) {
				remove();
				return;
			}
			pos = hit.at;
			vel = Vec3.ZERO;
		} else {
			pos = to;
			vel = vel.scale(DRAG).add(0, -GRAVITY, 0);
		}
		if (age > MAX_AGE && impact) {
			remove();
			return;
		}
		display.setPos(pos.x, pos.y, pos.z);
		if (impact && age % 2 == 0) {
			Cmd.particles(level, "minecraft:smoke", pos.x, pos.y + kind.scale, pos.z, 0.05, 0.0, 1);
		}
	}

	/** A bomb sitting still: has the ground under it gone? Then it starts falling again. */
	private @Nullable Hit restingHit() {
		BlockPos below = BlockPos.containing(pos.x, pos.y - 0.05, pos.z);
		if (!level.isLoaded(below)) {
			return new Hit(Kind.OUT, pos);
		}
		BlockState state = level.getBlockState(below);
		if (!state.getCollisionShape(level, below).isEmpty() || !state.getFluidState().isEmpty()) {
			return new Hit(Kind.BLOCK, pos);
		}
		vel = new Vec3(0, -GRAVITY, 0);
		return null;
	}

	/** The first thing between from and to, or null if the way is clear. */
	private @Nullable Hit trace(Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		double entityDist = Double.MAX_VALUE;
		Vec3 entityHit = null;
		if (impact) {
			AABB sweep = new AABB(from, to).inflate(1.0);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !e.isSpectator() && !Airships.isAboard(e))) {
				Optional<Vec3> clip = entity.getBoundingBox().inflate(0.2).clip(from, to);
				if (clip.isPresent() && clip.get().distanceTo(from) < entityDist) {
					entityDist = clip.get().distanceTo(from);
					entityHit = clip.get();
				}
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
			if (bp.getY() < level.getMinY()) {
				return new Hit(Kind.OUT, p); // into the void
			}
			if (bp.getY() >= level.getMaxY()) {
				last = p;
				continue; // above the build limit: nothing up there to hit
			}
			if (!level.isLoaded(bp)) {
				return new Hit(Kind.OUT, p); // don't load chunks for a stray bomb
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
		BlimeyConfig cfg = BlimeyConfig.get();
		Level.ExplosionInteraction interaction = cfg.bombsBreakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
		float power = (float) (kind.power() * (1 + SkillsLink.bonus(dropper, "piloteering/payload")));
		if (dropper != null) {
			int caught = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(at, at).inflate(power + 1)).size();
			double base = switch (kind) {
				case SMALL -> 5;
				case BIG -> 10;
				case HUGE -> 20;
			};
			SkillsLink.xp(dropper, "piloteering", base + 6.0 * caught);
		}
		level.explode(null, at.x, at.y, at.z, power, interaction);
		if (kind != BlimeyItems.BombKind.SMALL) {
			Cmd.particles(level, "minecraft:explosion_emitter", at.x, at.y + 1, at.z, kind.power() / 3.0, 0.0, kind == BlimeyItems.BombKind.HUGE ? 6 : 2);
			Cmd.sound(level, "minecraft:entity.generic.explode", at.x, at.y, at.z, 4.0f, kind == BlimeyItems.BombKind.HUGE ? 0.5f : 0.7f);
		}
	}
}
