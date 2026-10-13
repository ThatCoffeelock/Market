package com.thatcoffeelock.proost;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Market prices for mash and bottles. They're paper underneath, and paper sells for next to nothing, so we add a
 * price function to the list at "market:price_hooks" in Fabric's ObjectShare (see the Market's PriceHooks).
 * Neither mod needs the other to compile or run.
 */
final class Prices {
	static final String PRICE_HOOKS = "market:price_hooks";

	private Prices() {
	}

	@SuppressWarnings("unchecked")
	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.putIfAbsent(PRICE_HOOKS, new CopyOnWriteArrayList<Function<ItemStack, Long>>());
		if (share.get(PRICE_HOOKS) instanceof List<?> hooks) {
			((List<Function<ItemStack, Long>>) hooks).add(Prices::price);
		} else {
			ProostMod.LOG.warn("{} isn't a list; Proost won't have Market prices", PRICE_HOOKS);
		}
	}

	/** What the Market pays for one of these, in cents. null: not ours. */
	static Long price(ItemStack stack) {
		ProostConfig c = ProostConfig.get();
		if (ProostItems.isMash(stack)) {
			return cents(c.mashSellPrice);
		}
		if (!ProostItems.isSpirit(stack)) {
			return null;
		}
		double price = c.price(ProostItems.drink(stack), ProostItems.isAged(stack));
		if (ProostItems.isOpen(stack)) {
			int left = ProostItems.SWIGS - stack.getDamageValue();
			price *= c.openedFactor * left / ProostItems.SWIGS;
		}
		return cents(price);
	}

	private static long cents(double marks) {
		return Math.max(0, Math.round(marks * 100));
	}
}
