package com.thatcoffeelock.ahoy;

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
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AhoyMod implements ModInitializer {
	public static final String MOD_ID = "ahoy";
	public static final Logger LOG = LoggerFactory.getLogger("Ahoy");

	/** Ship state, saved on the ship's root entity. */
	public static final AttachmentType<ShipData> DATA = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "ship"), builder -> builder.persistent(ShipData.CODEC));
	/** Marks seats and hitboxes, so strays (e.g. saved during a crash) get cleaned up on load. */
	public static final AttachmentType<Boolean> MARKER = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "marker"), builder -> builder.persistent(Codec.BOOL));

	private static final boolean CANNON = FabricLoader.getInstance().isModLoaded("cannon");

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("ahoy.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Ships.shutdown();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				Wind.tick(level, ticks);
			}
			Ships.tick();
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
		});

		ServerEntityEvents.ENTITY_LOAD.register(Ships::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Ships::onUnload);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> Ships.allowDamage(entity, source));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Ships.onDisconnect(handler.player));

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Ships.useBottle(sp, level, hand);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Bottle.isBottle(player.getItemInHand(hand)) ? Ships.useBottle(sp, level, hand) : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Ships.useEntity(sp, hand, entity);
		});

		CommandRegistrationCallback.EVENT.register(AhoyCommands::register);
		LOG.info("Ahoy loaded. All hands on deck.");
	}

	/** Is the Cannon mod installed? Then ships have gun ports. */
	public static boolean hasCannon() {
		return CANNON;
	}

	/** Server ticks since the mod started. */
	public static int tickCount() {
		return ticks;
	}

	public static void nextTick(Runnable task) {
		NEXT_TICK.add(task);
	}

	public static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
