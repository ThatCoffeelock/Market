package com.thatcoffeelock.warehouse;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One warehouse: a core, the racks touching it, and a tally of what's inside. Items aren't kept in slots, they're
 * counted: "cobblestone: 48,213". Items with different data (an enchanted sword, a named one, a damaged one) are
 * counted separately, so nothing loses its enchantments in storage.
 */
final class Warehouse {
	/** An item kind: the item plus all its data, count ignored. */
	static final class Key {
		final ItemStack stack;

		private Key(ItemStack stack) {
			this.stack = stack;
		}

		static Key of(ItemStack stack) {
			return new Key(stack.copyWithCount(1));
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key other && ItemStack.isSameItemSameComponents(stack, other.stack);
		}

		@Override
		public int hashCode() {
			return stack.getItem().hashCode();
		}
	}

	final String id;
	String name;
	String owner;
	String ownerName;
	/** Locked: anyone can bring stuff in, only the owner can take it out. */
	boolean locked;
	/** What it accepts. ALL, or one category (a specialist warehouse). */
	Category filter = Category.ALL;
	String dimension;
	BlockPos pos;
	/** Picked up: the stock waits inside the core item until it's placed again. */
	boolean packed;
	/** The central warehouse this one is a branch of ("" if none). A central warehouse can open its branches from anywhere. */
	String central = "";
	/** A branch that sends everything it gets on to its central warehouse. */
	boolean forward;
	final Map<Key, Long> items = new LinkedHashMap<>();
	private long total;

	/** Racks counted for this warehouse right now (worked out by {@link Warehouses}). */
	int racks;
	/** Racks touching this warehouse that also touch another one, so they count for neither. */
	int disputed;
	/** Goes up on every change, so open screens know to redraw. */
	int version;

	Warehouse(String id, String name, String owner, String ownerName, String dimension, BlockPos pos) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.dimension = dimension;
		this.pos = pos;
	}

	long total() {
		return total;
	}

	int kinds() {
		return items.size();
	}

	long capacity() {
		WarehouseConfig config = WarehouseConfig.get();
		return config.coreCapacity + (long) racks * config.rackCapacity;
	}

	long space() {
		return Math.max(0, capacity() - total);
	}

	long count(Key key) {
		return items.getOrDefault(key, 0L);
	}

	boolean accepts(ItemStack stack) {
		return !stack.isEmpty() && filter.takes(stack);
	}

	/** Takes as much of the stack as fits and the filter allows. Shrinks the stack; returns how many went in. */
	int deposit(ItemStack stack) {
		if (packed || !accepts(stack)) {
			return 0;
		}
		int n = (int) Math.min(stack.getCount(), space());
		if (n <= 0) {
			return 0;
		}
		items.merge(Key.of(stack), (long) n, Long::sum);
		total += n;
		stack.shrink(n);
		changed();
		return n;
	}

	/** Takes up to n of this kind, as far as there's room and the filter allows. Returns how many went in. */
	long put(Key key, long n) {
		if (packed || !accepts(key.stack)) {
			return 0;
		}
		long in = Math.min(Math.max(0, n), space());
		if (in <= 0) {
			return 0;
		}
		items.merge(key, in, Long::sum);
		total += in;
		changed();
		return in;
	}

	boolean isBranch() {
		return !central.isEmpty();
	}

	/** Puts back stock without any checks (loading from disk). */
	void restore(ItemStack stack, long count) {
		if (stack.isEmpty() || count <= 0) {
			return;
		}
		items.merge(Key.of(stack), count, Long::sum);
		total += count;
	}

	/** Takes up to n of this kind out of the tally. Returns how many were actually there. */
	long take(Key key, long n) {
		long have = count(key);
		long out = Math.min(have, Math.max(0, n));
		if (out <= 0) {
			return 0;
		}
		if (out == have) {
			items.remove(key);
		} else {
			items.put(key, have - out);
		}
		total -= out;
		changed();
		return out;
	}

	void changed() {
		version++;
		Warehouses.markDirty();
	}

	boolean isOwner(Player player) {
		return owner.isEmpty() || owner.equals(player.getUUID().toString());
	}

	/** Anyone can put things in; taking out needs the owner when it's locked. */
	boolean mayWithdraw(Player player) {
		return !locked || isOwner(player) || player.isCreative();
	}

	boolean mayManage(Player player) {
		return isOwner(player) || player.isCreative();
	}

	int percentFull() {
		long cap = capacity();
		return cap <= 0 ? 100 : (int) Math.min(100, total * 100 / cap);
	}
}
