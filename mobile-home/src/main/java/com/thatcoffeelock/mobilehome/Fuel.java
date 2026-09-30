package com.thatcoffeelock.mobilehome;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import org.jetbrains.annotations.Nullable;

/**
 * Anything a furnace burns, the vehicle burns. Since 26.x that's whatever carries the cooking fuel
 * component, so datapack and modded fuels work too.
 */
final class Fuel {
	private static @Nullable Method burnTime;

	private Fuel() {
	}

	static int burnTicks(ServerLevel level, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		for (TypedDataComponent<?> component : stack.getComponents()) {
			if (component.value() instanceof CookingFuel fuel) {
				return burnTime(fuel);
			}
		}
		return 0;
	}

	/** The component's burn time, read through its record accessor (the only int it exposes). */
	private static int burnTime(CookingFuel fuel) {
		try {
			if (burnTime == null) {
				for (Method method : CookingFuel.class.getMethods()) {
					if (method.getParameterCount() == 0 && method.getReturnType() == int.class && !Modifier.isStatic(method.getModifiers())
						&& !method.getName().equals("hashCode")) {
						burnTime = method;
						MobileHomeMod.LOG.info("Reading fuel burn time from CookingFuel.{}()", method.getName());
						break;
					}
				}
			}
			return burnTime == null ? 0 : Math.max(0, (int) burnTime.invoke(fuel));
		} catch (ReflectiveOperationException e) {
			MobileHomeMod.LOG.error("Could not read fuel burn time", e);
			return 0;
		}
	}
}
