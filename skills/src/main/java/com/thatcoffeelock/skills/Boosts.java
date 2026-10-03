package com.thatcoffeelock.skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/**
 * The bonuses that are plain vanilla attribute modifiers, so vanilla clients predict them correctly: tool speed
 * (depends on what's in your hand), luck, oxygen, swim speed, and your mount's speed and jump. Modifiers are
 * transient (never saved), and re-applied every few ticks, so they survive death, relogging and config changes.
 */
final class Boosts {
	private static final Identifier TOOL = Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "tool_speed");
	private static final Identifier LUCK = Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "luck");
	private static final Identifier OXYGEN = Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "oxygen");
	private static final Identifier SWIM = Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "swim");
	private static final Identifier MOUNT_SPEED = Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "mount_speed");
	private static final Identifier MOUNT_JUMP = Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "mount_jump");

	/** Player -> the mount we boosted, so the boost comes off when they get off. */
	private static final Map<UUID, UUID> MOUNTS = new HashMap<>();

	private Boosts() {
	}

	static void refresh(ServerPlayer player) {
		set(player, Attributes.BLOCK_BREAK_SPEED, TOOL, toolSpeed(player), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.LUCK, LUCK, Skills.perk(player, Perk.LUCKY_CHARM), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.OXYGEN_BONUS, OXYGEN, Skills.perk(player, Perk.DEEP_LUNGS), AttributeModifier.Operation.ADD_VALUE);
		// water movement efficiency runs 0..1 (Depth Strider III = 1), so level 100 is about Depth Strider I
		set(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, Skills.passive(player, Skill.SAILING), AttributeModifier.Operation.ADD_VALUE);
		mount(player);
	}

	/** Pickaxe -> Mining, axe -> Woodcutting, shovel -> Excavation. Anything else: no bonus. */
	private static double toolSpeed(ServerPlayer player) {
		String id = Skills.id(player.getMainHandItem().getItem());
		if (id.endsWith("_pickaxe")) {
			double bonus = Skills.passive(player, Skill.MINING);
			if (player.getY() < 0) {
				bonus += Skills.perk(player, Perk.DEEP_DELVER);
			}
			return bonus;
		}
		if (id.endsWith("_axe")) {
			return Skills.passive(player, Skill.WOODCUTTING);
		}
		if (id.endsWith("_shovel")) {
			return Skills.passive(player, Skill.EXCAVATION) + Skills.perk(player, Perk.MOLE);
		}
		return 0;
	}

	private static void mount(ServerPlayer player) {
		Entity vehicle = player.getVehicle();
		LivingEntity mount = vehicle instanceof LivingEntity living && !(vehicle instanceof ServerPlayer) ? living : null;
		UUID previous = MOUNTS.get(player.getUUID());
		if (previous != null && (mount == null || !mount.getUUID().equals(previous))) {
			Entity old = ((ServerLevel) player.level()).getEntity(previous);
			if (old instanceof LivingEntity living) {
				clearMount(living);
			}
			MOUNTS.remove(player.getUUID());
		}
		if (mount != null) {
			set(mount, Attributes.MOVEMENT_SPEED, MOUNT_SPEED, Skills.passive(player, Skill.HORSERIDING), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			set(mount, Attributes.JUMP_STRENGTH, MOUNT_JUMP, Skills.perk(player, Perk.JUMPER), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			MOUNTS.put(player.getUUID(), mount.getUUID());
		}
	}

	private static void clearMount(LivingEntity mount) {
		set(mount, Attributes.MOVEMENT_SPEED, MOUNT_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(mount, Attributes.JUMP_STRENGTH, MOUNT_JUMP, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** Called when a player leaves: their horse shouldn't keep the boost. */
	static void forget(ServerPlayer player) {
		UUID previous = MOUNTS.remove(player.getUUID());
		if (previous != null && ((ServerLevel) player.level()).getEntity(previous) instanceof LivingEntity living) {
			clearMount(living);
		}
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
