package com.thatcoffeelock.fossilfool;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Optional links to the Market and Skills mods, through Fabric's ObjectShare, so none of the three needs the
 * others to compile or run.
 *
 * Market: we add a price function to the list at "market:price_hooks". The Market asks it what a stack is worth
 * before it falls back to its own price list (our buckets are paper underneath, and paper is cheap).
 *
 * Skills: it publishes "skills:wildcatting_bonus" (owner, what) -> bonus and "skills:wildcatting_xp" (owner, xp).
 * Machines keep working for owners who are offline, so both take a UUID, not a player.
 */
final class Hooks {
	static final String PRICE_HOOKS = "market:price_hooks";
	static final String BONUS = "skills:wildcatting_bonus";
	static final String XP = "skills:wildcatting_xp";

	private static boolean warned;

	private Hooks() {
	}

	@SuppressWarnings("unchecked")
	static void publishPrices() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.putIfAbsent(PRICE_HOOKS, new CopyOnWriteArrayList<Function<ItemStack, Long>>());
		Object list = share.get(PRICE_HOOKS);
		if (list instanceof List<?> hooks) {
			((List<Function<ItemStack, Long>>) hooks).add(Hooks::price);
		} else {
			FossilFoolMod.LOG.warn("{} isn't a list; oil won't have Market prices", PRICE_HOOKS);
		}
	}

	/** What the Market pays for one of these, in cents. -1: it won't buy it. null: not ours. */
	static Long price(ItemStack stack) {
		String kind = OilItems.kind(stack);
		if (kind.isEmpty()) {
			return null;
		}
		FossilConfig c = FossilConfig.get();
		return switch (kind) {
			case OilItems.CRUDE -> Math.round(c.crudeSellPrice * 100);
			case OilItems.DIESEL -> Math.round(c.dieselSellPrice * 100);
			// machines are never sold by accident: packing up a rig shouldn't end with it in the sell grid
			default -> -1L;
		};
	}

	/** A Wildcatting bonus for this player: "fuel", "speed", "refine" (fractions) or "dowse" (blocks). 0 without Skills. */
	@SuppressWarnings("unchecked")
	static double bonus(UUID player, String what) {
		Object hook = FabricLoader.getInstance().getObjectShare().get(BONUS);
		if (!(hook instanceof BiFunction<?, ?, ?> function)) {
			return 0;
		}
		try {
			Double result = ((BiFunction<UUID, String, Double>) function).apply(player, what);
			return result == null ? 0 : Math.max(0, result);
		} catch (RuntimeException e) {
			warn(e);
			return 0;
		}
	}

	/** Wildcatting XP (nothing happens without Skills). */
	@SuppressWarnings("unchecked")
	static void xp(UUID player, double amount) {
		Object hook = FabricLoader.getInstance().getObjectShare().get(XP);
		if (!(hook instanceof BiConsumer<?, ?> consumer)) {
			return;
		}
		try {
			((BiConsumer<UUID, Double>) consumer).accept(player, amount);
		} catch (RuntimeException e) {
			warn(e);
		}
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			FossilFoolMod.LOG.warn("The Skills link failed; carrying on without Wildcatting bonuses", e);
		}
	}
}
