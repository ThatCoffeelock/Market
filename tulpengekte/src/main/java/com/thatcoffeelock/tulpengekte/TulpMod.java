package com.thatcoffeelock.tulpengekte;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TulpMod implements ModInitializer {
	public static final String MOD_ID = "tulpengekte";
	public static final Logger LOG = LoggerFactory.getLogger("Tulpengekte");
	/**
	 * ObjectShare key: a {@code BiFunction<String, Integer, ItemStack>} making bulbs. The kind is a variety id
	 * ("semper_augustus") or a tier ("common", "fine", "rare": a random variety of that tier). Colonycraft's farms use it.
	 */
	public static final String ITEM_HOOK = "tulpengekte:item";

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		TulpConfig.load();
		FabricLoader.getInstance().getObjectShare().put(ITEM_HOOK, (BiFunction<String, Integer, ItemStack>) TulpItems::byKind);
		Prices.publish();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TulpStore.load(server);
			if (Boolean.getBoolean("tulpengekte.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TulpStore.save();
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
			if (ticks % 40 == 0) {
				Beds.scan(server);
			}
			if (ticks % 1200 == 0) {
				TulpStore.saveIfDirty();
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			InteractionResult result = Beds.useBoneMeal(sp, level, hand, hit);
			if (result == InteractionResult.PASS) {
				result = Beds.useBulb(sp, level, hand, hit);
			}
			return result;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Bloemist.useEntity(sp, hand, entity);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
			!(world instanceof ServerLevel level) || Beds.beforeBreak(level, player, pos, state));

		CommandRegistrationCallback.EVENT.register(TulpCommands::register);
		LOG.info("Tulpengekte loaded. Never pay a house for an onion.");
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
