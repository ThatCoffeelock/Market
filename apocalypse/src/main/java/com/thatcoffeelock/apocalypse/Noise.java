package com.thatcoffeelock.apocalypse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The dead hunt by sound. Breaking blocks, fighting and sprinting draw nearby zombies in. Ringing a bell draws in
 * every zombie for a long way around, which is also how you herd a horde: ring a bell over there, then go over here.
 */
final class Noise {
	/** Seconds when each player last made a noise, so mining a tunnel isn't a dinner bell every block. */
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private Noise() {
	}

	static void forget() {
		LAST.clear();
	}

	static void forget(UUID player) {
		LAST.remove(player);
	}

	private static boolean cooled(ServerPlayer player, int cooldown) {
		int now = ApocalypseMod.seconds();
		Integer last = LAST.get(player.getUUID());
		if (last != null && now - last < cooldown) {
			return false;
		}
		LAST.put(player.getUUID(), now);
		return true;
	}

	private static boolean counts(ServerPlayer player) {
		return ApocalypseConfig.get().noise && !player.isCreative() && !player.isSpectator();
	}

	static void blockBroken(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (counts(player) && cooled(player, 3)) {
			Hordes.hear(level, Vec3.atCenterOf(pos), ApocalypseConfig.get().breakNoiseRadius, 15, ApocalypseMod.seconds());
		}
	}

	static void fought(ServerLevel level, ServerPlayer player) {
		if (counts(player) && cooled(player, 2)) {
			Hordes.hear(level, player.position(), ApocalypseConfig.get().fightNoiseRadius, 15, ApocalypseMod.seconds());
		}
	}

	/** Once a second: sprinting is loud. Sneaking is not. */
	static void tick(MinecraftServer server) {
		int radius = ApocalypseConfig.get().sprintNoiseRadius;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (radius > 0 && counts(player) && player.isSprinting() && cooled(player, 2)) {
				Hordes.hear((ServerLevel) player.level(), player.position(), radius, 10, ApocalypseMod.seconds());
			}
		}
	}

	/** A bell: the dinner bell. Everything dead within earshot comes to see. */
	static void bell(ServerLevel level, ServerPlayer player, BlockPos pos) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		if (!cfg.noise || cfg.bellRadius <= 0) {
			return;
		}
		int heard = Hordes.hear(level, Vec3.atCenterOf(pos), cfg.bellRadius, cfg.bellSeconds, ApocalypseMod.seconds());
		String what = heard == 0 ? "Nothing dead heard it. Probably."
			: heard == 1 ? "Something heard that. It's coming."
			: heard + " things heard that. They're all coming.";
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("♪ " + what).withStyle(heard == 0 ? ChatFormatting.GRAY : ChatFormatting.RED)));
	}
}
