package com.thatcoffeelock.fossilfool;

import net.minecraft.core.BlockPos;

/**
 * A placed Tank: a cauldron that holds one fluid at a time, up to the configured capacity. It can be set to one
 * fluid (crude, diesel, water or lava), and then it takes nothing else, not even from the pipes. Unset, it takes
 * whatever comes first.
 */
final class Tank {
	final String dimension;
	final BlockPos pos;
	Fluid fluid = Fluid.NONE;
	int amount;
	/** The only fluid it takes, or NONE for any. */
	Fluid set = Fluid.NONE;
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

	/** "Lava Tank" when it's set to lava (or holds lava); "Tank" when it's unset and empty. */
	String name() {
		return set != Fluid.NONE ? set.tankName : fluid.tankName;
	}

	boolean takes(Fluid kind) {
		return kind != Fluid.NONE && (set == Fluid.NONE || set == kind) && (fluid == Fluid.NONE || fluid == kind) && space() > 0;
	}

	/** Can it be set to this fluid? Only if it's empty, or already holds it. */
	boolean canSet(Fluid kind) {
		return kind == Fluid.NONE || fluid == Fluid.NONE || fluid == kind;
	}

	/** Cycles to the next fluid it can be set to. Returns the new setting. */
	Fluid cycle() {
		Fluid next = set.next();
		while (!canSet(next)) {
			next = next.next();
		}
		set = next;
		Store.changed();
		return set;
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
