package com.thatcoffeelock.fossilfool;

import net.minecraft.core.BlockPos;

/** A placed Oil Tank: a cauldron that holds one kind of oil, up to the configured capacity. */
final class Tank {
	final String dimension;
	final BlockPos pos;
	Fluid fluid = Fluid.NONE;
	int amount;
	/** The label text last drawn over it, so it's only redrawn when it changes. */
	String label = "";

	Tank(String dimension, BlockPos pos) {
		this.dimension = dimension;
		this.pos = pos;
	}

	static int capacity() {
		return FossilConfig.get().tankCapacity;
	}

	int space() {
		return Math.max(0, capacity() - amount);
	}

	boolean takes(Fluid kind) {
		return kind != Fluid.NONE && (fluid == Fluid.NONE || fluid == kind) && space() > 0;
	}

	/** Pours in up to n buckets. Returns how many went in. */
	int fill(Fluid kind, int n) {
		if (!takes(kind) || n <= 0) {
			return 0;
		}
		int in = Math.min(n, space());
		fluid = kind;
		amount += in;
		Store.changed();
		return in;
	}

	/** Takes out up to n buckets of this kind. Returns how many came out. */
	int drain(Fluid kind, int n) {
		if (fluid != kind || n <= 0) {
			return 0;
		}
		int out = Math.min(n, amount);
		amount -= out;
		if (amount == 0) {
			fluid = Fluid.NONE;
		}
		Store.changed();
		return out;
	}
}
