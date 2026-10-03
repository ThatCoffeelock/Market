package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods (the Warehouse uses it): put something bigger behind a Drop-off Station. When the train stops at
 * a Drop-off Station, it first unloads into whatever a registered finder returns for that chest, then into the
 * chest itself. Cargo Train doesn't know or care who's on the other end.
 */
public final class CargoTrainApi {
	/** Takes cargo off the train. */
	@FunctionalInterface
	public interface Intake {
		/** Takes as much of the stack as it wants, shrinking it. Returns how many items it took. */
		int accept(ItemStack stack);
	}

	/** Finds the intake behind a Drop-off Station, if there is one. */
	@FunctionalInterface
	public interface IntakeFinder {
		@Nullable Intake find(ServerLevel level, BlockPos station);
	}

	private static final List<IntakeFinder> FINDERS = new ArrayList<>();

	private CargoTrainApi() {
	}

	public static void registerDropOffIntake(IntakeFinder finder) {
		FINDERS.add(finder);
	}

	static @Nullable Intake intakeBehind(ServerLevel level, BlockPos station) {
		for (IntakeFinder finder : FINDERS) {
			try {
				Intake intake = finder.find(level, station);
				if (intake != null) {
					return intake;
				}
			} catch (RuntimeException e) {
				CargoTrainMod.LOG.warn("A Drop-off Station intake from another mod failed", e);
			}
		}
		return null;
	}
}
