package com.thatcoffeelock.riches;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RichesMod implements ModInitializer {
	public static final String MOD_ID = "riches";
	public static final Logger LOG = LoggerFactory.getLogger("Riches");
	/** The Market's shared price-hook list (see the Market's PriceHooks). */
	static final String PRICE_HOOKS = "market:price_hooks";

	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		RichesConfig.load();
		publishPrices();
		RichesApi.publish();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Store.load(server);
			if (Boolean.getBoolean("riches.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Store.shutdown();
			LATER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ticks++;
			Vaults.tick(ticks);
			Showcases.tick(ticks);
			if (ticks % 200 == 0) {
				Interactions.validate();
			}
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
			if (ticks % 1200 == 0) {
				Store.saveIfDirty();
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
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
				Relics.mined(level, sp, pos, state);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Relics.killed(entity, source.getEntity()));

		CommandRegistrationCallback.EVENT.register(RichesCommands::register);
		LOG.info("Riches loaded. Money can't buy happiness, but it can buy a display case.");
	}

	/** Relics are priceless: the Market won't take them, so nobody sells the Dragon's Tooth for ₥0.20 by accident. */
	@SuppressWarnings("unchecked")
	private static void publishPrices() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.putIfAbsent(PRICE_HOOKS, new CopyOnWriteArrayList<Function<ItemStack, Long>>());
		if (share.get(PRICE_HOOKS) instanceof List<?> hooks) {
			((List<Function<ItemStack, Long>>) hooks).add(RichesMod::price);
		}
	}

	static Long price(ItemStack stack) {
		return RichesItems.kind(stack).isEmpty() ? null : -1L;
	}

	/** Runs a task after the given number of server ticks. */
	static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
