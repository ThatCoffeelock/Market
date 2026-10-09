package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Optional links for the guildhouse, through Fabric's ObjectShare: Sellswords ({@code sellswords:api}) makes its
 * target block a Mercenary Station where hiring is cheaper, and Fuck Illagers ({@code fuckillagers:api}) makes its
 * fletching table a Bounty Station. Neither mod needs Colonycraft (or each other) to compile or run. Without Sellswords
 * there's no guildhouse for sale; without Fuck Illagers its fletching table is just a fletching table.
 */
final class GuildLink {
	private static boolean warned;

	private GuildLink() {
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Object call(String key, String op, Object... args) {
		Object hook = FabricLoader.getInstance().getObjectShare().get(key);
		if (!(hook instanceof BiFunction<?, ?, ?> api)) {
			return null;
		}
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i + 1 < args.length; i += 2) {
			map.put((String) args[i], args[i + 1]);
		}
		try {
			return ((BiFunction<String, Map<String, Object>, Object>) api).apply(op, map);
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				ColonycraftMod.LOG.warn("The guildhouse link to {} failed; carrying on without it", key, e);
			}
			return null;
		}
	}

	static boolean sellswords() {
		return FabricLoader.getInstance().getObjectShare().get("sellswords:api") instanceof BiFunction<?, ?, ?>;
	}

	static boolean bounties() {
		return FabricLoader.getInstance().getObjectShare().get("fuckillagers:api") instanceof BiFunction<?, ?, ?>;
	}

	static void mercenaryStation(ServerLevel level, BlockPos pos, double discount, String name) {
		call("sellswords:api", "station", "level", level, "pos", pos, "discount", discount, "name", name);
	}

	static void removeMercenaryStation(ServerLevel level, BlockPos pos) {
		call("sellswords:api", "remove", "level", level, "pos", pos);
	}

	static void bountyStation(ServerLevel level, BlockPos pos) {
		call("fuckillagers:api", "station", "level", level, "pos", pos);
	}

	static void removeBountyStation(ServerLevel level, BlockPos pos) {
		call("fuckillagers:api", "remove", "level", level, "pos", pos);
	}

	/** The Mercenary Station at pos ("name", "discount", "hire_cents"), or null. For the smoke test. */
	@SuppressWarnings("unchecked")
	static @Nullable Map<String, Object> mercenaryInfo(ServerLevel level, BlockPos pos) {
		return call("sellswords:api", "info", "level", level, "pos", pos) instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
	}

	/** Is there a Bounty Station at pos? For the smoke test. */
	static boolean isBountyStation(ServerLevel level, BlockPos pos) {
		return Boolean.TRUE.equals(call("fuckillagers:api", "is_station", "level", level, "pos", pos));
	}
}
