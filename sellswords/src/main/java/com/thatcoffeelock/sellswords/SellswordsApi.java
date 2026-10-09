package com.thatcoffeelock.sellswords;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods (Colonycraft's Guildhouse uses it), through Fabric's ObjectShare as {@code sellswords:api}: a
 * {@code BiFunction<String, Map<String, Object>, Object>}, so nobody needs Sellswords to compile. Operations:
 * <ul>
 * <li>{@code station} (level, pos, discount: a fraction off the hiring price, name): a Mercenary Station built into
 * something at pos (the block, a target, is the caller's). Registering again updates the discount and name.</li>
 * <li>{@code remove} (level, pos): forgets the station at pos. Its idle mercenaries guard the spot instead.</li>
 * <li>{@code info} (level, pos): the station at pos as a map ("name", "discount", "hire_cents"), or null.</li>
 * <li>{@code is_mercenary} (entity): true for a mercenary's brain or body.</li>
 * </ul>
 * Vehicle mods seat mercenaries through the list {@code sellswords:board} instead; see {@link Boarding}.
 */
public final class SellswordsApi {
	static final String KEY = "sellswords:api";

	private SellswordsApi() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().putIfAbsent(KEY, (BiFunction<String, Map<String, Object>, Object>) SellswordsApi::call);
		Boarding.publish();
	}

	static @Nullable Object call(String op, Map<String, Object> args) {
		if (op.equals("is_mercenary")) {
			return args.get("entity") instanceof Entity e && Mercs.isMerc(e);
		}
		if (!(args.get("level") instanceof ServerLevel level) || !(args.get("pos") instanceof BlockPos pos)) {
			return null;
		}
		switch (op) {
			case "station" -> {
				double discount = args.get("discount") instanceof Number n ? n.doubleValue() : 0;
				String name = args.get("name") instanceof String s && !s.isBlank() ? s : "Mercenary Station";
				Station existing = Stations.at(level, pos);
				if (existing != null) {
					existing.discount = Math.max(0, Math.min(0.9, discount));
					existing.name = name;
					existing.builtIn = true;
					Mercs.changed();
				} else {
					Stations.add(level, pos.immutable(), discount, name, true);
				}
				return true;
			}
			case "remove" -> {
				Stations.remove(level, pos);
				return true;
			}
			case "info" -> {
				Station s = Stations.at(level, pos);
				if (s == null) {
					return null;
				}
				Map<String, Object> out = new HashMap<>();
				out.put("name", s.name);
				out.put("discount", s.discount);
				out.put("hire_cents", Stations.hireCost(s));
				return out;
			}
			default -> {
				return null;
			}
		}
	}
}
