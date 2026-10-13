package com.thatcoffeelock.colonycraft;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional link to De Compagnie, through Fabric's ObjectShare: a Company House's cartography table is a built-in
 * Company Desk, and the owner's best Company House gives their company an extra job slot, more agents and cheaper
 * wages. Without De Compagnie, there are no Company Houses for sale.
 */
final class CompagnieLink {
	private static boolean warned;

	private CompagnieLink() {
	}

	private static Object share(String key) {
		return FabricLoader.getInstance().getObjectShare().get(key);
	}

	static boolean present() {
		return share("compagnie:add_desk") instanceof BiConsumer<?, ?>;
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			ColonycraftMod.LOG.warn("The De Compagnie link failed", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <A, B> void call(String key, A a, B b) {
		if (share(key) instanceof BiConsumer<?, ?> f) {
			try {
				((BiConsumer<A, B>) f).accept(a, b);
			} catch (RuntimeException e) {
				warn(e);
			}
		}
	}

	static void addDesk(ServerLevel level, BlockPos pos) {
		call("compagnie:add_desk", level, pos);
	}

	static void removeDesk(ServerLevel level, BlockPos pos) {
		call("compagnie:remove_desk", level, pos);
	}

	/** Tells De Compagnie the owner's best Company House tier (0: none). */
	static void house(UUID owner, int tier) {
		call("compagnie:house", owner, tier);
	}

	@SuppressWarnings("unchecked")
	static boolean isDesk(ServerLevel level, BlockPos pos) {
		if (share("compagnie:is_desk") instanceof BiFunction<?, ?, ?> f) {
			try {
				return Boolean.TRUE.equals(((BiFunction<ServerLevel, BlockPos, Boolean>) f).apply(level, pos));
			} catch (RuntimeException e) {
				warn(e);
			}
		}
		return false;
	}
}
