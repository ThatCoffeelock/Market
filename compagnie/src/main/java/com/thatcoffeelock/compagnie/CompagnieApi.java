package com.thatcoffeelock.compagnie;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * For other mods, through Fabric's ObjectShare (no compile-time link either way):
 * <ul>
 * <li>{@code "compagnie:add_desk"}: (level, pos) makes the cartography table there a built-in Company Desk (Colonycraft's
 * Company House). Breaking a built-in desk gives nothing back.</li>
 * <li>{@code "compagnie:remove_desk"}: (level, pos) forgets it again.</li>
 * <li>{@code "compagnie:house"}: (owner, tier) the best Company House the owner has, 0 for none: +1 job slot, +1 agent
 * per tier, 10% off wages per tier.</li>
 * <li>{@code "compagnie:is_desk"}: (level, pos) is there a Company Desk?</li>
 * </ul>
 */
public final class CompagnieApi {
	public static final String ADD_DESK = "compagnie:add_desk";
	public static final String REMOVE_DESK = "compagnie:remove_desk";
	public static final String HOUSE = "compagnie:house";
	public static final String IS_DESK = "compagnie:is_desk";

	private CompagnieApi() {
	}

	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(ADD_DESK, (BiConsumer<ServerLevel, BlockPos>) (level, pos) -> Desks.add(level, pos.immutable(), true));
		share.put(REMOVE_DESK, (BiConsumer<ServerLevel, BlockPos>) Desks::remove);
		share.put(HOUSE, (BiConsumer<UUID, Integer>) (owner, tier) -> {
			Company c = Companies.get(owner);
			int t = tier == null ? 0 : Math.max(0, Math.min(3, tier));
			if (c == null && t > 0) {
				c = Companies.of(owner, "");
			}
			if (c != null && c.house != t) {
				c.house = t;
				Companies.changed();
			}
		});
		share.put(IS_DESK, (BiFunction<ServerLevel, BlockPos, Boolean>) (level, pos) -> Desks.at(level, pos) != null);
	}
}
