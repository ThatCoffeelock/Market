package com.thatcoffeelock.blimey;

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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlimeyMod implements ModInitializer {
	public static final String MOD_ID = "blimey";
	public static final Logger LOG = LoggerFactory.getLogger("Blimey");

	/** Airship state, saved on the airship's root entity. */
	public static final AttachmentType<AirshipData> DATA = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "airship"), builder -> builder.persistent(AirshipData.CODEC));
	/** Marks seats and hitboxes, so strays (e.g. saved during a crash) get cleaned up on load. */
	public static final AttachmentType<Boolean> MARKER = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "marker"), builder -> builder.persistent(Codec.BOOL));
	/** Marks falling bombs, which aren't saved: one found on load is a leftover and gets removed. */
	public static final AttachmentType<Boolean> BOMB = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "bomb"), builder -> builder.persistent(Codec.BOOL));

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		BlimeyConfig.load();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("blimey.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Airships.shutdown();
			Bomb.clear();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			Airships.tick();
			Bomb.tickAll();
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

		ServerEntityEvents.ENTITY_LOAD.register(Airships::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Airships::onUnload);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> Airships.allowDamage(entity, source));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Airships.onDisconnect(handler.player));

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Airships.useItem(sp, level, hand);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Airships.useBlock(sp, level, hand, hit);
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Airships.useEntity(sp, hand, entity);
		});

		CommandRegistrationCallback.EVENT.register(BlimeyCommands::register);
		Airships.offerSeatsToMercenaries();
		LOG.info("Blimey loaded. Mind your heads, and the diesel bill.");
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
