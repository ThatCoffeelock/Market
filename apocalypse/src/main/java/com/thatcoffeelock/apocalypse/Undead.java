package com.thatcoffeelock.apocalypse;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/** Small helpers for the walking dead. */
final class Undead {
	/** The shambling kind. Zombified piglins mind their own business, so they're not invited. */
	private static final Set<String> ZOMBIES = Set.of("minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager");

	static final Identifier HORDE_SPEED = ApocalypseMod.id("horde_speed");
	static final Identifier HORDE_NIGHT_SPEED = ApocalypseMod.id("horde_night_speed");
	static final Identifier HORDE_RANGE = ApocalypseMod.id("horde_range");

	private Undead() {
	}

	static String typeId(Entity entity) {
		return EntityType.getKey(entity.getType()).toString();
	}

	static boolean isZombie(@Nullable Entity entity) {
		return entity instanceof Mob && entity.isAlive() && ZOMBIES.contains(typeId(entity));
	}

	/** Spawns a fresh zombie (or anything else) standing at pos. Not persistent: it despawns like any monster. */
	@Nullable
	static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos, float yaw) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		if (entity instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		}
		level.addFreshEntityWithPassengers(entity);
		return entity;
	}

	/**
	 * Would vanilla spawn this here at night? The same checks the natural spawner makes: block light 0,
	 * a dark enough sky, a floor monsters can stand on, and room to stand. Light up your base and they stay out.
	 */
	static boolean allowedAt(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
		if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)
			|| !SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, level.getRandom())) {
			return false;
		}
		Mob probe = type.create(level, EntitySpawnReason.NATURAL);
		if (probe == null) {
			return false;
		}
		probe.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		boolean ok = probe.checkSpawnRules(level, EntitySpawnReason.NATURAL) && probe.checkSpawnObstruction(level);
		probe.discard();
		return ok;
	}

	/** Paths a mob somewhere. Returns false if it can't find a way. */
	static boolean walkTo(Mob mob, double x, double y, double z, double speed) {
		return mob.getNavigation().moveTo(x, y, z, speed);
	}

	/** Paths a mob to within a block of a spot: for solid targets (a lantern) it can't stand inside. */
	static boolean walkNear(Mob mob, double x, double y, double z, double speed) {
		return mob.getNavigation().moveTo(x, y, z, 1, speed);
	}

	static boolean busy(Mob mob) {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive();
	}

	/** Horde members are a bit faster and keep track of you from further away. */
	static void boost(Mob mob, boolean hordeNight) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		set(mob, Attributes.MOVEMENT_SPEED, HORDE_SPEED, cfg.hordeSpeedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(mob, Attributes.MOVEMENT_SPEED, HORDE_NIGHT_SPEED, hordeNight ? cfg.hordeNightSpeedBonus : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		double extra = range == null ? 0 : Math.max(0, cfg.hordeFollowRange - range.getBaseValue());
		set(mob, Attributes.FOLLOW_RANGE, HORDE_RANGE, extra, AttributeModifier.Operation.ADD_VALUE);
	}

	static void unboost(Mob mob) {
		set(mob, Attributes.MOVEMENT_SPEED, HORDE_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(mob, Attributes.MOVEMENT_SPEED, HORDE_NIGHT_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(mob, Attributes.FOLLOW_RANGE, HORDE_RANGE, 0, AttributeModifier.Operation.ADD_VALUE);
	}

	static boolean boosted(Mob mob) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.getModifier(HORDE_SPEED) != null;
	}

	private static void set(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		@Nullable AttributeModifier current = instance.getModifier(id);
		if (amount == 0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || current.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}
}
