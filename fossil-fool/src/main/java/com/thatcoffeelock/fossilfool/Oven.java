package com.thatcoffeelock.fossilfool;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * A placed Industrial Oven: a smoker that burns diesel and smelts or cooks anything a furnace can, about a stack in
 * three seconds. Ores and raw metal come out double. It holds its own diesel, and drinks more from Tanks within reach
 * or on its pipeline. Hoppers work the vanilla way: in at the top, out at the bottom.
 */
final class Oven {
	enum State {
		OFF("Switched off"),
		WORKING("Smelting"),
		IDLE("Nothing to smelt"),
		NO_DIESEL("Out of diesel"),
		FULL("Output full");

		final String text;

		State(String text) {
			this.text = text;
		}
	}

	/** The smoker's own slots, which hoppers use: input on top, output underneath. */
	private static final int HOPPER_IN = 0;
	private static final int HOPPER_OUT = 2;

	final String dimension;
	final BlockPos pos;
	String owner = "";
	boolean on = true;
	/** Buckets of diesel in its tank. */
	int diesel;
	/** Items left to smelt on the bucket that's burning. */
	int charge;
	/** Items smelted, ever. For bragging. */
	long smelted;
	State state = State.IDLE;
	final SimpleContainer input = new SimpleContainer(9);
	final SimpleContainer output = new SimpleContainer(18);
	String label = "";
	private int age;
	private double progress;
	private final Pipes.Link pipes = new Pipes.Link();

	Oven(String dimension, BlockPos pos) {
		this.dimension = dimension;
		this.pos = pos;
	}

	static int capacity() {
		return FossilConfig.get().ovenTank;
	}

	@Nullable UUID ownerId() {
		try {
			return owner.isEmpty() ? null : UUID.fromString(owner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The pipeline that starts at a pipe touching the oven. */
	Pipes.Network pipeline(ServerLevel level) {
		return pipes.get(level, () -> Machines.around(pos));
	}

	/** Ores, raw metal and ancient debris come out double. */
	static boolean doubles(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.endsWith("_ore") || id.equals("ancient_debris") || (id.startsWith("raw_") && !id.endsWith("_block"));
	}

	/** What a furnace makes of one of these (doubled for ores), or EMPTY if it doesn't smelt. */
	static ItemStack smelt(ServerLevel level, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		SingleRecipeInput in = new SingleRecipeInput(stack.copyWithCount(1));
		ItemStack out = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, in, level)
			.map(holder -> holder.value().assemble(in, level.registryAccess()))
			.orElse(ItemStack.EMPTY);
		if (!out.isEmpty() && doubles(stack)) {
			out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * 2));
		}
		return out;
	}

	void tick(ServerLevel level) {
		age++;
		FossilConfig c = FossilConfig.get();
		if (age % 20 == 0) {
			Machines.pipeIn(level, this);
			hoppers(level);
		}
		State before = state;
		state = work(level, c);
		if (state == State.WORKING && age % 20 == 0) {
			Cmd.particles(level, "minecraft:campfire_cosy_smoke", pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0.05, 0.02, 1);
		}
		if (before != state || age % 100 == 1) {
			lit(level, state == State.WORKING);
		}
	}

	private State work(ServerLevel level, FossilConfig c) {
		if (!on) {
			return State.OFF;
		}
		if (input.isEmpty()) {
			return State.IDLE;
		}
		int slot = -1;
		ItemStack result = ItemStack.EMPTY;
		boolean full = false;
		for (int i = 0; i < input.getContainerSize(); i++) {
			ItemStack out = smelt(level, input.getItem(i));
			if (out.isEmpty()) {
				continue;
			}
			if (!Rig.fits(output, List.of(out))) {
				full = true;
				continue;
			}
			slot = i;
			result = out;
			break;
		}
		if (slot < 0) {
			return full ? State.FULL : State.IDLE;
		}
		if (charge <= 0) {
			if (diesel <= 0) {
				Machines.pipeIn(level, this);
			}
			if (diesel <= 0) {
				return State.NO_DIESEL;
			}
			diesel--;
			charge += c.ovenItemsPerDiesel;
			Store.changed();
		}
		progress += 1;
		if (progress < c.ovenTicksPerItem) {
			return State.WORKING;
		}
		progress -= c.ovenTicksPerItem;
		input.getItem(slot).shrink(1);
		if (input.getItem(slot).isEmpty()) {
			input.setItem(slot, ItemStack.EMPTY);
		}
		output.addItem(result);
		charge--;
		smelted++;
		UUID who = ownerId();
		if (who != null && smelted % 8 == 0) {
			Hooks.xp(who, 1.0);
		}
		if (smelted % 16 == 0) {
			Cmd.sound(level, "minecraft:block.fire.extinguish", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.3f, 1.4f);
		}
		return State.WORKING;
	}

	/** Takes what hoppers put in the smoker's top slot, and leaves output in its bottom slot for hoppers to take. */
	private void hoppers(ServerLevel level) {
		if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container box) || box.getContainerSize() <= HOPPER_OUT) {
			return;
		}
		ItemStack in = box.getItem(HOPPER_IN);
		if (!in.isEmpty()) {
			ItemStack rest = input.addItem(in.copy());
			box.setItem(HOPPER_IN, rest);
		}
		ItemStack out = box.getItem(HOPPER_OUT);
		for (int i = 0; i < output.getContainerSize(); i++) {
			ItemStack stack = output.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (out.isEmpty()) {
				box.setItem(HOPPER_OUT, stack.copy());
				output.setItem(i, ItemStack.EMPTY);
				out = box.getItem(HOPPER_OUT);
			} else if (ItemStack.isSameItemSameComponents(out, stack) && out.getCount() < out.getMaxStackSize()) {
				int n = Math.min(stack.getCount(), out.getMaxStackSize() - out.getCount());
				out.grow(n);
				stack.shrink(n);
				if (stack.isEmpty()) {
					output.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		box.setChanged();
	}

	/** The smoker glows while it works. */
	private void lit(ServerLevel level, boolean lit) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState s = level.getBlockState(pos);
		if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != lit) {
			level.setBlock(pos, s.setValue(BlockStateProperties.LIT, lit), 3);
		}
	}
}
