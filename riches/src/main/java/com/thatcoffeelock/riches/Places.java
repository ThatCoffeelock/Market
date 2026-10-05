package com.thatcoffeelock.riches;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Every vault, vault door and display case, and who trusts whom. */
final class Places {
	/** A Vault Ledger: its owner's balance piles up around it. */
	static final class Vault {
		final String dimension;
		final BlockPos pos;
		String owner = "";
		String ownerName = "";
		/** The pile height last drawn (NaN: not drawn since the server started). */
		double drawn = Double.NaN;
		final Set<UUID> wading = new HashSet<>();

		Vault(String dimension, BlockPos pos) {
			this.dimension = dimension;
			this.pos = pos;
		}
	}

	/** A Vault Door (its lower half). */
	static final class Door {
		final String dimension;
		final BlockPos pos;
		String owner = "";
		String ownerName = "";
		/** Game time it swings shut again. */
		long openUntil;

		Door(String dimension, BlockPos pos) {
			this.dimension = dimension;
			this.pos = pos;
		}
	}

	enum Kind { CASE, PEDESTAL }

	/** A Display Case or Pedestal, and what's on show in it. */
	static final class Showcase {
		final String dimension;
		final BlockPos pos;
		final Kind kind;
		String owner = "";
		String ownerName = "";
		ItemStack item = ItemStack.EMPTY;
		String shownBy = "";
		String shownOn = "";
		/** The floating item, while it's drawn. */
		@Nullable UUID display;
		float angle;
		boolean drawn;

		Showcase(String dimension, BlockPos pos, Kind kind) {
			this.dimension = dimension;
			this.pos = pos;
			this.kind = kind;
		}
	}

	static final Map<String, Vault> VAULTS = new LinkedHashMap<>();
	static final Map<String, Door> DOORS = new LinkedHashMap<>();
	static final Map<String, Showcase> SHOWCASES = new LinkedHashMap<>();
	/** Owner UUID -> the players they trust with their vaults and cases. */
	static final Map<String, Set<String>> TRUST = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private Places() {
	}

	static void start(MinecraftServer srv) {
		server = srv;
	}

	static void reset() {
		VAULTS.clear();
		DOORS.clear();
		SHOWCASES.clear();
		TRUST.clear();
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	static String dim(Level level) {
		return level.dimension().toString();
	}

	static String key(String dim, BlockPos pos) {
		return dim + "@" + pos.asLong();
	}

	static String key(Level level, BlockPos pos) {
		return key(dim(level), pos);
	}

	static @Nullable ServerLevel level(String dim) {
		if (server == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (dim(level).equals(dim)) {
				return level;
			}
		}
		return null;
	}

	/** May this player use (open, fill, empty, break) something this owner placed? */
	static boolean allowed(Player player, String owner) {
		if (owner.isEmpty() || player.isCreative()) {
			return true;
		}
		String me = player.getUUID().toString();
		if (owner.equals(me)) {
			return true;
		}
		Set<String> trusted = TRUST.get(owner);
		return trusted != null && trusted.contains(me);
	}

	static @Nullable Door doorAt(Level level, BlockPos pos) {
		Door door = DOORS.get(key(level, pos));
		return door != null ? door : DOORS.get(key(level, pos.below()));
	}
}
