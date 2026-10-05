package com.thatcoffeelock.warehouse;

import java.util.List;

import com.thatcoffeelock.fossilfool.FossilFoolApi;

/**
 * The only class that talks to Fossil Fool, and it's only loaded when Fossil Fool is installed. A Drill Rig unloads
 * its ore and stone holds into the warehouses near it, the same way a ship unloads at a Loading Dock: specialist
 * warehouses (say, ores only) get their kind first, then the general ones, nearest first.
 */
final class FossilLink {
	private FossilLink() {
	}

	static void init() {
		FossilFoolApi.registerUnloader((level, rig, holds) -> {
			List<Warehouse> near = Warehouses.near(level, rig, FossilFoolApi.reach());
			return near.isEmpty() ? 0 : Docks.unload(near, holds).moved();
		});
	}
}
