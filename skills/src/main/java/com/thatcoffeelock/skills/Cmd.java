package com.thatcoffeelock.skills;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs vanilla commands as the server. Sounds, particles and summons go through commands because their text format
 * is far more stable between Minecraft versions than the Java methods.
 */
final class Cmd {
	private Cmd() {
	}

	static void run(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	static String f(double v) {
		return String.format(Locale.ROOT, "%.3f", v);
	}

	static String block(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	/** A sound only this player hears, at their position. */
	static void sound(ServerPlayer player, String sound, float volume, float pitch) {
		run((ServerLevel) player.level(), "playsound " + sound + " player " + player.getUUID() + " "
			+ f(player.getX()) + " " + f(player.getY()) + " " + f(player.getZ()) + " " + f(volume) + " " + f(pitch));
	}

	/** A sound everyone nearby hears. */
	static void sound(ServerLevel level, String sound, double x, double y, double z, float volume, float pitch) {
		run(level, "playsound " + sound + " player @a[x=" + f(x) + ",y=" + f(y) + ",z=" + f(z) + ",distance=..24] "
			+ f(x) + " " + f(y) + " " + f(z) + " " + f(volume) + " " + f(pitch));
	}

	static void particles(ServerLevel level, String particle, double x, double y, double z, double spread, int count) {
		run(level, "particle " + particle + " " + f(x) + " " + f(y) + " " + f(z) + " " + f(spread) + " " + f(spread) + " "
			+ f(spread) + " 0.02 " + count);
	}

	static void setblock(ServerLevel level, BlockPos pos, String state) {
		run(level, "setblock " + block(pos) + " " + state);
	}
}
