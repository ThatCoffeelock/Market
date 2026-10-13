package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompagnieMod implements ModInitializer {
	public static final String MOD_ID = "compagnie";
	public static final Logger LOG = LoggerFactory.getLogger("Compagnie");

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		CompagnieApi.publish();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Companies.load(server);
			if (Boolean.getBoolean("compagnie.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Companies.stop();
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
			if (ticks % 100 == 0) {
				Companies.tick(server);
			}
			if (ticks % 1200 == 0) {
				Companies.saveIfDirty();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			Company c = Companies.get(player.getUUID());
			if (c != null && (c.unread > 0 || c.pendingTelegrams() > 0)) {
				nextTick(() -> player.sendSystemMessage(Component.literal("[Compagnie] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(c.unread + " pieces of news and " + c.pendingTelegrams()
						+ " telegrams while you were away. Visit your Company Desk, or try /compagnie news.").withStyle(ChatFormatting.YELLOW))));
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator() || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			return Desks.use(sp, level, hit);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> Desks.allowBreak(player, world, pos));
		CommandRegistrationCallback.EVENT.register(CompagnieCommands::register);
		LOG.info("De Compagnie loaded. Profit is a moral obligation.");
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
