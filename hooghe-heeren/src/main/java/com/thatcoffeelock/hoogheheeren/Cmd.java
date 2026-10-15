package com.thatcoffeelock.hoogheheeren;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Runs vanilla commands as the server. Building seats, summoning mannequins, dealing damage, boss bars, sounds and
 * particles go through commands, because their text format is far more stable between Minecraft versions than the
 * Java methods.
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

	static String block(int x, int y, int z) {
		return x + " " + y + " " + z;
	}

	/** NBT int-array form of a UUID, so we can pick the UUID of what we summon and find it again. */
	static String uuidNbt(UUID uuid) {
		int[] a = UUIDUtil.uuidToIntArray(uuid);
		return "UUID:[I;" + a[0] + "," + a[1] + "," + a[2] + "," + a[3] + "]";
	}

	/** "minecraft:the_nether" from a level's dimension key ("ResourceKey[minecraft:dimension / minecraft:the_nether]"). */
	static String dimId(Level level) {
		String s = level.dimension().toString();
		int slash = s.lastIndexOf(" / ");
		int end = s.lastIndexOf(']');
		return slash >= 0 && end > slash ? s.substring(slash + 3, end).trim() : "minecraft:overworld";
	}

	/** Damage dealt by {@code by}, so kills are credited to it. */
	static void damage(ServerLevel level, UUID target, float amount, UUID by) {
		run(level, "damage " + target + " " + f(amount) + " minecraft:mob_attack by " + by);
	}

	static void sound(ServerLevel level, String sound, double x, double y, double z, float volume, float pitch) {
		run(level, "playsound " + sound + " neutral @a[x=" + f(x) + ",y=" + f(y) + ",z=" + f(z) + ",distance=..64] "
			+ pos(x, y, z) + " " + f(volume) + " " + f(pitch));
	}

	static void particles(ServerLevel level, String particle, double x, double y, double z, double spread, double speed, int count) {
		run(level, "particle " + particle + " " + pos(x, y, z) + " " + f(spread) + " " + f(spread) + " " + f(spread) + " " + f(speed) + " " + count);
	}
}
