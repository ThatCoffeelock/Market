package com.thatcoffeelock.overenchant;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OverenchantMod implements ModInitializer {
	public static final String MOD_ID = "overenchant";
	public static final Logger LOG = LoggerFactory.getLogger("Overenchant");

	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		OverenchantConfig.get();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("overenchant.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LATER.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ticks++;
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
		});
		CommandRegistrationCallback.EVENT.register(OverenchantCommands::register);
		LOG.info("Overenchant loaded. Sharpness X, anyone?");
	}

	/** Runs a task after the given number of server ticks. */
	static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
