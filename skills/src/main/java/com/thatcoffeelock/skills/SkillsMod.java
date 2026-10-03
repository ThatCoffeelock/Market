package com.thatcoffeelock.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkillsMod implements ModInitializer {
	public static final String MOD_ID = "skills";
	public static final Logger LOG = LoggerFactory.getLogger("Skills");

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		SkillsConfig.load();
		Trading.publish();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SkillsStore.load(server);
			if (Boolean.getBoolean("skills.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SkillsStore.save();
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
			if (ticks % 5 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					Boosts.refresh(player);
				}
			}
			if (ticks % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					Tracker.tick(player);
				}
			}
			if (ticks % 6000 == 0) {
				Tracker.clearBabies();
			}
			if (ticks % 1200 == 0) {
				SkillsStore.saveIfDirty();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			Boosts.forget(player);
			Tracker.forget(player.getUUID());
			Skills.forget(player.getUUID());
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
				Gathering.afterBreak(level, sp, pos, state, blockEntity);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
			Fighting.afterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register(Fighting::afterDeath);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && !player.isSpectator()) {
				BlockPos pos = hit.getBlockPos();
				if (Gathering.id(level.getBlockState(pos)).equals("brewing_stand")) {
					Brewing.opened(level, pos, sp);
				}
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && !player.isSpectator() && hit == null && hand == InteractionHand.MAIN_HAND) {
				Trading.beforeTrade(sp, entity);
			}
			return InteractionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register(SkillsCommands::register);
		LOG.info("Skills loaded. Git gud.");
	}

	/** Called by BlockItemMixin for every block a player places. */
	public static void placed(ServerLevel level, BlockPos pos) {
		Placed.onPlaced(level, pos);
	}

	/** Server ticks since start. */
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
