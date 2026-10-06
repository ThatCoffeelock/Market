package com.thatcoffeelock.burlapsack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BurlapSackMod implements ModInitializer {
	public static final String MOD_ID = "burlapsack";
	public static final Logger LOG = LoggerFactory.getLogger("Burlap Sack");
	/** ObjectShare key: a {@code Supplier<ItemStack>} of a fresh empty sack. */
	public static final String EMPTY_SACK = "burlapsack:empty";

	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("burlapsack.smokeTest")) {
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

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Sacks.useEntity(sp, hand, entity);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Sacks.useBlock(sp, level, hand, hit);
		});

		CommandRegistrationCallback.EVENT.register(SackCommands::register);
		// Colonycraft hires villagers out of full sacks and hands the empty sack back (no compile-time link either way)
		FabricLoader.getInstance().getObjectShare().put(EMPTY_SACK, (Supplier<ItemStack>) SackItems::empty);
		LOG.info("Burlap Sack loaded. Lock your doors, villagers.");
	}

	/** Runs a task after the given number of server ticks. */
	public static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
