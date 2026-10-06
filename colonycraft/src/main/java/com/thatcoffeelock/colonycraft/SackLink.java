package com.thatcoffeelock.colonycraft;

import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Optional link to the Burlap Sack mod: a villager you bring in a sack can take an empty job in the colony, for free.
 * The sack's data is read straight off the item; the empty sack comes back through Fabric's ObjectShare
 * ({@code burlapsack:empty}).
 */
final class SackLink {
	private SackLink() {
	}

	/** A sack with a grown-up villager inside (not a wandering trader, not a baby). */
	static boolean isVillagerSack(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		CompoundTag tag = data.copyTag();
		return "full".equals(tag.getStringOr("burlapsack", "")) && "minecraft:villager".equals(tag.getStringOr("type", ""))
			&& tag.getCompoundOrEmpty("captive").getIntOr("Age", 0) >= 0;
	}

	/** Who's in the sack. */
	static String name(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		String name = data == null ? "" : data.copyTag().getStringOr("name", "");
		return name.isBlank() ? "Someone" : name;
	}

	/** The inventory slot of the first sack with a villager in it, or -1. */
	static int find(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isVillagerSack(inventory.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	/** A fresh empty sack to hand back, or null if Burlap Sack isn't there to make one. */
	@SuppressWarnings("unchecked")
	static @Nullable ItemStack emptySack() {
		Object hook = FabricLoader.getInstance().getObjectShare().get("burlapsack:empty");
		if (hook instanceof Supplier<?> supplier) {
			try {
				return ((Supplier<ItemStack>) supplier).get();
			} catch (RuntimeException e) {
				ColonycraftMod.LOG.warn("Couldn't make an empty Burlap Sack", e);
			}
		}
		return null;
	}
}
