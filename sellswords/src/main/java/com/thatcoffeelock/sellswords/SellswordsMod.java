package com.thatcoffeelock.sellswords;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SellswordsMod implements ModInitializer {
	public static final String MOD_ID = "sellswords";
	public static final Logger LOG = LoggerFactory.getLogger("Sellswords");

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		SellswordsConfig.load();
		SellswordsApi.publish();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Mercs.load(server);
			if (Boolean.getBoolean("sellswords.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Mercs.stop();
			Duty.clear();
			Bolt.clear();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Duty.tick(server);
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
			if (ticks % 200 == 0) {
				Mercs.saveIfDirty();
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register(Mercs::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Mercs::onUnload);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Combat::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
			Combat.afterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register(Mercs::onDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Duty.ownerLeft(handler.player));

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator() || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			return Stations.use(sp, level, hit);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> Stations.allowBreak(player, world, pos));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator() || !Mercs.isMerc(entity)) {
				return InteractionResult.PASS;
			}
			return useMerc(sp, hand, entity);
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player instanceof ServerPlayer sp && !player.isSpectator()) {
				Horn.use(sp, player.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register(SellswordsCommands::register);
		LOG.info("Sellswords loaded. Goedendag.");
	}

	/** Right-click on a mercenary: the owner (empty-handed) gets the menu. Nobody gets to trade with the brain. */
	private static InteractionResult useMerc(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		Merc m = Mercs.of(entity);
		if (m == null) {
			return InteractionResult.SUCCESS;
		}
		if (!m.owner.equals(player.getUUID().toString())) {
			bar(player, m.firstName() + " works for " + m.ownerName + ". \"Not my boss, not my problem.\"", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (!player.getMainHandItem().isEmpty()) {
			bar(player, "Empty your hand to give " + m.firstName() + " orders.", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		MercMenu.open(player, m);
		return InteractionResult.SUCCESS;
	}

	static void bar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
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
