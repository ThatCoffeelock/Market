package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
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

public final class FossilFoolMod implements ModInitializer {
	public static final String MOD_ID = "fossilfool";
	public static final Logger LOG = LoggerFactory.getLogger("Fossil Fool");

	/** Marks a Drill Rig's model root with the rig's id, so the model can be matched up again when it loads. */
	public static final AttachmentType<String> RIG = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "rig"), builder -> builder.persistent(Codec.STRING));

	/** Marks the moving parts of a rig's model: 1 = the drill string, 2 and up = drill head parts. */
	public static final AttachmentType<Integer> RIG_PART = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "rig_part"), builder -> builder.persistent(Codec.INT));

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		FossilConfig.load();
		Hooks.publishPrices();
		FossilFoolApi.publish();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Store.load(server);
			if (Boolean.getBoolean("fossilfool.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Rigs.shutdown();
			Store.shutdown();
			MachineMenu.closeAll();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Rigs.tick();
			Machines.tick(ticks);
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
				MachineMenu.refreshAll();
			}
			if (ticks % 1200 == 0) {
				Store.saveIfDirty();
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register(Rigs::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Rigs::onUnload);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Interactions.forget(handler.player.getUUID()));

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Interactions.use(sp, level, hand, hit);
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			// the dowsing rod also works pointed at the sky (no block hit)
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && !player.isSpectator()
				&& OilItems.isRod(player.getItemInHand(hand))) {
				return Interactions.use(sp, level, hand, null);
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return Rigs.useHitbox(sp, level, hand, entity);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
			!(world instanceof ServerLevel level) || Interactions.beforeBreak(level, player, pos, state));
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level) {
				Interactions.afterBreak(level, player, pos);
			}
		});

		CommandRegistrationCallback.EVENT.register(FossilCommands::register);
		LOG.info("Fossil Fool loaded. There's oil in them thar hills.");
	}

	static int ticks() {
		return ticks;
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
