package com.thatcoffeelock.flintlock;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * What the enchantments on a gun do. Guns borrow the bow and crossbow enchantments (see data/minecraft/tags/item/enchantable);
 * Unbreaking and Mending were always vanilla's job.
 * <ul>
 * <li><b>Power</b>: +15% damage per level.</li>
 * <li><b>Punch</b>: +50% knockback per level (a shot still never shoves harder than {@link Gun#MAX_KNOCKBACK}).</li>
 * <li><b>Flame</b>: whatever a ball hits burns for 5 seconds.</li>
 * <li><b>Quick Charge</b>: reloading takes 15% less time per level, down to a quarter of the time.</li>
 * <li><b>Piercing</b>: a ball carries on through this many extra targets.</li>
 * <li><b>Multishot</b>: one shot fires three, in a fan.</li>
 * <li><b>Infinity</b>: reloading doesn't use up ammo (you still need one in your inventory).</li>
 * </ul>
 */
record GunEnchants(int power, int punch, int flame, int quickCharge, int piercing, int multishot, int infinity) {
	static final GunEnchants NONE = new GunEnchants(0, 0, 0, 0, 0, 0, 0);

	static final double POWER_PER_LEVEL = 0.15;
	static final double PUNCH_PER_LEVEL = 0.5;
	static final double QUICK_CHARGE_PER_LEVEL = 0.15;
	static final double MIN_RELOAD = 0.25;
	static final float FLAME_SECONDS = 5f;
	/** Angle between the balls of a Multishot fan, in degrees. */
	static final double MULTISHOT_DEGREES = 10;

	static GunEnchants of(ItemStack stack) {
		ItemEnchantments all = stack.get(DataComponents.ENCHANTMENTS);
		if (all == null || all.isEmpty()) {
			return NONE;
		}
		int power = 0;
		int punch = 0;
		int flame = 0;
		int quickCharge = 0;
		int piercing = 0;
		int multishot = 0;
		int infinity = 0;
		for (Holder<Enchantment> enchantment : all.keySet()) {
			int level = all.getLevel(enchantment);
			if (enchantment.is(Enchantments.POWER)) {
				power = level;
			} else if (enchantment.is(Enchantments.PUNCH)) {
				punch = level;
			} else if (enchantment.is(Enchantments.FLAME)) {
				flame = level;
			} else if (enchantment.is(Enchantments.QUICK_CHARGE)) {
				quickCharge = level;
			} else if (enchantment.is(Enchantments.PIERCING)) {
				piercing = level;
			} else if (enchantment.is(Enchantments.MULTISHOT)) {
				multishot = level;
			} else if (enchantment.is(Enchantments.INFINITY)) {
				infinity = level;
			}
		}
		return new GunEnchants(power, punch, flame, quickCharge, piercing, multishot, infinity);
	}

	double damageMultiplier() {
		return 1.0 + POWER_PER_LEVEL * power;
	}

	double knockbackMultiplier() {
		return 1.0 + PUNCH_PER_LEVEL * punch;
	}

	/** How long reloading takes for this gun, in ticks. */
	int reloadTicks(Gun gun) {
		double factor = Math.max(MIN_RELOAD, 1.0 - QUICK_CHARGE_PER_LEVEL * quickCharge);
		return Math.max(4, (int) Math.round(gun.reloadTicks * factor));
	}

	/** How many fans of balls one shot fires: 3 with Multishot, otherwise 1. */
	int fan() {
		return multishot > 0 ? 3 : 1;
	}

	boolean any() {
		return this != NONE && (power > 0 || punch > 0 || flame > 0 || quickCharge > 0 || piercing > 0 || multishot > 0 || infinity > 0);
	}
}
