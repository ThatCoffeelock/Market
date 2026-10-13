package com.thatcoffeelock.compagnie;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Runs vanilla commands as the server: sounds and particles, whose text format is stabler than the Java methods. */
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

	/** "minecraft:the_nether" from a level's dimension key ("ResourceKey[minecraft:dimension / minecraft:the_nether]"). */
	static String dimId(Level level) {
		String s = level.dimension().toString();
		int slash = s.lastIndexOf(" / ");
		int end = s.lastIndexOf(']');
		return slash >= 0 && end > slash ? s.substring(slash + 3, end).trim() : "minecraft:overworld";
	}

	static void sound(ServerLevel level, String sound, double x, double y, double z, float volume, float pitch) {
		run(level, "playsound " + sound + " neutral @a[x=" + f(x) + ",y=" + f(y) + ",z=" + f(z) + ",distance=..32] "
			+ pos(x, y, z) + " " + f(volume) + " " + f(pitch));
	}
}
