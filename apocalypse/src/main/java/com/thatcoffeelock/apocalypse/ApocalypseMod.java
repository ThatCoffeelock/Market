package com.thatcoffeelock.apocalypse;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApocalypseMod implements ModInitializer {
	public static final String MOD_ID = "apocalypse";
	public static final Logger LOG = LoggerFactory.getLogger("Apocalypse");

	/** Marks horde zombies, so they find a horde again after their chunk reloads. */
	public static final AttachmentType<Boolean> HORDE = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "horde"), builder -> builder.persistent(Codec.BOOL));
	/** Seconds since a player was bitten. Gone when cured, and it doesn't survive death. */
	public static final AttachmentType<Integer> INFECTION = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "infection"), builder -> builder.persistent(Codec.INT));

	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;
	private static int seconds;

	@Override
	public void onInitialize() {
		ApocalypseConfig.load();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("apocalypse.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LATER.clear();
			Hordes.forget();
			HordeNight.forget();
			Doors.forget();
			Noise.forget();
			Lights.forget();
		});
		ServerTickEvents.END_SERVER_TICK.register(ApocalypseMod::tick);

		ServerEntityEvents.ENTITY_LOAD.register(Hordes::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Hordes.onUnload(entity));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			Infection.afterDamage(entity, source, damageTaken);
			if (source.getEntity() instanceof ServerPlayer attacker && entity.level() instanceof ServerLevel level) {
				Noise.fought(level, attacker);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Infection::afterDeath);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
				Noise.blockBroken(level, sp, pos);
			}
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && !player.isSpectator()) {
				BlockPos pos = hit.getBlockPos();
				if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().equals("bell")) {
					Noise.bell(level, sp, pos);
				}
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Noise.forget(handler.player.getUUID()));
		CommandRegistrationCallback.EVENT.register(ApocalypseCommands::register);
		LOG.info("Zombie Apocalypse loaded. Aim for the head.");
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (!LATER.isEmpty()) {
			List<Delayed> due = new ArrayList<>();
			LATER.removeIf(d -> d.at <= ticks && due.add(d));
			due.forEach(d -> d.task.run());
		}
		if (ticks % 20 != 0) {
			return;
		}
		seconds++;
		HordeNight.tick(server);
		for (ServerLevel level : server.getAllLevels()) {
			Hordes.tick(level, seconds);
		}
		Hordes.spawnTick(server);
		Doors.tick(seconds);
		Lights.tickLoose(server, seconds);
		Infection.tick(server);
		Noise.tick(server);
	}

	static int seconds() {
		return seconds;
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
