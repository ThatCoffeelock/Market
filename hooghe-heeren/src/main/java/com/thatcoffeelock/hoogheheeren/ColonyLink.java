package com.thatcoffeelock.hoogheheeren;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Colonycraft's colony network, through the ObjectShare ({@code "colonycraft:network"}): the Heeren's riders fall on
 * your convoys, and at war their armies besiege your colonies. Without Colonycraft, nothing happens.
 */
final class ColonyLink {
	private static boolean warned;

	private ColonyLink() {
	}

	@SuppressWarnings("unchecked")
	static @Nullable Object call(String op, UUID owner, Rival r, Object... more) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("colonycraft:network");
		if (!(hook instanceof BiFunction<?, ?, ?> function)) {
			return null;
		}
		Map<String, Object> args = new java.util.HashMap<>();
		args.put("owner", owner);
		args.put("by", r.House());
		for (int i = 0; i + 1 < more.length; i += 2) {
			args.put((String) more[i], more[i + 1]);
		}
		try {
			return ((BiFunction<String, Map<String, Object>, Object>) function).apply(op, args);
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				HeerenMod.LOG.warn("Colonycraft's network wouldn't answer", e);
			}
			return null;
		}
	}

	/** Their riders take a convoy on the road: what it was worth, or 0 when nothing was on the road. */
	static long raidConvoy(Court c, Rival r) {
		if (call("raid_convoy", c.uuid(), r) instanceof Map<?, ?> m) {
			long value = m.get("value") instanceof Number n ? n.longValue() : 0;
			c.chronicle(r.House() + "'s riders fell on your convoy from " + m.get("from") + " to " + m.get("to") + " and carried off "
				+ m.get("items") + " items" + (value > 0 ? " (" + Bank.format(value) + " worth)." : "."));
			return value;
		}
		return 0;
	}

	/** Their army lays siege to one of your linked colonies: its name, or null. */
	static @Nullable String besiege(Court c, Rival r, int days) {
		return call("siege", c.uuid(), r, "days", days) instanceof String name ? name : null;
	}

	/** Their sieges lift: how many. */
	static int lift(Court c, Rival r) {
		return call("lift", c.uuid(), r) instanceof Integer n ? n : 0;
	}

	/** The colonies this rival has under siege. */
	static List<String> besieged(Court c, Rival r) {
		Object got = call("besieged", c.uuid(), r);
		if (got instanceof List<?> list) {
			return list.stream().map(String::valueOf).toList();
		}
		return List.of();
	}

	/** Where a besieged colony stands (overworld only): {x, y, z}, or null. */
	static int[] siegeAt(Court c, Rival r) {
		if (!(call("colonies", c.uuid(), r) instanceof List<?> list)) {
			return null;
		}
		for (Object o : list) {
			if (o instanceof Map<?, ?> m && r.House().equals(m.get("besieged")) && String.valueOf(m.get("dimension")).contains("minecraft:overworld")
				&& m.get("x") instanceof Number x && m.get("y") instanceof Number y && m.get("z") instanceof Number z) {
				return new int[] {x.intValue(), y.intValue(), z.intValue()};
			}
		}
		return null;
	}
}
