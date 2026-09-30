package com.thatcoffeelock.ahoy;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A slowly wandering wind per dimension. Sailing with it is full speed, into it is half speed.
 * The angle is the direction the wind blows towards, in the same degrees as entity yaw.
 */
final class Wind {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final Map<ResourceKey<Level>, Float> ANGLE = new HashMap<>();

	private Wind() {
	}

	static float angle(ServerLevel level) {
		return ANGLE.computeIfAbsent(level.dimension(), k -> (float) (Math.random() * 360));
	}

	/** Called every server tick: the wind shifts a little now and then. */
	static void tick(ServerLevel level, int ticks) {
		if (ticks % 100 == 0) {
			ANGLE.put(level.dimension(), Mth.wrapDegrees(angle(level) + (float) (Math.random() * 20 - 10)));
		}
	}

	/** Wind direction relative to the ship's heading, -180..180 (0 = wind straight from behind). */
	private static float relative(ServerLevel level, float heading) {
		return Mth.wrapDegrees(angle(level) - heading);
	}

	static double factor(ServerLevel level, float heading) {
		return 0.5 + 0.5 * Math.max(0, Math.cos(Math.toRadians(relative(level, heading))));
	}

	static String arrow(ServerLevel level, float heading) {
		return ARROWS[Math.floorMod(Math.round(relative(level, heading) / 45f), 8)];
	}

	static String label(ServerLevel level, float heading) {
		float rel = Math.abs(relative(level, heading));
		return rel < 45 ? "tailwind" : rel > 135 ? "headwind" : "crosswind";
	}
}
