package com.thatcoffeelock.sellswords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What every mercenary does, every tick: pick a fight or carry out their orders, keep their body where their brain is,
 * heal slowly, board the owner's ship or airship, and catch up when the owner goes through a portal or teleports.
 */
final class Duty {
	/** Following: keep this close, and teleport over when further than {@link #FOLLOW_TELEPORT}. */
	static final double FOLLOW_CLOSE = 3.5;
	static final double FOLLOW_TELEPORT = 24;
	/** Guarding: fight things up to this far from the post. */
	static final double GUARD_RADIUS = 16;
	/** Following: fight things up to this far from the owner. */
	static final double FOLLOW_RADIUS = 16;

	/** Per-mercenary state that isn't worth saving. */
	static final class State {
		@Nullable UUID target;
		/** A target given by a Charge! from the horn: chased further than usual. */
		boolean charging;
		int engaged;
		int shootCooldown;
		int meleeCooldown;
		int retarget;
		int repath;
		int wanderAt;
		@Nullable BlockPos wander;
		int noBody;
		int taunt;
		int regen;
		int look;
		boolean seated;
		/** Following, but unloaded: the chunk we force-loaded to fetch them, and until when. */
		@Nullable ServerLevel fetchLevel;
		int fetchX;
		int fetchZ;
		int fetchUntil;
		/** Which weapon the body holds: "ranged", "melee" or "" (unknown). */
		String shown = "";
	}

	private static final Map<String, State> STATES = new HashMap<>();
	private static int ticks;

	private Duty() {
	}

	static State state(Merc m) {
		return STATES.computeIfAbsent(m.id, id -> new State());
	}

	static void forget(Merc m) {
		State s = STATES.remove(m.id);
		if (s != null) {
			unforce(s);
		}
	}

	static void clear() {
		STATES.clear();
	}

	static int ticks() {
		return ticks;
	}

	// ---------------------------------------------------------------- the tick

	static void tick(MinecraftServer server) {
		ticks++;
		for (Merc m : new ArrayList<>(Mercs.ALL.values())) {
			try {
				tick(m);
			} catch (RuntimeException e) {
				SellswordsMod.LOG.error("{} tripped over something; carrying on", m.name, e);
			}
		}
		Bolt.tickAll();
	}

	private static void tick(Merc m) {
		State s = state(m);
		LivingEntity brain = Mercs.brain(m);
		ServerPlayer owner = Mercs.owner(m);
		if (brain == null) {
			fetch(m, s, owner);
			return;
		}
		if (!(brain.level() instanceof ServerLevel level)) {
			return;
		}
		if (s.fetchLevel != null) {
			// fetched: bring them over and let the chunk go
			if (owner != null && m.orders() == Merc.Orders.FOLLOW) {
				teleportNear(m, brain, owner, true);
			}
			unforce(s);
		}
		m.dim = Cmd.dimId(level);
		m.x = brain.getX();
		m.y = brain.getY();
		m.z = brain.getZ();
		LivingEntity body = Mercs.body(m);
		if (body == null) {
			if (++s.noBody > 40 && Mercs.bodyFailures == 0) {
				s.noBody = 0;
				body = Mercs.spawnBody(level, m, brain.getX(), brain.getY(), brain.getZ());
			}
		} else {
			s.noBody = 0;
		}
		if (ticks % 20 == 0) {
			brain.setInvisible(body != null || Mercs.bodyFailures == 0);
			brain.setSilent(true);
		}
		if (m.orders() == Merc.Orders.FOLLOW && (owner == null || owner.isSpectator())) {
			hold(m, brain, "their owner left"); // logged out (or a server restart): guard where they stand
		}
		s.shootCooldown--;
		s.meleeCooldown--;
		if (s.taunt > 0) {
			s.taunt--;
		}
		regen(m, s, brain);
		boolean seated = Boarding.tick(m, s, brain, body, owner);
		if (!seated) {
			if (owner != null && m.orders() == Merc.Orders.FOLLOW && owner.level() != level) {
				teleportNear(m, brain, owner, false); // the owner went through a portal
				return;
			}
			if (brain.isInWater() || brain.isInLava()) {
				((Mob) brain).getJumpControl().jump();
			}
		}
		LivingEntity target = target(m, s, level, brain, owner);
		if (target != null) {
			engage(m, s, level, brain, body, target, seated);
		} else {
			s.engaged = 0;
			s.charging = false;
			if (body != null) {
				Mercs.wield(level, m, body, m.rank().path != Rank.Path.MELEE);
			}
			if (!seated) {
				idle(m, s, level, brain, owner);
			}
		}
		if (m.rank().taunts() && s.taunt <= 0 && !seated) {
			taunt(level, brain);
			s.taunt = 60;
		}
		if (body != null && !seated) {
			sync(brain, body);
		}
	}

