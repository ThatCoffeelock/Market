package com.thatcoffeelock.specerij;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Market prices for spices. They're paper underneath, so we add a price function to the list at "market:price_hooks"
 * in Fabric's ObjectShare (see the Market's PriceHooks). Neither mod needs the other to compile or run.
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
			SpiceMod.LOG.warn("{} isn't a list; spices won't have Market prices", PRICE_HOOKS);
		}
	}

	/** What the Market pays for one of these, in cents, or null if it isn't ours. */
	static Long price(ItemStack stack) {
		Spice spice = SpiceItems.spice(stack);
		if (spice == null) {
			return null;
		}
		SpiceConfig c = SpiceConfig.get();
		double marks = switch (SpiceItems.kind(stack)) {
			case SpiceItems.CUTTING -> c.cuttingSellPrice;
			case SpiceItems.FRESH -> spice.freshPrice * c.priceMultiplier;
			case SpiceItems.DRIED -> spice.driedPrice * c.priceMultiplier;
			case SpiceItems.AGED -> Spice.BLACK_TEA_PRICE * c.priceMultiplier;
			default -> -1;
		};
		return marks < 0 ? null : Math.max(0, Math.round(marks * 100));
	}
}
