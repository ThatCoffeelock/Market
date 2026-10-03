package com.thatcoffeelock.warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WarehouseMod implements ModInitializer {
	public static final String MOD_ID = "warehouse";
	public static final Logger LOG = LoggerFactory.getLogger("Warehouse");

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;
	private static boolean ahoy;
	private static boolean train;

	@Override
	public void onInitialize() {
		WarehouseConfig.load();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Warehouses.load(server);
			if (Boolean.getBoolean("warehouse.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Warehouses.shutdown();
			Prompts.clear();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Warehouses.tick();
			Runnable task;
			while ((task = NEXT_TICK.poll()) != null) {
				task.run();
			}
			ticks++;
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Interactions.use(sp, level, hand, hit);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
			!(world instanceof ServerLevel level) || Interactions.beforeBreak(level, player, pos, state));
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> Prompts.onChat(sender, message.signedContent()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Prompts.forget(handler.player.getUUID()));
		CommandRegistrationCallback.EVENT.register(WarehouseCommands::register);

		// Ships and trains are optional: only touch their mods when they're installed.
		if (FabricLoader.getInstance().isModLoaded("ahoy")) {
			try {
				AhoyLink.init();
				ahoy = true;
			} catch (LinkageError e) {
				LOG.warn("This Ahoy is too old for Loading Docks; update Ahoy to 1.1.0 or newer", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("cargotrain")) {
			try {
				TrainLink.init();
				train = true;
			} catch (LinkageError e) {
				LOG.warn("This Cargo Train is too old to unload into warehouses; update it to 1.1.0 or newer", e);
			}
		}
		LOG.info("Warehouse loaded. Stack it high{}{}.", ahoy ? ", ships welcome" : "", train ? ", trains welcome" : "");
	}

	/** Is Ahoy installed (and new enough for Loading Docks)? */
	static boolean ahoy() {
		return ahoy;
	}

	static boolean train() {
		return train;
	}

	/** Runs a task at the end of the current server tick (used to switch screens safely). */
	public static void nextTick(Runnable task) {
		NEXT_TICK.add(task);
	}

	/** Runs a task after the given number of server ticks. */
	public static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