	/** The body goes where the brain is, facing the same way. */
	static void sync(LivingEntity brain, LivingEntity body) {
		if (body.isPassenger()) {
			return;
		}
		body.setPos(brain.getX(), brain.getY(), brain.getZ());
		body.setYRot(brain.getYRot());
		body.setXRot(brain.getXRot());
		body.setYHeadRot(brain.getYHeadRot());
		body.setYBodyRot(brain.yBodyRot);
		body.setDeltaMovement(Vec3.ZERO);
		if (body.isOnFire() && !brain.isOnFire()) {
			body.clearFire();
		}
	}

	private static void regen(Merc m, State s, LivingEntity brain) {
		if (++s.regen < 100) {
			return;
		}
		s.regen = 0;
		if (brain.getHealth() < brain.getMaxHealth() && brain.isAlive()) {
			brain.heal(m.rank().regen);
		}
	}

	// ---------------------------------------------------------------- picking a fight

	/** Where they fight around and how far: the owner, their post or their station. */
	private record Area(Vec3 center, double radius) {
	}

	private static Area area(Merc m, LivingEntity brain, @Nullable ServerPlayer owner) {
		return switch (m.orders()) {
			case FOLLOW -> owner != null && owner.level() == brain.level() ? new Area(owner.position(), FOLLOW_RADIUS) : new Area(brain.position(), FOLLOW_RADIUS);
			case GUARD -> new Area(Vec3.atBottomCenterOf(m.post()), GUARD_RADIUS);
			case STATION -> {
				Station st = Stations.ALL.get(m.home);
				yield st == null ? new Area(brain.position(), GUARD_RADIUS) : new Area(Vec3.atBottomCenterOf(st.pos()), SellswordsConfig.get().stationRadius);
			}
		};
	}

	private static boolean canFight(Merc m, LivingEntity brain, LivingEntity e) {
		if (!e.isAlive() || e.isRemoved() || e.level() != brain.level() || Combat.friendly(e) || e == brain) {
			return false;
		}
		Rank r = m.rank();
		if (Combat.isCreeper(e) && !r.shoots()) {
			return false; // nobody hugs a creeper with a sword
		}
		return !e.isInvisible() || brain.distanceTo(e) < 4;
	}

	private static @Nullable LivingEntity target(Merc m, State s, ServerLevel level, LivingEntity brain, @Nullable ServerPlayer owner) {
		Area area = area(m, brain, owner);
		LivingEntity current = null;
		if (s.target != null && level.getEntity(s.target) instanceof LivingEntity e && canFight(m, brain, e)) {
			double leash = s.charging ? 48 : area.radius + 12;
			if (e.position().distanceTo(area.center) <= leash && ++s.engaged < 900) {
				current = e;
			}
		}
		if (current == null && --s.retarget <= 0) {
			s.retarget = 10;
			s.charging = false;
			s.engaged = 0;
			current = pick(m, level, brain, owner, area);
		}
		s.target = current == null ? null : current.getUUID();
		return current;
	}

