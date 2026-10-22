package com.thatcoffeelock.tulpengekte;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Market prices for bulbs. They're paper underneath, and paper sells for next to nothing, so we add a price function
 * to the list at "market:price_hooks" in Fabric's ObjectShare (see the Market's PriceHooks).
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
			TulpMod.LOG.warn("{} isn't a list; bulbs won't have Market prices", PRICE_HOOKS);
		}
	}

	/** What the Market pays for one of these, in cents, or null if it isn't a bulb. */
	static Long price(ItemStack stack) {
		Variety variety = TulpItems.variety(stack);
		return variety == null ? null : cents(variety);
	}

	/** The Market price of one bulb of this variety, in cents. */
	static long cents(Variety variety) {
		return Math.max(0, Math.round(TulpConfig.get().sellPrice(variety.tier) * 100));
	}
}
