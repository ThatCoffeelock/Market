package com.thatcoffeelock.warehouse;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** How much of an item fits somewhere, and putting it there. */
final class Slots {
	private Slots() {
	}

	/** Room for this item in the player's main inventory and hotbar (not armor or offhand). */
	static long room(ServerPlayer player, ItemStack kind) {
		Inventory inventory = player.getInventory();
		int max = kind.getMaxStackSize();
		long room = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack slot = inventory.getItem(i);
			if (slot.isEmpty()) {
				room += max;
			} else if (ItemStack.isSameItemSameComponents(slot, kind)) {
				room += Math.max(0, max - slot.getCount());
			}
		}
		return room;
	}

	/** Room for this item in a set of containers (ship holds). */
	static long room(List<Container> containers, ItemStack kind) {
		long room = 0;
		for (Container c : containers) {
			int max = Math.min(c.getMaxStackSize(), kind.getMaxStackSize());
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack slot = c.getItem(i);
				if (slot.isEmpty()) {
					if (c.canPlaceItem(i, kind)) {
						room += max;
					}
				} else if (ItemStack.isSameItemSameComponents(slot, kind)) {
					room += Math.max(0, max - slot.getCount());
				}
			}
		}
		return room;
	}

	/** Gives the player this many of the item, a stack at a time (drops what doesn't fit, which shouldn't happen). */
	static void give(ServerPlayer player, ItemStack kind, long count) {
		int max = kind.getMaxStackSize();
		while (count > 0) {
			int n = (int) Math.min(max, count);
			Parts.give(player, kind.copyWithCount(n));
			count -= n;
		}
	}

	/** Puts this many of the item into the containers, topping up stacks first. Returns how many didn't fit. */
	static long insert(List<Container> containers, ItemStack kind, long count) {
		for (int pass = 0; pass < 2 && count > 0; pass++) {
			for (Container c : containers) {
				int max = Math.min(c.getMaxStackSize(), kind.getMaxStackSize());
				boolean changed = false;
				for (int i = 0; i < c.getContainerSize() && count > 0; i++) {
					ItemStack slot = c.getItem(i);
					if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, kind) && slot.getCount() < max) {
						int n = (int) Math.min(count, max - slot.getCount());
						slot.grow(n);
						count -= n;
						changed = true;
					} else if (pass == 1 && slot.isEmpty() && c.canPlaceItem(i, kind)) {
						int n = (int) Math.min(count, max);
						c.setItem(i, kind.copyWithCount(n));
						count -= n;
						changed = true;
					}
				}
				if (changed) {
					c.setChanged();
				}
			}
		}
		return count;
	}
}
