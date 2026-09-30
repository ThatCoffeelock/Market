package com.thatcoffeelock.mobilehome;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Anything a furnace burns, the vehicle burns. Since 26.x that's whatever carries the cooking fuel
 * component, so datapack and modded fuels work too.
 */
final class Fuel {
	private static final Set<String> WARNED = new HashSet<>();

	private Fuel() {
	}

	static int burnTicks(ServerLevel level, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
		if (fuel == null) {
			return 0;
		}
		ResolvableInt burn = fuel.burnTime();
		if (burn instanceof ResolvableInt.Constant constant) {
			return Math.max(0, constant.value());
		}
		try {
			// context-dependent fuels (scoreboards, storage...) have nothing to read from here; most still resolve to their fallback
			return Math.max(0, burn.get(null, 0));
		} catch (RuntimeException e) {
			if (WARNED.add(burn.toString())) {
				MobileHomeMod.LOG.warn("Can't work out how long {} burns ({}); the vehicle won't take it", stack, burn);
			}
			return 0;
		}
	}
}
