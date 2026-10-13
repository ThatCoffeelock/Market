package com.thatcoffeelock.colonycraft;

import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Optional link to the Proost mod, through Fabric's ObjectShare ({@code proost:item}): distilleries make real
 * genever, whisky, rum and brandy, and taverns pour it. Bottles are read straight off the item's custom data.
 * Without Proost, there are no distilleries or taverns for sale.
 */
final class ProostLink {
	private static boolean warned;

	private ProostLink() {
	}

	static boolean present() {
		return FabricLoader.getInstance().getObjectShare().get("proost:item") instanceof BiFunction<?, ?, ?>;
	}

	/** A Proost item by name ("genever", "genever:aged", "mash:grain", "tun", "cask"), or empty without Proost. */
	@SuppressWarnings("unchecked")
	static ItemStack item(String what, int count) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("proost:item");
		if (!(hook instanceof BiFunction<?, ?, ?> make) || count <= 0) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = ((BiFunction<String, Integer, ItemStack>) make).apply(what, count);
			return stack == null ? ItemStack.EMPTY : stack;
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				ColonycraftMod.LOG.warn("The Proost link failed; distilleries make nothing", e);
			}
			return ItemStack.EMPTY;
		}
	}

	/** A full, sealed bottle of Proost spirit (an opened one carries a swig counter and doesn't count). */
	static boolean isSealedBottle(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && "spirit".equals(data.copyTag().getStringOr("proost", "")) && !stack.has(DataComponents.MAX_DAMAGE);
	}
}
