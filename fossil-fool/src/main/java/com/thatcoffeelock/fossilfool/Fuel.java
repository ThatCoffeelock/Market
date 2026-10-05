package com.thatcoffeelock.fossilfool;

import net.minecraft.ChatFormatting;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The fuel ladder. Each one is worth more drilled blocks per item than the last, and burns faster too:
 * coal (and charcoal, and coal blocks) to get going, lava from the Nether, crude from the ground, and diesel from
 * the Refinery. Everything that burns in a Drill Rig also heats a Refinery.
 */
enum Fuel {
	COAL("Coal", ChatFormatting.DARK_GRAY),
	LAVA("Lava", ChatFormatting.RED),
	CRUDE("Crude Oil", ChatFormatting.GRAY),
	DIESEL("Diesel", ChatFormatting.GOLD);

	final String title;
	final ChatFormatting color;

	Fuel(String title, ChatFormatting color) {
		this.title = title;
		this.color = color;
	}

	/** Drilled blocks one item is worth. */
	double blocks() {
		FossilConfig c = FossilConfig.get();
		return switch (this) {
			case COAL -> c.coalBlocks;
			case LAVA -> c.lavaBlocks;
			case CRUDE -> c.crudeBlocks;
			case DIESEL -> c.dieselBlocks;
		};
	}

	/** How fast a machine runs on it (crude = 1.0). */
	double speed() {
		FossilConfig c = FossilConfig.get();
		return switch (this) {
			case COAL -> c.coalSpeed;
			case LAVA -> c.lavaSpeed;
			case CRUDE -> c.crudeSpeed;
			case DIESEL -> c.dieselSpeed;
		};
	}

	static @Nullable Fuel byName(String name) {
		for (Fuel fuel : values()) {
			if (fuel.name().equalsIgnoreCase(name)) {
				return fuel;
			}
		}
		return null;
	}

	/** One item of fuel going into the fire: what it is, what it's worth, and what's left over (a bucket). */
	record Burn(Fuel fuel, double blocks, ItemStack leftover) {
	}

	/** What burning one of this stack gives, or null if it doesn't burn in our machines. */
	static @Nullable Burn of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (OilItems.isDiesel(stack)) {
			return new Burn(DIESEL, DIESEL.blocks(), new ItemStack(Items.BUCKET));
		}
		if (OilItems.isCrude(stack)) {
			return new Burn(CRUDE, CRUDE.blocks(), new ItemStack(Items.BUCKET));
		}
		if (!OilItems.kind(stack).isEmpty()) {
			return null;
		}
		if (stack.is(Items.LAVA_BUCKET)) {
			return new Burn(LAVA, LAVA.blocks(), new ItemStack(Items.BUCKET));
		}
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return new Burn(COAL, COAL.blocks(), ItemStack.EMPTY);
		}
		if (stack.is(Items.COAL_BLOCK)) {
			return new Burn(COAL, COAL.blocks() * 9, ItemStack.EMPTY);
		}
		return null;
	}

	static boolean burns(ItemStack stack) {
		return of(stack) != null;
	}

	/**
	 * Takes one item of fuel out of a firebox. Empty buckets go back into the firebox (a free slot, or onto a stack
	 * of buckets); if there's no room they're handed to {@code overflow}. Returns null if there's nothing to burn.
	 */
	static @Nullable Burn take(Container firebox, java.util.function.Consumer<ItemStack> overflow) {
		for (int i = 0; i < firebox.getContainerSize(); i++) {
			ItemStack stack = firebox.getItem(i);
			Burn burn = of(stack);
			if (burn == null) {
				continue;
			}
			if (stack.getCount() == 1 && !burn.leftover().isEmpty()) {
				firebox.setItem(i, burn.leftover().copy());
			} else {
				stack.shrink(1);
				if (stack.isEmpty()) {
					firebox.setItem(i, ItemStack.EMPTY);
				}
				if (!burn.leftover().isEmpty()) {
					ItemStack left = burn.leftover().copy();
					stash(firebox, left);
					if (!left.isEmpty()) {
						overflow.accept(left);
					}
				}
			}
			firebox.setChanged();
			return burn;
		}
		return null;
	}

	/** Puts an empty bucket back into the firebox if there's room. Shrinks the stack by what went in. */
	private static void stash(Container box, ItemStack stack) {
		for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack slot = box.getItem(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
				int n = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
				slot.grow(n);
				stack.shrink(n);
			}
		}
		for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, stack.copy());
				stack.setCount(0);
			}
		}
	}

	/** Lore lines that explain the ladder, for the help icons. */
	static String ladderLine(Fuel fuel) {
		double b = fuel.blocks();
		String blocks = b == Math.rint(b) ? String.valueOf((long) b) : String.valueOf(b);
		return fuel.title + ": " + blocks + " blocks each, " + fuel.speed() + "× speed";
	}
}
