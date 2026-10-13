package com.thatcoffeelock.proost;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProostMod implements ModInitializer {
	public static final String MOD_ID = "proost";
	public static final Logger LOG = LoggerFactory.getLogger("Proost");
	/**
	 * ObjectShare key: a {@code BiFunction<String, Integer, ItemStack>} making Proost items by name ("mash:grain",
	 * "genever", "genever:aged"...), for Colonycraft's distillery and tavern.
	 */
	public static final String ITEM_HOOK = "proost:item";

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		ProostConfig.load();
		// Colonycraft distills and pours the real thing (no compile-time link either way)
		FabricLoader.getInstance().getObjectShare().put(ITEM_HOOK, (BiFunction<String, Integer, ItemStack>) ProostMod::item);
		// and the Market pays a fair price for it
		Prices.publish();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ProostStore.load(server);
			if (Boolean.getBoolean("proost.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ProostStore.save();
			Drinking.reset();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
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
			if (ticks % 20 == 0) {
				Drinking.soberUp(20);
			}
			if (ticks % 40 == 0) {
				Drinking.ambient(server);
			}
			if (ticks % 100 == 0) {
				Barrels.scan(server);
			}
			if (ticks % 1200 == 0) {
				ProostStore.saveIfDirty();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Drinking.onDisconnect(handler.player));

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			InteractionResult result = Still.use(sp, level, hand, hit);
			if (result == InteractionResult.PASS) {
				result = Barrels.useBarrel(sp, level, hand, hit);
			}
			return result;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Drinking.use(sp, hand);
		});

		CommandRegistrationCallback.EVENT.register(ProostCommands::register);
		LOG.info("Proost loaded. Gezondheid!");
	}

	/** Server ticks since start. */
	static int ticks() {
		return ticks;
	}

	static ItemStack item(String what, Integer count) {
		return ProostItems.byName(what, count == null ? 1 : Math.max(1, count));
	}

	/** Runs a task at the end of the current server tick. */
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
