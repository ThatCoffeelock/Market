package com.thatcoffeelock.apocalypse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Player bases: a circle in the overworld where zombies don't spawn. No hordes rise inside, and natural zombie
 * spawns are cancelled. Zombies can still walk in from outside: a base is a home, not a force field.
 * One base per player, saved in the world folder as apocalypse-bases.json.
 */
final class Bases {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type LIST = new TypeToken<List<Base>>() {
	}.getType();

	static final class Base {
		String owner;
		String name;
		int x;
		int y;
		int z;
		int radius;

		Base(UUID owner, String name, BlockPos at, int radius) {
			this.owner = owner.toString();
			this.name = name;
			this.x = at.getX();
			this.y = at.getY();
			this.z = at.getZ();
			this.radius = radius;
		}

		boolean contains(double px, double pz) {
			double dx = px - (x + 0.5);
			double dz = pz - (z + 0.5);
			return dx * dx + dz * dz <= (double) radius * radius;
		}

		BlockPos center() {
			return new BlockPos(x, y, z);
		}
	}

	private static final List<Base> BASES = new ArrayList<>();

	private Bases() {
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("apocalypse-bases.json");
	}

	static void load(MinecraftServer server) {
		BASES.clear();
		Path file = file(server);
		if (!Files.exists(file)) {
			return;
		}
		try {
			List<Base> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST);
			if (loaded != null) {
				for (Base b : loaded) {
					if (b != null && b.owner != null && b.radius > 0) {
						BASES.add(b);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			ApocalypseMod.LOG.error("Could not read {}; starting without bases (the file is kept as .broken)", file, e);
			try {
				Files.copy(file, file.resolveSibling("apocalypse-bases.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
		}
	}

	private static void save(MinecraftServer server) {
		Path file = file(server);
		try {
			Path tmp = file.resolveSibling("apocalypse-bases.json.tmp");
			Files.writeString(tmp, GSON.toJson(BASES, LIST), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			ApocalypseMod.LOG.error("Could not save {}", file, e);
		}
	}

	static void forget() {
		BASES.clear();
	}

	static List<Base> all() {
		return BASES;
	}

	static @Nullable Base of(UUID owner) {
		String id = owner.toString();
		for (Base b : BASES) {
			if (b.owner.equals(id)) {
				return b;
			}
		}
		return null;
	}

	/** Sets (or moves) someone's base. */
	static Base set(MinecraftServer server, UUID owner, String name, BlockPos at, int radius) {
		BASES.removeIf(b -> b.owner.equals(owner.toString()));
		Base base = new Base(owner, name, at, radius);
		BASES.add(base);
		save(server);
		return base;
	}

	static boolean remove(MinecraftServer server, UUID owner) {
		boolean removed = BASES.removeIf(b -> b.owner.equals(owner.toString()));
		if (removed) {
			save(server);
		}
		return removed;
	}

	/** The base covering this spot, if any. Bases only exist in the overworld. */
	static @Nullable Base at(Level level, double x, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		for (Base b : BASES) {
			if (b.contains(x, z)) {
				return b;
			}
		}
		return null;
	}

	static boolean protects(Level level, BlockPos pos) {
		return at(level, pos.getX() + 0.5, pos.getZ() + 0.5) != null;
	}

	/**
	 * A zombie just appeared. If it's an ordinary one (not a horde member that walked in, not a named or
	 * persistent one like a risen player) and it's inside a base, it never happened.
	 */
	static void onLoad(Entity entity, ServerLevel level) {
		if (BASES.isEmpty() || !(entity instanceof Mob mob) || !Undead.isZombie(mob) || mob.isPersistenceRequired()
			|| mob.hasCustomName() || mob.hasAttached(ApocalypseMod.HORDE) || at(level, mob.getX(), mob.getZ()) == null) {
			return;
		}
		// not while the level is in the middle of adding it
		ApocalypseMod.later(1, () -> {
			if (mob.isAlive() && Hordes.of(mob) == null) {
				mob.discard();
			}
		});
	}
}
