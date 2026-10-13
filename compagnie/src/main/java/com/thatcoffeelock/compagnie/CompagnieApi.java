package com.thatcoffeelock.compagnie;

import java.util.function.BiConsumer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * For other mods, through Fabric's ObjectShare (no compile-time link either way):
 * <ul>
 * <li>{@code "compagnie:add_desk"}: (level, pos) makes the cartography table there a built-in Company Desk (Colonycraft's
 * Company House). Breaking a built-in desk gives nothing back.</li>
 * <li>{@code "compagnie:remove_desk"}: (level, pos) forgets it again.</li>
 * </ul>
 */
public final class CompagnieApi {
	public static final String ADD_DESK = "compagnie:add_desk";
	public static final String REMOVE_DESK = "compagnie:remove_desk";

	private CompagnieApi() {
	}

	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(ADD_DESK, (BiConsumer<ServerLevel, BlockPos>) (level, pos) -> Desks.add(level, pos.immutable(), true));
		share.put(REMOVE_DESK, (BiConsumer<ServerLevel, BlockPos>) Desks::remove);
	}
}
