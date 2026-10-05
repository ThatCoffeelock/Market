package com.thatcoffeelock.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarketMod implements ModInitializer {
	public static final String MOD_ID = "market";
	public static final Logger LOG = LoggerFactory.getLogger("Market");

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static @Nullable MinecraftServer server;
	private static int ticks;

	@Override
	public void onInitialize() {
		MarketConfig.load();
		PriceHooks.publish();

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			PriceBook.reload();
			MarketData.load(s);
			if (Boolean.getBoolean("market.smokeTest")) {
				SmokeTest.run(s);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			MarketData.save();
			NEXT_TICK.clear();
			LATER.clear();
			server = null;
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s, resources, success) -> PriceBook.reload());
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			Runnable task;
			while ((task = NEXT_TICK.poll()) != null) {
				task.run();
			}
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
			if (++ticks % 600 == 0) {
				MarketData.saveIfDirty();
			}
		});

		CommandRegistrationCallback.EVENT.register(MarketCommands::register);
		MarketInteractions.register();
		LOG.info("Market loaded. Buy low, sell high.");
	}

	/** Runs a task at the end of the current server tick (used to switch GUIs safely). */
	public static void nextTick(Runnable task) {
		NEXT_TICK.add(task);
	}

	/** Runs a task after the given number of server ticks. */
	public static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}
}
