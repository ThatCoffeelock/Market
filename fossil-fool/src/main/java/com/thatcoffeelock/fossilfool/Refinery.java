package com.thatcoffeelock.fossilfool;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * A placed Refinery: a blast furnace with a still on top. Holds crude and diesel, burns fuel from its firebox, and
 * cooks {@code crudePerDiesel} buckets of crude into one bucket of diesel. It drinks crude from Oil Tanks nearby and
 * pipes diesel back into them.
 */
final class Refinery {
	enum State {
		OFF("Switched off"),
		WORKING("Refining"),
		NO_CRUDE("Waiting for crude"),
		NO_FUEL("Out of fuel"),
		FULL("Diesel tank full");

		final String text;

		State(String text) {
			this.text = text;
		}
	}

	final String dimension;
	final BlockPos pos;
	String owner = "";
	boolean on = true;
	int crude;
	int diesel;
	/** Fuel left in the fire, in drilled-block units. */
	double energy;
	@Nullable Fuel burning;
	double progress;
	State state = State.NO_CRUDE;
	final SimpleContainer firebox = new SimpleContainer(9) {
		@Override
		public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) {
			return Fuel.burns(stack);
		}
	};
	String label = "";
	private int age;

	Refinery(String dimension, BlockPos pos) {
		this.dimension = dimension;
		this.pos = pos;
		firebox.addListener(c -> Store.changed());
	}

	static int capacity() {
		return FossilConfig.get().refineryCapacity;
	}

	@Nullable UUID ownerId() {
		try {
			return owner.isEmpty() ? null : UUID.fromString(owner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	void tick(ServerLevel level) {
		age++;
		FossilConfig c = FossilConfig.get();
		if (age % 20 == 0) {
			Machines.pipeIn(level, this);
			Machines.pipeOut(level, this);
		}
		State before = state;
		if (!on) {
			state = State.OFF;
		} else if (diesel >= capacity()) {
			state = State.FULL;
		} else if (crude < c.crudePerDiesel) {
			state = State.NO_CRUDE;
		} else {
			if (progress <= 0 && energy < c.refineHeat) {
				refuel(level, c.refineHeat);
			}
			if (progress <= 0 && energy < c.refineHeat) {
				state = State.NO_FUEL;
			} else {
				if (progress <= 0) {
					energy -= c.refineHeat;
				}
				state = State.WORKING;
				double speed = burning == null ? 1.0 : burning.speed();
				progress += speed;
				if (progress >= c.refineTicks) {
					progress = 0;
					crude -= c.crudePerDiesel;
					int made = 1;
					UUID who = ownerId();
					if (who != null && Math.random() < Hooks.bonus(who, "refine")) {
						made++;
					}
					diesel = Math.min(capacity(), diesel + made);
					if (who != null) {
						Hooks.xp(who, 6.0);
					}
					Cmd.sound(level, "minecraft:block.brewing_stand.brew", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.8f, 0.7f);
					Store.changed();
				}
			}
		}
		if (state == State.WORKING && age % 30 == 0) {
			Cmd.particles(level, "minecraft:campfire_cosy_smoke", pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 0.05, 0.02, 1);
		}
		if (before != state || age % 100 == 1) {
			lit(level, state == State.WORKING);
		}
	}

	/** Burns fuel until there's at least this much heat in the fire (or the firebox is empty). */
	private void refuel(ServerLevel level, double needed) {
		UUID who = ownerId();
		double bonus = who == null ? 0 : Hooks.bonus(who, "fuel");
		while (energy < needed) {
			Fuel.Burn burn = Fuel.take(firebox, left -> Block.popResource(level, pos.above(), left));
			if (burn == null) {
				return;
			}
			energy += burn.blocks() * (1.0 + bonus);
			burning = burn.fuel();
		}
	}

	/** The furnace glows while it works. */
	private void lit(ServerLevel level, boolean lit) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
			level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), 3);
		}
	}
}
