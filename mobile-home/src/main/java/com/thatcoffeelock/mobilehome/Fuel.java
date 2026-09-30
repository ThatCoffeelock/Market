package com.thatcoffeelock.mobilehome;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Anything a furnace burns, the vehicle burns. Uses the game's own fuel list, so modded fuels work too. */
final class Fuel {
	private Fuel() {
	}

	static int burnTicks(ServerLevel level, ItemStack stack) {
		return stack.isEmpty() ? 0 : level.fuelValues().burnDuration(stack);
	}
}
