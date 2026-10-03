package com.thatcoffeelock.warehouse;

import com.thatcoffeelock.cargotrain.CargoTrainApi;
import com.thatcoffeelock.cargotrain.CargoTrainApi.Intake;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The only class that talks to Cargo Train, and it's only loaded when Cargo Train is installed. A Drop-off Station
 * touching a warehouse (its core or a counted rack) unloads the train straight onto the shelves, not just into the
 * chest.
 */
final class TrainLink {
	private TrainLink() {
	}

	static void init() {
		CargoTrainApi.registerDropOffIntake(TrainLink::intake);
	}

	static @Nullable Intake intake(ServerLevel level, BlockPos station) {
		Warehouse w = Warehouses.touching(level, station);
		return w == null ? null : w::deposit;
	}
}
