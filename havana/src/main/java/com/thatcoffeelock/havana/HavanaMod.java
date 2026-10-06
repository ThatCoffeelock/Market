package com.thatcoffeelock.havana;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HavanaMod implements ModInitializer {
	public static final String MOD_ID = "havana";
	public static final Logger LOG = LoggerFactory.getLogger("Havana");
	/** ObjectShare key: a {@code BiFunction<String, Integer, ItemStack>} making tobacco ("seeds", "leaf", "cured", "aged"). */
	public static final String ITEM_HOOK = "havana:item";

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		// Colonycraft's tobacco farms grow the real thing (no compile-time link either way)
		FabricLoader.getInstance().getObjectShare().put(ITEM_HOOK, (BiFunction<String, Integer, ItemStack>) HavanaMod::tobacco);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			HavanaStore.load(server);
			if (Boolean.getBoolean("havana.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HavanaStore.save();
			Smoking.reset();
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
			if (ticks % 10 == 0) {
				Smoking.ambient(server);
			}
			if (ticks % 40 == 0) {
				Crops.scan(server);
			}
			if (ticks % 100 == 0) {
				Curing.scan(server);
			}
			if (ticks % 1200 == 0) {
				HavanaStore.saveIfDirty();
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register(Crops::onEntityLoad);

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			InteractionResult result = Crops.useSeeds(sp, level, hand, hit);
			if (result == InteractionResult.PASS) {
				result = Smoking.lightOnBlock(sp, level, hand, hit);
			}
			if (result == InteractionResult.PASS) {
				result = Rolling.useTable(sp, level, hand, hit);
			}
			if (result == InteractionResult.PASS) {
				result = Curing.useBarrel(sp, level, hand, hit);
			}
			return result;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Smoking.use(sp, hand);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
			!(world instanceof ServerLevel level) || Crops.beforeBreak(level, player, pos, state));
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level) {
				Crops.afterBreak(level, player, pos, state);
			}
		});

		CommandRegistrationCallback.EVENT.register(HavanaCommands::register);
		LOG.info("Havana loaded. Light 'em if you've got 'em.");
	}

	/** Server ticks since start. */
	static int ticks() {
		return ticks;
	}

	static ItemStack tobacco(String kind, Integer count) {
		int n = count == null ? 1 : Math.max(1, count);
		if (kind == null) {
			return ItemStack.EMPTY;
		}
		return switch (kind) {
			case HavanaItems.SEEDS -> HavanaItems.seeds(n);
			case HavanaItems.LEAF -> HavanaItems.leaf(n);
			case HavanaItems.CURED -> HavanaItems.cured(n);
			case HavanaItems.AGED -> HavanaItems.aged(n);
			default -> ItemStack.EMPTY;
		};
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