	/** In order: whoever hurt them, whoever hurt the owner or got hit by them, monsters about, game if hunting. */
	private static @Nullable LivingEntity pick(Merc m, ServerLevel level, LivingEntity brain, @Nullable ServerPlayer owner, Area area) {
		LivingEntity attacker = brain.getLastHurtByMob();
		if (attacker != null && brain.tickCount - brain.getLastHurtByMobTimestamp() < 200 && canFight(m, brain, attacker)
			&& attacker.position().distanceTo(area.center) < area.radius + 12) {
			return attacker;
		}
		if (owner != null && m.orders() == Merc.Orders.FOLLOW && owner.level() == level) {
			LivingEntity foe = owner.getLastHurtByMob();
			if (foe != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && canFight(m, brain, foe)) {
				return foe;
			}
			LivingEntity prey = owner.getLastHurtMob();
			if (prey != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200 && canFight(m, brain, prey)
				&& prey.distanceTo(owner) < FOLLOW_RADIUS + 8) {
				return prey; // the hunting party joins in on whatever you're hitting
			}
		}
		double r = area.radius;
		double sight = m.orders() == Merc.Orders.STATION ? 32 : r + 8;
		AABB box = new AABB(area.center.x - r, area.center.y - 10, area.center.z - r, area.center.x + r, area.center.y + 10, area.center.z + r);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.position().distanceTo(area.center) <= r)) {
			if (!canFight(m, brain, e)) {
				continue;
			}
			boolean foe = Combat.hostile(e);
			boolean game = !foe && m.hunting && m.orders() == Merc.Orders.FOLLOW && Combat.game(e);
			if (!foe && !game) {
				continue;
			}
			double d = brain.distanceTo(e);
			if (d > sight || (d > 4 && !brain.hasLineOfSight(e))) {
				continue;
			}
			// monsters going after a friend first, then the closest; game comes last
			double score = d + (game ? 100 : 0) - (e instanceof Mob mob && Combat.friendly(mob.getTarget()) ? 50 : 0);
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** Told by the horn: go for this one. */
	static void charge(Merc m, LivingEntity target) {
		State s = state(m);
		s.target = target.getUUID();
		s.charging = true;
		s.engaged = 0;
	}

	/** Something hit them: if they're not busy, that's the new target. */
	static void hurt(Merc m, @Nullable Entity attacker) {
		State s = state(m);
		if (s.target == null && attacker instanceof LivingEntity && !Combat.friendly(attacker)) {
			s.target = attacker.getUUID();
			s.engaged = 0;
		}
	}

	// ---------------------------------------------------------------- fighting

	private static void engage(Merc m, State s, ServerLevel level, LivingEntity brain, @Nullable LivingEntity body, LivingEntity target, boolean seated) {
		Mob mob = (Mob) brain;
		Rank r = m.rank();
		double d = brain.distanceTo(target);
		double reach = 1.9 + target.getBbWidth() / 2;
		boolean creeper = Combat.isCreeper(target);
		boolean sees = brain.hasLineOfSight(target);
		boolean canShoot = r.shoots() && sees && d <= r.range;
		mob.getLookControl().setLookAt(target, 30, 30);
		if (seated) {
			// aboard a ship or airship: shoot from the rail, no chasing
			if (canShoot && d > 2 && s.shootCooldown <= 0) {
				shoot(m, s, level, brain, body, target);
			}
			return;
		}
		boolean ranger = r.path != Rank.Path.MELEE;
		if (d <= reach && !creeper && sees) {
			mob.getNavigation().stop();
			if (body != null) {
				Mercs.wield(level, m, body, false);
			}
			if (s.meleeCooldown <= 0) {
				s.meleeCooldown = r.meleeCooldown();
				if (body != null) {
					swing(level, body);
				}
				Combat.hit(level, m, brain, target, r.melee, false);
				Cmd.sound(level, "minecraft:entity.player.attack.strong", brain.getX(), brain.getY() + 1, brain.getZ(), 0.7f, 1f);
			}
			return;
		}
		if (creeper && d < 5 && r.shoots()) {
			// back off and shoot
			Vec3 away = brain.position().subtract(target.position()).normalize().scale(6).add(brain.position());
			mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
			if (canShoot && s.shootCooldown <= 0) {
				shoot(m, s, level, brain, body, target);
			}
			return;
		}
		// rangers shoot whenever they can; the melee path shoots at things it can't get to (or from far off)
		boolean far = d > 8 || target.getY() - brain.getY() > 2.5 || creeper;
		if (canShoot && (ranger || far)) {
			if (ranger || creeper) {
				mob.getNavigation().stop();
			} else {
				chase(m, s, mob, target);
			}
			if (body != null) {
				Mercs.wield(level, m, body, true);
			}
			if (s.shootCooldown <= 0) {
				shoot(m, s, level, brain, body, target);
			}
			return;
		}
		if (body != null) {
			Mercs.wield(level, m, body, false);
		}
		chase(m, s, mob, target);
	}

	/** Closes in, but a guard doesn't leave their post (rangers stay on it, swords go 16 blocks out at most). */
	private static void chase(Merc m, State s, Mob mob, LivingEntity target) {
		if (m.orders() == Merc.Orders.GUARD && !s.charging) {
			Vec3 post = Vec3.atBottomCenterOf(m.post());
			double limit = m.rank().path == Rank.Path.MELEE ? GUARD_RADIUS : 3;
			if (target.position().distanceTo(post) > limit + 2) {
				if (mob.position().distanceTo(post) > 1.5) {
					walk(s, mob, post, 1.1);
				}
				return;
			}
		}
		if (--s.repath <= 0 || mob.getNavigation().isDone()) {
			s.repath = 8;
			mob.getNavigation().moveTo(target, 1.25);
		}
	}

	private static void shoot(Merc m, State s, ServerLevel level, LivingEntity brain, @Nullable LivingEntity body, LivingEntity target) {
		Rank r = m.rank();
		s.shootCooldown = r.shootCooldown;
		if (body != null) {
			Mercs.wield(level, m, body, true);
			swing(level, body);
		}
		Bolt.fire(level, m, brain, target);
	}

	/** The arm swing everyone nearby sees. */
	static void swing(ServerLevel level, LivingEntity body) {
		level.getChunkSource().broadcast(body, new ClientboundAnimatePacket(body, ClientboundAnimatePacket.SWING_MAIN_HAND));
	}

	/** A Bulwark bellows: every monster within 10 blocks going after someone else comes for him instead. */
	private static void taunt(ServerLevel level, LivingEntity brain) {
		boolean shouted = false;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, brain.getBoundingBox().inflate(10), e -> e.isAlive() && Combat.hostile(e))) {
			LivingEntity t = mob.getTarget();
			if (t != null && t != brain && Combat.friendly(t)) {
				mob.setTarget(brain);
				Cmd.particles(level, "minecraft:angry_villager", mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 0.2, 0, 2);
				shouted = true;
			}
		}
		if (shouted) {
			Cmd.sound(level, "minecraft:entity.ravager.roar", brain.getX(), brain.getY() + 1, brain.getZ(), 0.6f, 1.6f);
		}
	}

	// ---------------------------------------------------------------- orders

	private static void walk(State s, Mob mob, Vec3 to, double speed) {
		if (--s.repath <= 0 || mob.getNavigation().isDone()) {
			s.repath = 20;
			mob.getNavigation().moveTo(to.x, to.y, to.z, speed);
		}
	}

	private static void idle(Merc m, State s, ServerLevel level, LivingEntity brain, @Nullable ServerPlayer owner) {
		Mob mob = (Mob) brain;
		switch (m.orders()) {
			case FOLLOW -> {
				if (owner == null) {
					return;
				}
				double d = brain.distanceTo(owner);
				if (d > FOLLOW_TELEPORT) {
					teleportNear(m, brain, owner, false);
				} else if (d > FOLLOW_CLOSE) {
					if (--s.repath <= 0 || mob.getNavigation().isDone()) {
						s.repath = 10;
						mob.getNavigation().moveTo(owner, d > 10 ? 1.4 : 1.05);
					}
				} else {
					mob.getNavigation().stop();
					if (++s.look % 40 == 0) {
						mob.getLookControl().setLookAt(owner, 30, 30);
					}
				}
			}
			case GUARD -> {
				if (!Cmd.dimId(level).equals(m.postDim) && !m.postDim.isEmpty()) {
					ServerLevel there = Mercs.level(m.postDim);
					if (there != null) {
						BlockPos p = m.post();
						Cmd.tp(there, brain.getUUID(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
						teleportBody(m, there, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
					}
					return;
				}
				Vec3 post = Vec3.atBottomCenterOf(m.post());
				double d = brain.position().distanceTo(post);
				if (d > 48) {
					Cmd.tp(level, brain.getUUID(), post.x, post.y, post.z);
				} else if (d > 1.2) {
					walk(s, mob, post, 1.0);
				} else {
					lookAround(s, mob);
				}
			}
			case STATION -> {
				Station st = Stations.ALL.get(m.home);
				if (st == null) {
					hold(m, brain, "their station is gone");
					return;
				}
				ServerLevel home = Mercs.level(st.dim);
				Vec3 center = Vec3.atBottomCenterOf(st.pos());
				if (home != level || brain.position().distanceTo(center) > SellswordsConfig.get().stationRadius + 60) {
					if (home != null) {
						BlockPos spot = Spots.near(home, st.pos(), 3);
						Cmd.tp(home, brain.getUUID(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
						teleportBody(m, home, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
					}
					return;
				}
				patrol(m, s, level, mob, st);
			}
		}
	}

	/** Wanders between random spots within the station's radius, at a stroll. */
	private static void patrol(Merc m, State s, ServerLevel level, Mob mob, Station st) {
		if (s.wander != null && mob.position().distanceTo(Vec3.atBottomCenterOf(s.wander)) > 2 && ticks < s.wanderAt) {
			walk(s, mob, Vec3.atBottomCenterOf(s.wander), 0.75);
			return;
		}
		if (s.wander != null && ticks < s.wanderAt) {
			lookAround(s, mob);
			return;
		}
		int radius = SellswordsConfig.get().stationRadius;
		double angle = Mercs.RANDOM.nextDouble() * Math.PI * 2;
		double dist = Math.sqrt(Mercs.RANDOM.nextDouble()) * radius;
		int x = st.x + (int) Math.round(Math.cos(angle) * dist);
		int z = st.z + (int) Math.round(Math.sin(angle) * dist);
		if (!level.isLoaded(new BlockPos(x, st.y, z))) {
			x = st.x;
			z = st.z;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		s.wander = new BlockPos(x, y, z);
		s.wanderAt = ticks + 200 + Mercs.RANDOM.nextInt(300);
		s.repath = 0;
	}

	/** A patrol point for the smoke test: where they're headed. */
	static @Nullable BlockPos wanderTarget(Merc m) {
		return state(m).wander;
	}

	private static void lookAround(State s, Mob mob) {
		if (++s.look % 80 == 0) {
			double a = Mercs.RANDOM.nextDouble() * Math.PI * 2;
			mob.getLookControl().setLookAt(mob.getX() + Math.cos(a) * 8, mob.getEyeY(), mob.getZ() + Math.sin(a) * 8);
		}
	}

	/** Orders change: guard where they stand. */
	static void hold(Merc m, LivingEntity brain, String why) {
		m.orders(Merc.Orders.GUARD);
		m.post(Cmd.dimId(brain.level()), brain.blockPosition());
		Mercs.changed();
		SellswordsMod.LOG.debug("{} holds at {}: {}", m.name, brain.blockPosition(), why);
	}

	// ---------------------------------------------------------------- moving them about

	/** Puts them beside the owner (behind them if there's room). Only when the owner is standing on something. */
	static boolean teleportNear(Merc m, LivingEntity brain, ServerPlayer owner, boolean anyway) {
		Entity root = owner.getRootVehicle();
		if (!anyway && !root.onGround() && !owner.isInWater()) {
			return false;
		}
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 back = owner.getLookAngle().multiply(-1, 0, -1);
		BlockPos behind = BlockPos.containing(owner.getX() + back.x * 2, owner.getY(), owner.getZ() + back.z * 2);
		BlockPos spot = Spots.standable(level, behind) ? behind : Spots.find(level, owner.blockPosition(), 3);
		if (spot == null) {
			spot = owner.blockPosition();
		}
		double x = spot.getX() + 0.5;
		double z = spot.getZ() + 0.5;
		Cmd.tp(level, brain.getUUID(), x, spot.getY(), z);
		teleportBody(m, level, x, spot.getY(), z);
		((Mob) brain).getNavigation().stop();
		return true;
	}

	static void teleportBody(Merc m, ServerLevel level, double x, double y, double z) {
		LivingEntity body = Mercs.body(m);
		if (body != null) {
			if (body.isPassenger()) {
				body.stopRiding();
			}
			Cmd.tp(level, body.getUUID(), x, y, z);
		}
	}

	/** A brain just loaded (a restart, a chunk coming back, a trip through a portal). */
	static void arrived(Merc m, LivingEntity brain) {
		State s = state(m);
		s.shown = "";
		s.repath = 0;
	}

	/**
	 * A following mercenary whose chunk isn't loaded (the owner pearled away, or logged in somewhere else): load that
	 * chunk for a moment so they can be brought over.
	 */
	private static void fetch(Merc m, State s, @Nullable ServerPlayer owner) {
		if (s.fetchLevel != null && ticks > s.fetchUntil) {
			unforce(s);
		}
		if (owner == null || m.orders() != Merc.Orders.FOLLOW || s.fetchLevel != null || ticks % 40 != 0 || m.dim.isEmpty()) {
			return;
		}
		ServerLevel level = Mercs.level(m.dim);
		if (level == null) {
			return;
		}
		s.fetchLevel = level;
		s.fetchX = (int) Math.floor(m.x);
		s.fetchZ = (int) Math.floor(m.z);
		s.fetchUntil = ticks + 200;
		Cmd.run(level, "forceload add " + s.fetchX + " " + s.fetchZ);
	}

	private static void unforce(State s) {
		if (s.fetchLevel != null) {
			Cmd.run(s.fetchLevel, "forceload remove " + s.fetchX + " " + s.fetchZ);
			s.fetchLevel = null;
		}
	}

	// ---------------------------------------------------------------- the owner

	/** Logged out: whoever was following guards where they stand. */
	static void ownerLeft(ServerPlayer player) {
		for (Merc m : Mercs.ownedBy(player.getUUID())) {
			if (m.orders() == Merc.Orders.FOLLOW) {
				LivingEntity brain = Mercs.brain(m);
				m.orders(Merc.Orders.GUARD);
				if (brain != null) {
					m.post(Cmd.dimId(brain.level()), brain.blockPosition());
				} else {
					m.post(m.dim, BlockPos.containing(m.x, m.y, m.z));
				}
				Mercs.changed();
			}
		}
	}

	/** The owner died: whoever was following guards the spot (and whatever the owner dropped there). */
	static void ownerDied(ServerPlayer player) {
		int n = 0;
		for (Merc m : Mercs.ownedBy(player.getUUID())) {
			if (m.orders() == Merc.Orders.FOLLOW) {
				m.orders(Merc.Orders.GUARD);
				m.post(Cmd.dimId(player.level()), player.blockPosition());
				Mercs.changed();
				n++;
			}
		}
		if (n > 0) {
			player.sendSystemMessage(Component.literal((n == 1 ? "Your mercenary is" : "Your mercenaries are") + " guarding your things at "
				+ player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ() + ". They promise not to look through them.")
				.withStyle(ChatFormatting.GOLD));
		}
	}

	/** Through a portal: everyone following comes along. */
	static void ownerChangedWorld(ServerPlayer player) {
		SellswordsMod.later(2, () -> {
			for (Merc m : Mercs.ownedBy(player.getUUID())) {
				LivingEntity brain = Mercs.brain(m);
				if (m.orders() == Merc.Orders.FOLLOW && brain != null && brain.level() != player.level()) {
					teleportNear(m, brain, player, true);
				}
			}
		});
	}
}
