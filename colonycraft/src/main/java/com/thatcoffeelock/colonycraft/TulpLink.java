package com.thatcoffeelock.colonycraft;

import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Optional link to the Tulpengekte mod, through Fabric's ObjectShare ({@code tulpengekte:item}): farmers dig up the
 * odd tulip bulb. Without Tulpengekte, farms grow what they always did.
 */
final class TulpLink {
	private static boolean warned;

	private TulpLink() {
	}

	static boolean present() {
		return FabricLoader.getInstance().getObjectShare().get("tulpengekte:item") instanceof BiFunction<?, ?, ?>;
	}

	/** Bulbs of this kind (a tier like "common" or "fine", or a variety id), or empty without Tulpengekte. */
	@SuppressWarnings("unchecked")
	static ItemStack bulbs(String kind, int count) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("tulpengekte:item");
		if (!(hook instanceof BiFunction<?, ?, ?> make) || count <= 0) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = ((BiFunction<String, Integer, ItemStack>) make).apply(kind, count);
			return stack == null ? ItemStack.EMPTY : stack;
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				ColonycraftMod.LOG.warn("The Tulpengekte link failed; farms dig up no bulbs", e);
			}
			return ItemStack.EMPTY;
		}
	}
}
