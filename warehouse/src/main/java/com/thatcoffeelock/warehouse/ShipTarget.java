package com.thatcoffeelock.warehouse;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.world.Container;

/**
 * A ship moored at a Loading Dock, without Warehouse having to know what a ship is (Ahoy fills this in, see AhoyLink).
 *
 * @param present the ship is still there, close enough to the dock
 * @param mayUse  the player looking at the screen may open its cargo
 */
record ShipTarget(String name, List<Container> holds, BooleanSupplier present, BooleanSupplier mayUse) {
	boolean usable() {
		return present.getAsBoolean() && mayUse.getAsBoolean();
	}
}
