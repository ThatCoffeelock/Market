package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Optional link to the Riches mod, through Fabric's ObjectShare ({@code riches:api}): neither mod needs the other to
 * compile or run. With Riches, a bank's vault money piles up in gold around its Vault Ledger and its vault door swings
 * shut by itself, and a museum's cases and pedestals are real Riches display cases (relics on show count towards the
 * Royal Society's collections). Without it, the bank still banks; the museum needs it.
 */
final class RichesLink {
	/** A bank vault's pool, as Riches knows it: "pool:colonycraft:<building id>". */
	static final String NAMESPACE = "colonycraft";
	private static boolean warned;

	private RichesLink() {
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Object call(String op, Object... args) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("riches:api");
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
				ColonycraftMod.LOG.warn("The Riches link failed; carrying on without it", e);
			}
			return null;
		}
	}

	static boolean present() {
		return FabricLoader.getInstance().getObjectShare().get("riches:api") instanceof BiFunction<?, ?, ?>;
	}

	/** Tells Riches how much is in each bank's vault (called once the server's up; the colonies may load later). */
	static void publishPools() {
		call("pools", "namespace", NAMESPACE, "resolver", (Function<String, Long>) Colonies::vaultOf);
	}

	static void vault(ServerLevel level, BlockPos pos, String buildingId, String name) {
		call("vault", "level", level, "pos", pos, "owner", "pool:" + NAMESPACE + ":" + buildingId, "owner_name", name);
	}

	/** A vault door that opens for anyone (an empty owner). */
	static void publicDoor(ServerLevel level, BlockPos pos) {
		call("door", "level", level, "pos", pos, "owner", "", "owner_name", "");
	}

	static void showcase(ServerLevel level, BlockPos pos, boolean pedestal, String owner, String ownerName) {
		call("showcase", "level", level, "pos", pos, "kind", pedestal ? "pedestal" : "case", "owner", owner, "owner_name", ownerName);
	}

	static void remove(ServerLevel level, BlockPos pos) {
		call("remove", "level", level, "pos", pos);
	}

	/** What Riches has at pos ("kind", "owner"), or null. For the smoke test. */
	@SuppressWarnings("unchecked")
	static @Nullable Map<String, Object> info(ServerLevel level, BlockPos pos) {
		return call("info", "level", level, "pos", pos) instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
	}

	/** Relics on show at these positions. */
	static int relics(ServerLevel level, List<BlockPos> positions) {
		return call("relics", "level", level, "positions", positions) instanceof Number n ? n.intValue() : 0;
	}

	/** Cases at these positions with anything in them. */
	static int occupied(ServerLevel level, List<BlockPos> positions) {
		return call("occupied", "level", level, "positions", positions) instanceof Number n ? n.intValue() : 0;
	}
}
