package com.thatcoffeelock.flintlock;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlintlockMod implements ModInitializer {
	public static final String MOD_ID = "flintlock";
	public static final Logger LOG = LoggerFactory.getLogger("Flintlock");

	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("flintlock.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Shot.clear();
			Guns.clear();
			LATER.clear();
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> Shot.tickAll());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Guns.tick();
			ticks++;
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Guns.onDisconnect(handler.player));

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Guns.use(sp, hand);
		});

		CommandRegistrationCallback.EVENT.register(FlintlockCommands::register);
		LOG.info("Flintlock loaded. Keep your powder dry.");
	}

	/** Runs a task after the given number of server ticks. */
	public static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
