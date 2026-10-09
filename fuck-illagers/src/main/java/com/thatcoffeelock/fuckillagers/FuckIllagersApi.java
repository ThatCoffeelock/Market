package com.thatcoffeelock.fuckillagers;

import java.util.Map;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods (Colonycraft's guildhouse uses it), through Fabric's ObjectShare as {@code fuckillagers:api}: a
 * {@code BiFunction<String, Map<String, Object>, Object>}, so nobody needs Fuck Illagers to compile. Operations:
 * <ul>
 * <li>{@code station} (level, pos): the fletching table at pos is a Bounty Station from now on.</li>
 * <li>{@code remove} (level, pos): it isn't any more (the block is the caller's business).</li>
 * <li>{@code is_station} (level, pos): is it one?</li>
 * </ul>
 */
public final class FuckIllagersApi {
	static final String KEY = "fuckillagers:api";

	private FuckIllagersApi() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().putIfAbsent(KEY, (BiFunction<String, Map<String, Object>, Object>) FuckIllagersApi::call);
	}

	static @Nullable Object call(String op, Map<String, Object> args) {
		if (!(args.get("level") instanceof ServerLevel level) || !(args.get("pos") instanceof BlockPos pos)) {
			return null;
		}
		return switch (op) {
			case "station" -> {
				Bounties.addStation(level, pos.immutable());
				yield true;
			}
			case "remove" -> {
				Bounties.removeStation(level, pos);
				yield true;
			}
			case "is_station" -> Bounties.isStation(level, pos);
			default -> null;
		};
	}
}
