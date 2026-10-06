package com.thatcoffeelock.colonycraft;

import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Optional link to the Havana mod, through Fabric's ObjectShare ({@code havana:item}): tobacco farms grow the real
 * tobacco. Without Havana, there are no tobacco farms for sale.
 */
final class HavanaLink {
	private static boolean warned;

	private HavanaLink() {
	}

	static boolean present() {
		return FabricLoader.getInstance().getObjectShare().get("havana:item") instanceof BiFunction<?, ?, ?>;
	}

	/** Tobacco of this kind ("seeds", "leaf", "cured" or "aged"), or empty without Havana. */
	@SuppressWarnings("unchecked")
	static ItemStack tobacco(String kind, int count) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("havana:item");
		if (!(hook instanceof BiFunction<?, ?, ?> make) || count <= 0) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = ((BiFunction<String, Integer, ItemStack>) make).apply(kind, count);
			return stack == null ? ItemStack.EMPTY : stack;
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				ColonycraftMod.LOG.warn("The Havana link failed; tobacco farms grow nothing", e);
			}
			return ItemStack.EMPTY;
		}
	}
}
