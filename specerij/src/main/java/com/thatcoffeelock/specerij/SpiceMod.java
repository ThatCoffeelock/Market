package com.thatcoffeelock.specerij;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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

public final class SpiceMod implements ModInitializer {
	public static final String MOD_ID = "specerij";
	public static final Logger LOG = LoggerFactory.getLogger("Specerij");
	/** ObjectShare key: a {@code BiFunction<String, Integer, ItemStack>} making spice items ("dried:pepper", "fresh:tea", "cutting:cloves", "aged:tea"). */
	public static final String ITEM_HOOK = "specerij:item";
	/** ObjectShare key: a {@code Function<String, String>} from a biome id ("minecraft:jungle") to the spice that grows there ("pepper"), or null. */
	public static final String BIOME_HOOK = "specerij:spice_for_biome";

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		SpiceConfig.load();
		// Colonycraft's spice plantations grow the real thing (no compile-time link either way)
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(ITEM_HOOK, (BiFunction<String, Integer, ItemStack>) SpiceItems::byKind);
		share.put(BIOME_HOOK, (Function<String, String>) biome -> {
			Spice spice = biome == null ? null : Spice.forBiome(biome);
			return spice == null ? null : spice.id();
		});
		Prices.publish();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SpiceStore.load(server);
			if (Boolean.getBoolean("specerij.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SpiceStore.save();
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
				Gardens.scan(server);
			}
			if (ticks % 100 == 0) {
				Drying.scan(server);
			}
			if (ticks % 1200 == 0) {
				SpiceStore.saveIfDirty();
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			InteractionResult result = Gardens.useBoneMeal(sp, level, hand, hit);
			if (result == InteractionResult.PASS) {
				result = Gardens.usePlant(sp, level, hand, hit);
			}
			if (result == InteractionResult.PASS) {
				result = Gardens.useCutting(sp, level, hand, hit);
			}
			if (result == InteractionResult.PASS) {
				result = Drying.useRack(sp, level, hand, hit);
			}
			return result;
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
			!(world instanceof ServerLevel level) || Gardens.beforeBreak(level, player, pos, state));
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level) {
				Gardens.afterBreak(level, player, pos, state);
			}
		});

		CommandRegistrationCallback.EVENT.register(SpiceCommands::register);
		LOG.info("Specerij loaded. The Spice Islands are that way.");
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
