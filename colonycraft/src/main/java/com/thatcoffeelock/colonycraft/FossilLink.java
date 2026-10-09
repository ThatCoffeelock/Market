package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Optional link to the Fossil Fool mod, through Fabric's ObjectShare ({@code fossilfool:api}): neither mod needs the
 * other to compile or run. A fuel depot's cauldrons are Fossil Fool Tanks (crude on the left, diesel on the right),
 * its blast furnace is a Refinery and the copper rods behind the tanks are a pipe manifold.
 */
final class FossilLink {
	private static boolean warned;

	private FossilLink() {
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Object call(String op, Object... args) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("fossilfool:api");
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
				ColonycraftMod.LOG.warn("The Fossil Fool link failed; carrying on without it", e);
			}
			return null;
		}
	}

	static boolean present() {
		return FabricLoader.getInstance().getObjectShare().get("fossilfool:api") instanceof BiFunction<?, ?, ?>;
	}

	static void tank(ServerLevel level, BlockPos pos, String fluid) {
		call("tank", "level", level, "pos", pos, "set", fluid);
	}

	static void refinery(ServerLevel level, BlockPos pos, String owner) {
		call("refinery", "level", level, "pos", pos, "owner", owner);
	}

	static void pipe(ServerLevel level, BlockPos pos) {
		call("pipe", "level", level, "pos", pos);
	}

	static void remove(ServerLevel level, BlockPos pos) {
		call("remove", "level", level, "pos", pos);
	}

	/** What Fossil Fool has at pos ("kind", and for a tank "set", "fluid", "amount"), or null. For the smoke test. */
	@SuppressWarnings("unchecked")
	static @Nullable Map<String, Object> info(ServerLevel level, BlockPos pos) {
		return call("info", "level", level, "pos", pos) instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
	}

	/** Buckets in the tanks at these positions, all together. */
	static long buckets(ServerLevel level, List<BlockPos> positions) {
		return call("buckets", "level", level, "positions", positions) instanceof Number n ? n.longValue() : 0;
	}
}
