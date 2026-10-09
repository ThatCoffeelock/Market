package com.thatcoffeelock.sellswords;

import net.minecraft.core.BlockPos;

/** A Mercenary Station: a target block, placed by a player or built into a Colonycraft Guildhouse. */
final class Station {
	String dim = "";
	int x;
	int y;
	int z;
	/** Off the hiring price: 0 for a plain station, more for a Guildhouse's (by its tier). */
	double discount;
	/** "Mercenary Station" or "<colony> Guildhouse". */
	String name = "Mercenary Station";
	/** True for one built into a building: breaking it gives nothing back (the building puts it back on repair). */
	boolean builtIn;

	BlockPos pos() {
		return new BlockPos(x, y, z);
	}

	String key() {
		return Stations.key(dim, pos());
	}
}
