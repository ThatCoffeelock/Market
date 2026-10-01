package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CargoTrainMod implements ModInitializer {
	public static final String MOD_ID = "cargotrain";
	public static final Logger LOG = LoggerFactory.getLogger("Cargo Train");

	/** Train state and cargo, saved on the locomotive's root entity. */
	public static final AttachmentType<TrainData> DATA = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "train"), builder -> builder.persistent(TrainData.CODEC));
	/**
	 * Marks the parts that are rebuilt every time a train loads (the wagon, its parts and the driver's seat),
	 * so strays saved during a crash get cleaned up.
	 */
	public static final AttachmentType<Boolean> MARKER = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "marker"), builder -> builder.persistent(Codec.BOOL));

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("cargotrain.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Trains.shutdown();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> Trains.tick());
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
		});

		ServerEntityEvents.ENTITY_LOAD.register(Trains::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Trains::onUnload);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> Trains.allowDamage(entity, source));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Trains.onDisconnect(handler.player));

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			InteractionResult placed = Trains.useTrainItem(sp, level, hand, hit);
			return placed != InteractionResult.PASS ? placed : Stations.useChest(sp, level, hand, hit);
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Trains.useEntity(sp, hand, entity);
		});

		CommandRegistrationCallback.EVENT.register(TrainCommands::register);
		LOG.info("Cargo Train loaded. All aboard (the cargo, anyway).");
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
