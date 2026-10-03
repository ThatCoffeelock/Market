package com.thatcoffeelock.apocalypse;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Every Nth night the dead come in force: hordes arrive three times as often, nearly twice as big, and faster.
 * Players get a warning the morning before. Sleeping through it is technically possible. Cowardly, but possible.
 */
final class HordeNight {
	private static boolean active;
	/** Started by an admin: keeps going this many seconds even in daylight. */
	private static int forcedSeconds;
	private static long lastNight = -1;
	private static long lastWarning = -1;

	private HordeNight() {
	}

	static boolean active() {
		return active;
	}

	static void forget() {
		active = false;
		forcedSeconds = 0;
		lastNight = -1;
		lastWarning = -1;
	}

	static boolean isHordeDay(long day) {
		int every = ApocalypseConfig.get().hordeNightEvery;
		return every > 0 && day % every == 0;
	}

	/** Days until the next Horde Night, 0 = tonight. -1 = never. */
	static long daysUntil(long day) {
		int every = ApocalypseConfig.get().hordeNightEvery;
		if (every <= 0) {
			return -1;
		}
		return (every - day % every) % every;
	}

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long day = Hordes.day(level);
		boolean bright = level.isBrightOutside();
		if (forcedSeconds > 0) {
			forcedSeconds--;
		}
		if (bright && isHordeDay(day) && lastWarning != day && !active) {
			lastWarning = day;
			server.getPlayerList().broadcastSystemMessage(Component.literal("☠ Day " + day + ". ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
				.append(Component.literal("Tonight is Horde Night. Board up the windows, count your arrows, say something nice to your dog.")
					.withStyle(ChatFormatting.RED)), false);
		}
		if (!active && !bright && isHordeDay(day) && lastNight != day) {
			start(server, day);
		} else if (active && bright && forcedSeconds <= 0) {
			end(server, day);
		}
	}

	static void start(MinecraftServer server, long day) {
		active = true;
		lastNight = day;
		ServerLevel level = server.overworld();
		server.getPlayerList().broadcastSystemMessage(Component.literal("☠ HORDE NIGHT ☠ ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
			.append(Component.literal("They're coming. All of them.").withStyle(ChatFormatting.RED)), false);
		Cmd.run(level, "title @a times 10 60 20");
		Cmd.run(level, "title @a subtitle {\"text\":\"Survive until dawn\",\"color\":\"red\"}");
		Cmd.run(level, "title @a title {\"text\":\"HORDE NIGHT\",\"color\":\"dark_red\",\"bold\":true}");
		Cmd.run(level, "execute as @a at @s run playsound minecraft:event.raid.horn hostile @s ~ ~ ~ 64 0.6");
		Hordes.reboost(true);
	}

	static void end(MinecraftServer server, long day) {
		active = false;
		forcedSeconds = 0;
		server.getPlayerList().broadcastSystemMessage(Component.literal("☀ Dawn. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("Horde Night is over. If you're reading this, congratulations: you're not a zombie.")
				.withStyle(ChatFormatting.YELLOW)), false);
		Hordes.reboost(false);
	}

	/** /apocalypse admin hordenight: right now, for at least five minutes. */
	static void force(MinecraftServer server) {
		forcedSeconds = 300;
		if (!active) {
			start(server, Hordes.day(server.overworld()));
		}
	}

	static void stop(MinecraftServer server) {
		if (active) {
			end(server, Hordes.day(server.overworld()));
		}
	}
}
