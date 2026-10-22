package com.thatcoffeelock.terraincognita;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Which level is which: Terra Incognita, and home (the Overworld). */
final class Terra {
	static final String DIMENSION = "terra_incognita:terra";
	static final String HOME = "minecraft:overworld";
	/** What counts as an illager for the raiders and the bounty. */
	static final Set<String> ILLAGERS = Set.of("minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner",
		"minecraft:ravager");

	private Terra() {
	}

	static boolean isTerra(Level level) {
		return level.dimension().toString().contains(DIMENSION);
	}

	static boolean isHome(Level level) {
		return level.dimension().toString().contains(HOME);
	}

	/** Terra Incognita, or null if the dimension didn't load (a datapack conflict, say). */
	static @Nullable ServerLevel terra(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (isTerra(level)) {
				return level;
			}
		}
		return null;
	}

	/** Where a chart takes you from here: Terra Incognita from home, home from Terra Incognita, nowhere from anywhere else. */
	static @Nullable ServerLevel across(ServerLevel from) {
		if (isHome(from)) {
			return terra(from.getServer());
		}
		return isTerra(from) ? from.getServer().overworld() : null;
	}

	/** The id to use in "execute in". */
	static String id(ServerLevel level) {
		return isTerra(level) ? DIMENSION : HOME;
	}

	static String typeId(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	static boolean isIllager(Entity entity) {
		return ILLAGERS.contains(typeId(entity));
	}

	static String typeId(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}
}
