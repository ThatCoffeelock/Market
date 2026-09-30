package com.thatcoffeelock.mobilehome;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Anything a furnace burns, the vehicle burns. Since 26.x that's whatever carries the cooking fuel
 * component, so datapack and modded fuels work too.
 */
final class Fuel {
	private static final Set<String> WARNED = new HashSet<>();

	private Fuel() {
	}

	/** An empty loot context: fuel providers only read optional values from it. */
	private static LootContext context(ServerLevel level) {
		LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
		return new LootContext.Builder(params).create(Optional.empty());
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
			if (burn instanceof ResolvableInt.Reference reference) {
				// vanilla fuels point at a data-driven provider, e.g. minecraft:cooking/time_coal
				ResourceKey<ContextIntProvider> key = reference.key();
				ContextIntProvider provider = level.getServer().reloadableRegistries().lookup().lookupOrThrow(key.registryKey()).getOrThrow(key).value();
				return Math.max(0, provider.getIntUnsafe(context(level)));
			}
			return Math.max(0, burn.get(context(level), 0));
		} catch (RuntimeException e) {
			if (WARNED.add(burn.toString())) {
				MobileHomeMod.LOG.warn("Can't work out how long {} burns ({}); the vehicle won't take it", stack, burn, e);
			}
			return 0;
		}
	}
}
