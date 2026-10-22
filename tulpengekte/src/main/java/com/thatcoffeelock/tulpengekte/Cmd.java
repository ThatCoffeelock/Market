package com.thatcoffeelock.tulpengekte;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Runs vanilla commands as the server. Block placement, summoning, sounds and particles go through commands
 * because their text format is far more stable between Minecraft versions than the Java methods.
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
		return String.format(Locale.ROOT, "%.4f", v);
	}

	static String pos(double x, double y, double z) {
		return f(x) + " " + f(y) + " " + f(z);
	}

	static String block(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	static String uuidNbt(UUID uuid) {
		int[] a = UUIDUtil.uuidToIntArray(uuid);
		return "UUID:[I;" + a[0] + "," + a[1] + "," + a[2] + "," + a[3] + "]";
	}

	static void setblock(ServerLevel level, BlockPos pos, String state) {
		run(level, "setblock " + block(pos) + " " + state);
	}

	static void sound(ServerLevel level, String sound, double x, double y, double z, float volume, float pitch) {
		run(level, "playsound " + sound + " player @a[x=" + f(x) + ",y=" + f(y) + ",z=" + f(z) + ",distance=..32] "
			+ pos(x, y, z) + " " + f(volume) + " " + f(pitch));
	}

	static void particles(ServerLevel level, String particle, double x, double y, double z, double spread, double speed, int count) {
		run(level, "particle " + particle + " " + pos(x, y, z) + " " + f(spread) + " " + f(spread) + " " + f(spread) + " " + f(speed) + " " + count);
	}
}
