package com.thatcoffeelock.specerij;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.world.item.ItemStack;

/**
 * A Colonycraft Spice Plantation's day, worked out here so the balance lives with the spices. Colonycraft asks
 * through {@link SpiceMod#PLANTATION_HOOK} with the spice, the tier, the workers and how far from home the colony is.
 *
 * Every spice is worth about the same per worker: a planter brings in about ₥4 of fresh spice a day (5 peppercorns,
 * a saffron crocus and a bit), times the tier bonus (1, 1.5, 2) and the distance factor (1 at home, up to 2 far away).
 * Tier 2's drying shed dries half of it (about four times the value), tier 3 dries all of it and ages a third of the
 * tea black. Now and then a cutting comes in too.
 */
final class Plantation {
	/** Marks of fresh spice per planter per day, before the tier and distance. */
	static final double MARKS_PER_WORKER = 4;

	private Plantation() {
	}

	/** Hook entry: args "spice" (id), "tier", "workers", "distance" (the factor, 1 to 2). */
	static List<ItemStack> day(Map<String, Object> args) {
		Spice spice = args.get("spice") instanceof String id ? Spice.byId(id) : null;
		int tier = args.get("tier") instanceof Number n ? n.intValue() : 1;
		int workers = args.get("workers") instanceof Number n ? n.intValue() : 0;
		double distance = args.get("distance") instanceof Number n ? n.doubleValue() : 1;
		return spice == null ? List.of() : day(spice, tier, workers, distance, new Random());
	}

	static List<ItemStack> day(Spice spice, int tier, int workers, double distance, Random random) {
		List<ItemStack> out = new ArrayList<>();
		if (workers <= 0) {
			return out;
		}
		double bonus = tier <= 1 ? 1.0 : tier == 2 ? 1.5 : 2.0;
		double marks = MARKS_PER_WORKER * workers * bonus * Math.max(1, Math.min(2, distance)) * (0.8 + random.nextDouble() * 0.4);
		int total = Math.max(1, (int) Math.round(marks / spice.freshPrice));
		int dried = tier >= 3 ? total : tier == 2 ? total / 2 : 0;
		int black = tier >= 3 && spice == Spice.TEA ? dried / 3 : 0;
		add(out, SpiceItems.FRESH, spice, total - dried);
		add(out, SpiceItems.DRIED, spice, dried - black);
		add(out, SpiceItems.AGED, spice, black);
		if (random.nextInt(8) == 0) {
			out.add(SpiceItems.cutting(spice, 1));
		}
		return out;
	}

	private static void add(List<ItemStack> out, String kind, Spice spice, int count) {
		while (count > 0) {
			int n = Math.min(count, 64);
			out.add(SpiceItems.of(kind, spice, n));
			count -= n;
		}
	}
}
