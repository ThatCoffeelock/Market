package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Where the spoils go: every company has a strongbox at its desk. It holds {@link #MAX} stacks; anything that
 * doesn't fit is sold to the Market on the spot.
 */
final class Strongbox {
	static final int MAX = 108;
	private static final Map<String, Item> ITEMS = new HashMap<>();

	private Strongbox() {
	}

	/** An item by id ("cocoa_beans" or "minecraft:cocoa_beans"), or null if this game doesn't have it. */
	static Item item(String id) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id.contains(":") ? id : "minecraft:" + id);
		return item == null || item == Items.AIR ? null : item;
	}

	/** So many of an item (may be more than a stack: {@link #add} splits it), or empty if there's no such item. */
	static ItemStack make(String id, int count) {
		Item item = item(id);
		if (item == null || count <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		stack.setCount(count);
		return stack;
	}

	/** Puts a stack in the strongbox, topping up stacks that are already there. What doesn't fit is sold. */
	static void add(Company c, ItemStack stack) {
		int left = stack.getCount();
		int max = Math.max(1, stack.getMaxStackSize());
		for (ItemStack there : c.strongbox) {
			if (left <= 0) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < max) {
				int n = Math.min(left, max - there.getCount());
				there.grow(n);
				left -= n;
			}
		}
		long sold = 0;
		while (left > 0) {
			int n = Math.min(left, max);
			ItemStack part = stack.copyWithCount(n);
			if (c.strongbox.size() < MAX) {
				c.strongbox.add(part);
			} else {
				sold += Math.max(0, Bank.sellValue(part));
			}
			left -= n;
		}
		if (sold > 0) {
			Bank.credit(c.uuid(), c.ownerName, sold);
			c.profit += sold;
			c.news("The strongbox is full: " + Bank.format(sold) + " worth of spoils went straight to the Market.");
		}
		Companies.changed();
	}

	/** "24 Cocoa Beans, 30 Sugar". */
	static String describe(List<ItemStack> items) {
		List<String> parts = new ArrayList<>();
		for (ItemStack stack : items) {
			parts.add(stack.getCount() + " " + stack.getHoverName().getString());
		}
		return String.join(", ", parts);
	}

	static int count(Company c) {
		return c.strongbox.size();
	}
}
