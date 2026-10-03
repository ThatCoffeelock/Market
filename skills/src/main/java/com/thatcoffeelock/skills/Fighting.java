package com.thatcoffeelock.skills;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Combat, Marksmanship and the damage-related perks of other skills (Horseriding, Sailing, Blacksmithing). */
public final class Fighting {
	/** Damage modifications done, for the smoke test (proves the LivingEntity mixin is live). */
	static int calls;

	private Fighting() {
	}

	private static boolean ranged(ServerPlayer attacker, DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION) || source.getDirectEntity() != attacker;
	}

	/** Called from LivingEntityMixin before damage is applied. Bonuses add up, reductions are capped at 60%. */
	public static float modify(LivingEntity victim, DamageSource source, float amount) {
		calls++;
		if (amount <= 0) {
			return amount;
		}
		float result = amount;
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
			double bonus;
			if (ranged(attacker, source)) {
				bonus = Skills.passive(attacker, Skill.MARKSMANSHIP) + Skills.perk(attacker, Perk.EAGLE_EYE);
				if (attacker.distanceTo(victim) >= 20) {
					bonus += Skills.perk(attacker, Perk.LONG_SHOT);
				}
			} else {
				bonus = Skills.passive(attacker, Skill.COMBAT) + Skills.perk(attacker, Perk.BRUTE);
				if (victim.getHealth() < victim.getMaxHealth() * 0.3f) {
					bonus += Skills.perk(attacker, Perk.EXECUTIONER);
				}
				if (attacker.getVehicle() instanceof LivingEntity) {
					bonus += Skills.perk(attacker, Perk.CAVALRY);
				}
			}
			result *= (float) (1.0 + bonus);
		}

		double reduction = 0;
		if (victim instanceof ServerPlayer player) {
			if (source.getEntity() instanceof Mob) {
				reduction += Skills.perk(player, Perk.THICK_SKIN);
			}
			if (source.is(DamageTypeTags.IS_DROWNING)) {
				reduction += Skills.perk(player, Perk.SEA_LEGS);
			}
			if (armorPieces(player) >= 3) {
				reduction += Skills.perk(player, Perk.ARMORER);
			}
		} else if (rider(victim) instanceof ServerPlayer rider) {
			reduction += Skills.perk(rider, Perk.BONDED);
		}
		if (reduction > 0) {
			result *= (float) (1.0 - Math.min(0.6, reduction));
		}
		return result;
	}

	private static Entity rider(LivingEntity mount) {
		List<Entity> passengers = mount.getPassengers();
		return passengers.isEmpty() ? null : passengers.get(0);
	}

	private static int armorPieces(ServerPlayer player) {
		int count = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/** After damage landed: XP for the attacker, and Recovery's arrow refund. */
	static void afterDamage(LivingEntity victim, DamageSource source, float damageTaken) {
		if (damageTaken <= 0 || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
			return;
		}
		// only real fights count: mobs and other players, not armor stands
		if (!(victim instanceof Mob) && !(victim instanceof ServerPlayer)) {
			return;
		}
		double dealt = Math.min(damageTaken, victim.getMaxHealth());
		if (ranged(attacker, source)) {
			double distance = Math.min(48.0, attacker.distanceTo(victim));
			Skills.award(attacker, Skill.MARKSMANSHIP, dealt * 1.5 * (1.0 + distance / 48.0));
			recover(attacker, source);
		} else {
			Skills.award(attacker, Skill.COMBAT, dealt);
		}
	}

	/** Recovery: a chance to get a plain or spectral arrow back. Never with Infinity, that'd be free arrows. */
	private static void recover(ServerPlayer attacker, DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct == null || direct == attacker) {
			return;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
		if (!type.equals("arrow") && !type.equals("spectral_arrow")) {
			return;
		}
		if (hasInfinity(attacker.getMainHandItem()) || hasInfinity(attacker.getOffhandItem())) {
			return;
		}
		if (Skills.roll(Skills.perk(attacker, Perk.RECOVERY))) {
			Skills.give(attacker, new ItemStack(Skills.item(type)));
		}
	}

	private static boolean hasInfinity(ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null) {
			return false;
		}
		for (var entry : enchantments.entrySet()) {
			if (entry.getKey().getRegisteredName().endsWith("infinity")) {
				return true;
			}
		}
		return false;
	}

	/** Butcher: animals you kill can drop their loot twice. The loot is copied right after it spawns. */
	static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || victim.getType().getCategory() != MobCategory.CREATURE) {
			return;
		}
		if (!(victim.level() instanceof ServerLevel level) || !Skills.roll(Skills.perk(killer, Perk.BUTCHER))) {
			return;
		}
		Vec3 at = victim.position();
		SkillsMod.nextTick(() -> {
			AABB box = new AABB(at.x - 2, at.y - 1, at.z - 2, at.x + 2, at.y + 3, at.z + 2);
			for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.tickCount <= 3)) {
				ItemEntity copy = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), drop.getItem().copy());
				level.addFreshEntity(copy);
			}
			Cmd.particles(level, "minecraft:happy_villager", at.x, at.y + 0.5, at.z, 0.3, 6);
		});
	}
}
