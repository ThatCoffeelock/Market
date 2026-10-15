package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.thatcoffeelock.hoogheheeren.mixin.MobAccessor;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The men who fight your wars, and theirs. Each soldier is two entities walking as one (the same trick as a
 * Sellswords mercenary): an invisible, silent <b>brain</b> (a wandering trader with its own ideas taken out) that
 * walks and takes the hits, and a <b>body</b> (a mannequin in the house's colours) that's put where the brain is
 * every tick. We steer the brains ourselves: find the nearest enemy, close in, strike.
 * <p>
 * Ours never hurt players or each other; theirs go for our soldiers and for the court's owner. Soldiers are never
 * saved: when a battle ends (or the server stops) they're taken away.
 */
final class Soldiers {
	static final String OURS = "ours";
	static final String THEIRS = "theirs";

	static final class Soldier {
		final String id;
		final String fight;
		final String side;
		final UUID owner;
		LivingEntity brain;
		@Nullable LivingEntity body;
		int cooldown;
		int repath;
		float damage = 4;

		Soldier(String id, String fight, String side, UUID owner) {
			this.id = id;
			this.fight = fight;
			this.side = side;
			this.owner = owner;
		}
	}

	static final Map<String, Soldier> ALL = new HashMap<>();
	private static final Map<UUID, Soldier> BY_ENTITY = new HashMap<>();
	private static int next;
	/** True while a hit on a body is being passed on to its brain. */
	private static boolean forwarding;

	private Soldiers() {
	}

	static @Nullable Soldier of(@Nullable Entity e) {
		return e == null ? null : BY_ENTITY.get(e.getUUID());
	}

	static boolean isBody(Soldier s, Entity e) {
		return s.body != null && s.body.getUUID().equals(e.getUUID());
	}

	/** A soldier's brain or body loaded back in (a chunk unloaded and reloaded mid-battle): keep it if it's still in the fight. */
	static boolean adopt(Entity e) {
		Soldier s = BY_ENTITY.get(e.getUUID());
		if (s == null || !(e instanceof LivingEntity living)) {
			return false;
		}
		if (isBody(s, e)) {
			s.body = living;
		} else {
			s.brain = living;
			if (living instanceof Mob mob) {
				lobotomise(mob);
			}
		}
		return true;
	}

	static void lobotomise(Mob mob) {
		MobAccessor accessor = (MobAccessor) mob;
		accessor.hoogheheeren$goals().removeAllGoals(goal -> true);
		accessor.hoogheheeren$targets().removeAllGoals(goal -> true);
		mob.setSilent(true);
		mob.setPersistenceRequired();
	}

	private static void set(LivingEntity e, Holder<Attribute> attribute, double value) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	/** Summons a soldier for a side in a fight. Null if the brain wouldn't summon. */
	static @Nullable Soldier spawn(ServerLevel level, String fight, String side, UUID owner, double x, double y, double z, float yaw,
		String name, String colour, float damage, double health) {
		String id = fight + "#" + (next++);
		UUID brainId = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:wandering_trader " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(brainId)
			+ ",PersistenceRequired:1b,Silent:1b,DespawnDelay:0,Tags:[\"hoogheheeren\"]}");
		if (!(level.getEntity(brainId) instanceof LivingEntity brain)) {
			return null;
		}
		Soldier s = new Soldier(id, fight, side, owner);
		s.brain = brain;
		s.damage = damage;
		brain.setAttached(HeerenMod.MARK, "brain:" + id);
		if (brain instanceof Mob mob) {
			lobotomise(mob);
		}
		brain.setInvisible(true);
		set(brain, Attributes.MAX_HEALTH, health);
		set(brain, Attributes.MOVEMENT_SPEED, 0.32);
		set(brain, Attributes.FOLLOW_RANGE, 40);
		set(brain, Attributes.ARMOR, 4);
		brain.setHealth((float) health);
		brain.setYRot(yaw);
		ALL.put(id, s);
		BY_ENTITY.put(brainId, s);
		boolean crossbow = Courts.RANDOM.nextInt(3) == 0;
		Figures.Look look = new Figures.Look(name, Figures.line(side.equals(OURS) ? "Your regiment" : "The enemy", side.equals(OURS) ? "gold" : "red"),
			Courts.RANDOM.nextInt(5) == 0, Names.skin(Courts.RANDOM), colour, Courts.RANDOM.nextBoolean() ? "minecraft:iron_helmet" : "",
			crossbow ? "minecraft:crossbow" : "minecraft:iron_sword", Courts.RANDOM.nextInt(4) == 0 ? "minecraft:shield" : "");
		LivingEntity body = Figures.summon(level, "body:" + id, x, y, z, yaw, look, false);
		if (body != null) {
			Figures.LIVE.remove("body:" + id); // soldiers are tracked here, not as figures
			s.body = body;
			BY_ENTITY.put(body.getUUID(), s);
		} else {
			brain.setInvisible(false);
		}
		return s;
	}

	static List<Soldier> in(String fight, String side) {
		List<Soldier> out = new ArrayList<>();
		for (Soldier s : ALL.values()) {
			if (s.fight.equals(fight) && (side == null || s.side.equals(side))) {
				out.add(s);
			}
		}
		return out;
	}

	static void remove(Soldier s) {
		ALL.remove(s.id);
		BY_ENTITY.remove(s.brain.getUUID());
		if (!s.brain.isRemoved()) {
			s.brain.discard();
		}
		if (s.body != null) {
			BY_ENTITY.remove(s.body.getUUID());
			if (!s.body.isRemoved()) {
				s.body.discard();
			}
		}
	}

	static void removeAll(String fight) {
		for (Soldier s : in(fight, null)) {
			remove(s);
		}
	}

	static void clear() {
		for (Soldier s : new ArrayList<>(ALL.values())) {
			remove(s);
		}
		ALL.clear();
		BY_ENTITY.clear();
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		if (ALL.isEmpty()) {
			return;
		}
		for (Soldier s : new ArrayList<>(ALL.values())) {
			LivingEntity brain = s.brain;
			if (brain.isRemoved() || !brain.isAlive()) {
				continue; // unloaded with its chunk (it comes back), or dead (onDeath deals with that)
			}
			if (s.body != null && !s.body.isRemoved()) {
				sync(brain, s.body);
			}
			s.cooldown--;
			if (Math.floorMod(server.getTickCount() + s.id.hashCode(), 4) != 0) {
				continue;
			}
			if (brain.level() instanceof ServerLevel level && brain instanceof Mob mob) {
				think(s, level, mob);
			}
		}
	}

	private static void sync(LivingEntity brain, LivingEntity body) {
		body.setPos(brain.getX(), brain.getY(), brain.getZ());
		body.setYRot(brain.getYRot());
		body.setXRot(brain.getXRot());
		body.setYHeadRot(brain.getYHeadRot());
		body.setYBodyRot(brain.yBodyRot);
		body.setDeltaMovement(Vec3.ZERO);
	}

	private static @Nullable LivingEntity target(Soldier s, ServerLevel level) {
		LivingEntity best = null;
		double bestD = 32 * 32;
		for (Soldier o : ALL.values()) {
			if (!o.fight.equals(s.fight) || o.side.equals(s.side) || !o.brain.isAlive() || o.brain.level() != level) {
				continue;
			}
			double d = o.brain.distanceToSqr(s.brain);
			if (d < bestD) {
				bestD = d;
				best = o.brain;
			}
		}
		// whoever hits them (a Sellsword, a dog, a golem) gets it back
		LivingEntity grudge = s.brain.getLastHurtByMob();
		if (s.side.equals(THEIRS) && grudge != null && grudge.isAlive() && grudge.level() == level && of(grudge) == null
			&& grudge.distanceToSqr(s.brain) < 16 * 16) {
			double d = grudge.distanceToSqr(s.brain);
			if (d < bestD) {
				bestD = d;
				best = grudge;
			}
		}
		if (s.side.equals(THEIRS)) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(s.owner);
			if (owner != null && owner.level() == level && owner.isAlive() && !owner.isSpectator() && !owner.isCreative()) {
				double d = owner.distanceToSqr(s.brain);
				if (d < bestD) {
					best = owner;
				}
			}
		}
		return best;
	}

	private static void think(Soldier s, ServerLevel level, Mob brain) {
		LivingEntity target = target(s, level);
		if (target == null) {
			brain.getNavigation().stop();
			return;
		}
		brain.getLookControl().setLookAt(target, 30, 30);
		double d = brain.distanceToSqr(target);
		if (d > 2.4 * 2.4) {
			if (--s.repath <= 0 || brain.getNavigation().isDone()) {
				s.repath = 3;
				brain.getNavigation().moveTo(target, 1.0);
			}
			return;
		}
		brain.getNavigation().stop();
		if (s.cooldown <= 0) {
			s.cooldown = 18 + Courts.RANDOM.nextInt(8);
			if (s.body != null) {
				s.body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			Cmd.damage(level, target.getUUID(), s.damage, brain.getUUID());
		}
	}

	/** The rival's men in a field battle: fair game for your Sellswords ({@code "hoogheheeren:foe"}). */
	static boolean foe(@Nullable Entity e) {
		Soldier s = of(e);
		return s != null && s.side.equals(THEIRS);
	}

	// ---------------------------------------------------------------- damage

	/** Fabric's ALLOW_DAMAGE: hits on bodies go to brains; nobody hurts their own side; players can't hurt our men. */
	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		Soldier by = of(attacker);
		Soldier s = of(entity);
		if (s == null) {
			// one of ours swinging at a player: never
			return !(by != null && by.side.equals(OURS) && entity instanceof Player);
		}
		if (isBody(s, entity)) {
			if (!forwarding && entity.level() instanceof ServerLevel level && s.brain.isAlive() && allowDamage(s.brain, source, amount)) {
				forwarding = true;
				try {
					s.brain.hurtServer(level, source, amount);
				} finally {
					forwarding = false;
				}
			}
			return false;
		}
		if (by != null && by.side.equals(s.side)) {
			return false;
		}
		return !(s.side.equals(OURS) && attacker instanceof Player);
	}

	/** Fabric's AFTER_DAMAGE: show the hit on the body (the brain is invisible). */
	static void afterDamage(LivingEntity entity, DamageSource source, float taken) {
		Soldier s = of(entity);
		if (taken <= 0 || s == null || isBody(s, entity) || s.body == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		level.broadcastDamageEvent(s.body, source);
		Cmd.sound(level, "minecraft:entity.player.hurt", s.body.getX(), s.body.getY() + 1, s.body.getZ(), 0.8f, 0.9f + Courts.RANDOM.nextFloat() * 0.2f);
	}

	/** Fabric's AFTER_DEATH: a soldier's brain died. The body falls with it, and the battle counts the dead. */
	static void onDeath(LivingEntity entity, DamageSource source) {
		Soldier s = of(entity);
		if (s == null || isBody(s, entity)) {
			return;
		}
		ALL.remove(s.id);
		BY_ENTITY.remove(entity.getUUID());
		if (s.body != null) {
			BY_ENTITY.remove(s.body.getUUID());
			if (s.body.level() instanceof ServerLevel level) {
				Cmd.sound(level, "minecraft:entity.player.death", s.body.getX(), s.body.getY() + 1, s.body.getZ(), 1f, 1f);
				Cmd.particles(level, "minecraft:poof", s.body.getX(), s.body.getY() + 1, s.body.getZ(), 0.3, 0.02, 10);
			}
			s.body.discard();
		}
		Battles.fallen(s, source.getEntity() instanceof ServerPlayer p && p.getUUID().equals(s.owner));
	}
}
