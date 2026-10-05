package com.thatcoffeelock.fuckillagers;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional link to the Skills mod, through Fabric's ObjectShare: neither mod needs the other to compile or run.
 * Without Skills every bonus is 0 and XP goes nowhere.
 */
final class SkillsLink {
	private static boolean warned;

	private SkillsLink() {
	}

	/** A skill bonus: "artillery/passive" or "artillery/powder_monkey" (a fraction, or 0). */
	@SuppressWarnings("unchecked")
	static double bonus(UUID player, String key) {
		if (player == null) {
			return 0;
		}
		Object hook = FabricLoader.getInstance().getObjectShare().get("skills:bonus");
		if (!(hook instanceof BiFunction<?, ?, ?> function)) {
			return 0;
		}
		try {
			Double value = ((BiFunction<UUID, String, Double>) function).apply(player, key);
			return value == null ? 0 : Math.max(0, value);
		} catch (RuntimeException e) {
			warn(e);
			return 0;
		}
	}

	/** Rolls a chance bonus: true with probability bonus(player, key). */
	static boolean roll(UUID player, String key) {
		double chance = bonus(player, key);
		return chance > 0 && Math.random() < chance;
	}

	/** Skill XP, e.g. xp(player, "artillery", 6). */
	@SuppressWarnings("unchecked")
	static void xp(UUID player, String skill, double amount) {
		if (player == null || amount <= 0) {
			return;
		}
		Object hook = FabricLoader.getInstance().getObjectShare().get("skills:xp");
		if (!(hook instanceof BiConsumer<?, ?> consumer)) {
			return;
		}
		try {
			((BiConsumer<UUID, Map.Entry<String, Double>>) consumer).accept(player, Map.entry(skill, amount));
		} catch (RuntimeException e) {
			warn(e);
		}
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			FuckIllagersMod.LOG.warn("The Skills link failed; carrying on without skill bonuses", e);
		}
	}
}
