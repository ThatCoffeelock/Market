package com.thatcoffeelock.fossilfool;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A fixed-size view onto part of a bigger container, from a movable offset: one page of an upgraded rig's hold in the
 * rig's chest screen. The container behind it is looked up every time, because upgrading the holds replaces it.
 */
final class Window implements Container {
	private final Supplier<Container> base;
	private final IntSupplier offset;
	private final int size;

	Window(Supplier<Container> base, IntSupplier offset, int size) {
		this.base = base;
		this.offset = offset;
		this.size = size;
	}

	/** Index in the container behind, or -1 if this slot of the page is past its end. */
	private int at(int slot) {
		int i = offset.getAsInt() + slot;
		return slot >= 0 && slot < size && i < base.get().getContainerSize() ? i : -1;
	}

	@Override
	public int getContainerSize() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < size; i++) {
			if (!getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		int i = at(slot);
		return i < 0 ? ItemStack.EMPTY : base.get().getItem(i);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		int i = at(slot);
		return i < 0 ? ItemStack.EMPTY : base.get().removeItem(i, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		int i = at(slot);
		return i < 0 ? ItemStack.EMPTY : base.get().removeItemNoUpdate(i);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		int i = at(slot);
		if (i >= 0) {
			base.get().setItem(i, stack);
		}
	}

	@Override
	public void setChanged() {
		base.get().setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/** Never: closing a screen must not empty the rig. */
	@Override
	public void clearContent() {
	}
}
