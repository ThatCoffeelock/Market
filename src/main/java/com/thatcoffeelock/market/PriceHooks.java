package com.thatcoffeelock.market;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Prices for other mods' items. Mods like Fossil Fool hand out vanilla items with custom data (a bucket of crude is
 * paper underneath), which the price list can't tell apart. They add a function to the list at "market:price_hooks"
 * in Fabric's ObjectShare: stack -> sell price of one item in cents, -1 if the market shouldn't buy it, or null if
 * it isn't theirs. Neither side needs the other to compile or run.
 */
public final class PriceHooks {
	public static final String KEY = "market:price_hooks";

	private PriceHooks() {
	}

	/** Makes sure the list exists, so mods that load before or after us all add to the same one. */
	static void publish() {
		FabricLoader.getInstance().getObjectShare().putIfAbsent(KEY, new CopyOnWriteArrayList<Function<ItemStack, Long>>());
	}

	/** What another mod says one of this item sells for, in cents (-1 = not sellable), or null if no mod claims it. */
	@SuppressWarnings("unchecked")
	public static @Nullable Long unitPrice(ItemStack stack) {
		Object hooks = FabricLoader.getInstance().getObjectShare().get(KEY);
		if (!(hooks instanceof List<?> list) || list.isEmpty()) {
			return null;
		}
		for (Object hook : list) {
			if (!(hook instanceof Function<?, ?> function)) {
				continue;
			}
			try {
				Long price = ((Function<ItemStack, Long>) function).apply(stack);
				if (price != null) {
					return price;
				}
			} catch (RuntimeException e) {
				MarketMod.LOG.warn("A price hook from another mod failed", e);
			}
		}
		return null;
	}
}
