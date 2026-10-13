package com.thatcoffeelock.havana;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Market prices for tobacco and cigars. They're paper and sticks underneath, and paper and sticks sell for next to
 * nothing, so we add a price function to the list at "market:price_hooks" in Fabric's ObjectShare (see the Market's
 * PriceHooks). Neither mod needs the other to compile or run.
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
			HavanaMod.LOG.warn("{} isn't a list; tobacco won't have Market prices", PRICE_HOOKS);
		}
	}

	/** What the Market pays for one of these, in cents. -1: it won't buy it. null: not ours. */
	static Long price(ItemStack stack) {
		HavanaConfig c = HavanaConfig.get();
		return switch (HavanaItems.kind(stack)) {
			case HavanaItems.SEEDS -> cents(c.seedsSellPrice);
			case HavanaItems.LEAF -> cents(c.leafSellPrice);
			case HavanaItems.CURED -> cents(c.curedSellPrice);
			case HavanaItems.AGED -> cents(c.agedSellPrice);
			case HavanaItems.CIGAR -> cigar(stack, c);
			default -> null;
		};
	}

	private static Long cigar(ItemStack stack, HavanaConfig c) {
		// nobody buys a cigar you're still smoking
		if (HavanaItems.isLit(stack)) {
			return -1L;
		}
		double price = HavanaItems.grade(stack) == HavanaItems.Grade.AGED ? c.granReservaSellPrice : c.cigarSellPrice;
		if (HavanaItems.flavor(stack) != HavanaItems.Flavor.NONE) {
			price += c.flavorBonus;
		}
		// lit once and put out: it carries its own puff count
		if (stack.has(DataComponents.MAX_DAMAGE)) {
			int left = HavanaItems.PUFFS - stack.getDamageValue();
			price *= c.halfSmokedFactor * left / HavanaItems.PUFFS;
		}
		return cents(price);
	}

	private static long cents(double marks) {
		return Math.max(0, Math.round(marks * 100));
	}
}
