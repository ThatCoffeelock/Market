package com.thatcoffeelock.sellswords;

import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Who's a friend, who's fair game, and what happens when a mercenary hits something or something hits a mercenary.
 * Mercenaries never hurt players, villagers, golems, tamed or named animals, colony folk or each other, not even by
 * accident: their bolts fly straight through friends, and any damage they'd do to one is cancelled.
 */
public final class Combat {
	/** Never attacked, and protected. */
	static final Set<String> FRIENDS = Set.of("villager", "wandering_trader", "iron_golem", "snow_golem", "copper_golem", "allay",
		"armor_stand", "mannequin", "cat", "parrot", "horse", "donkey", "mule", "camel", "llama", "trader_llama", "happy_ghast");
	/** Monsters (or neutral mobs) best left alone unless they start something. */
	static final Set<String> LEAVE_ALONE = Set.of("enderman", "zombified_piglin", "piglin", "warden", "creaking", "bee", "wolf",
		"polar_bear", "panda", "dolphin", "fox", "goat", "ender_dragon", "wither");
	/** What a hunting party shoots. */
	static final Set<String> GAME = Set.of("cow", "pig", "sheep", "chicken", "rabbit", "mooshroom");
	static final Set<String> ILLAGERS = Set.of("pillager", "vindicator", "evoker", "illusioner", "ravager");

	/** True while a hit on a body is being passed on to its brain. */
	private static boolean forwarding;

	private Combat() {
	}

	static String type(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
	}

	static boolean isIllager(Entity e) {
		return ILLAGERS.contains(type(e));
	}

	static boolean isCreeper(Entity e) {
		return type(e).equals("creeper");
	}

	/** Players, mercenaries, villagers, golems, tamed and named animals. */
	static boolean friendly(@Nullable Entity e) {
		if (e == null) {
			return false;
		}
		if (e instanceof Player || Mercs.isMerc(e)) {
			return true;
		}
		if (FRIENDS.contains(type(e))) {
			return true;
		}
		if (e instanceof TamableAnimal animal && animal.isTame()) {
			return true;
		}
		return !(e instanceof Enemy) && e.hasCustomName();
	}

	/** Something to fight: a monster, or anything at all that's going after a friend. */
	static boolean hostile(Entity e) {
		if (!(e instanceof LivingEntity living) || !living.isAlive() || friendly(e)) {
			return false;
		}
		if (e instanceof Mob mob && mob.getTarget() != null && friendly(mob.getTarget())) {
			return true;
		}
		return e instanceof Enemy && !LEAVE_ALONE.contains(type(e));
	}

	/** Fair game for a hunting party: farm animals that nobody named, leashed or is raising. */
	static boolean game(Entity e) {
		if (!(e instanceof LivingEntity living) || !living.isAlive() || living.isBaby() || e.hasCustomName() || e.isPassenger()) {
			return false;
		}
		if (e instanceof Mob mob && mob.isLeashed()) {
			return false;
		}
		return GAME.contains(type(e)) && !friendly(e);
	}

	// ---------------------------------------------------------------- dealing damage

	/** Damage a mercenary deals, with the owner's Drillmaster perk and their grudge against illagers. */
	static float damage(Merc m, Entity target, float base) {
		SellswordsConfig cfg = SellswordsConfig.get();
		double d = base * cfg.damageMultiplier * (1 + SkillsLink.bonus(m.ownerId(), "leadership/drillmaster"));
		if (isIllager(target)) {
			d *= 1 + cfg.illagerBonus;
		}
		return (float) d;
	}

	/** A sword blow or a bolt landing, credited to the brain so kills count and the victim fights back. */
	static void hit(ServerLevel level, Merc m, LivingEntity brain, LivingEntity target, float base, boolean ranged) {
		if (friendly(target) || !target.isAlive()) {
			return;
		}
		if (ranged) {
			target.setInvulnerableTime(0); // a bolt shouldn't be swallowed by the half second after a sword hit
		}
		Cmd.damage(level, target.getUUID(), damage(m, target, base), ranged ? "minecraft:arrow" : "minecraft:mob_attack", brain.getUUID());
	}

	// ---------------------------------------------------------------- taking damage

	/**
	 * Fabric's ALLOW_DAMAGE. Bodies never take damage themselves: hits on them go to the brain. Brains can't be hurt by
	 * players, other mercenaries or villagers. Nobody friendly can be hurt by a mercenary.
	 */
	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (Mercs.isBody(entity)) {
			Merc m = Mercs.of(entity);
			if (m == null) {
				return true; // a fallen mercenary's body being laid to rest
			}
			LivingEntity brain = Mercs.brain(m);
			if (brain != null && !forwarding && entity.level() instanceof ServerLevel level && (attacker != null || source.getDirectEntity() != null)
				&& allowDamage(brain, source, amount)) {
				forwarding = true;
				try {
					brain.hurtServer(level, source, amount);
				} finally {
					forwarding = false;
				}
			}
			return false;
		}
		if (Mercs.isBrain(entity)) {
			if (Mercs.of(entity) == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			return attacker == null || !(attacker instanceof Player || Mercs.isMerc(attacker) || FRIENDS.contains(type(attacker)));
		}
		return !(attacker != null && Mercs.isMerc(attacker) && friendly(entity));
	}

	/** Fabric's AFTER_DAMAGE: show the hit on the body (the brain is invisible). */
	static void afterDamage(LivingEntity entity, DamageSource source, float taken) {
		if (taken <= 0 || !Mercs.isBrain(entity) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Merc m = Mercs.of(entity);
		LivingEntity body = m == null ? null : Mercs.body(m);
		if (body == null) {
			return;
		}
		level.broadcastDamageEvent(body, source);
		Cmd.sound(level, "minecraft:entity.player.hurt", body.getX(), body.getY() + 1, body.getZ(), 0.8f, 0.9f + Mercs.RANDOM.nextFloat() * 0.2f);
		Duty.hurt(m, source.getEntity());
	}

	/**
	 * The mixin on LivingEntity.hurtServer, for every hit on anything (so it bails out fast): a brain takes less with
	 * its owner's Shield Wall perk, and a shield-bearer blocks part of a hit from the front.
	 */
	public static float incoming(LivingEntity entity, DamageSource source, float amount) {
		if (!Mercs.isBrain(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		Merc m = Mercs.of(entity);
		if (m == null) {
			return amount;
		}
		UUID owner = m.ownerId();
		float a = amount * (float) (1 - Math.min(0.6, SkillsLink.bonus(owner, "leadership/shield_wall")));
		Rank r = m.rank();
		Vec3 from = source.getSourcePosition();
		if (r.block() > 0 && from != null) {
			Vec3 toward = from.subtract(entity.position());
			Vec3 facing = entity.getLookAngle();
			double flat = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
			if (flat > 1e-4 && (toward.x * facing.x + toward.z * facing.z) / flat > 0.2) {
				a *= (float) (1 - r.block());
				if (entity.level() instanceof ServerLevel level) {
					Cmd.sound(level, "minecraft:item.shield.block", entity.getX(), entity.getY() + 1, entity.getZ(), 0.8f, 0.9f + Mercs.RANDOM.nextFloat() * 0.2f);
				}
			}
		}
		return a;
	}
}
