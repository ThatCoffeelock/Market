package com.thatcoffeelock.sellswords;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** One hired sellsword, as saved in sellswords.json. Their brain and body are found by tag when their chunk loads. */
final class Merc {
	enum Orders {
		/** Walk with the owner, fight what attacks them or what they attack, board their ship or airship. */
		FOLLOW,
		/** Hold a spot like a dog told to stay: fight what comes near, then go back. Protect villagers. */
		GUARD,
		/** Idle at their Mercenary Station: patrol up to 50 blocks around it, protect it and its villagers. */
		STATION
	}

	String id = "";
	String owner = "";
	String ownerName = "";
	String name = "";
	boolean slim;
	String skin = "steve";
	String rank = Rank.RECRUIT.name();
	String orders = Orders.STATION.name();
	/** Following mercenaries also shoot farm animals near the owner (never named, leashed, tamed or baby ones). */
	boolean hunting;
	/** Where the brain was last seen. */
	String dim = "";
	double x;
	double y;
	double z;
	/** The spot to hold under GUARD orders. */
	String postDim = "";
	int postX;
	int postY;
	int postZ;
	/** The Mercenary Station they belong to (Stations key), or "". */
	String home = "";
	int kills;
	long hired;
	String brain = "";
	String body = "";

	Rank rank() {
		return Rank.byName(rank);
	}

	Orders orders() {
		try {
			return Orders.valueOf(orders);
		} catch (IllegalArgumentException e) {
			return Orders.GUARD;
		}
	}

	void orders(Orders o) {
		orders = o.name();
	}

	@Nullable UUID ownerId() {
		try {
			return UUID.fromString(owner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	BlockPos post() {
		return new BlockPos(postX, postY, postZ);
	}

	void post(String dimension, BlockPos pos) {
		postDim = dimension;
		postX = pos.getX();
		postY = pos.getY();
		postZ = pos.getZ();
	}

	/** "Joost de Vries, Marksman". */
	String title() {
		return name + ", " + rank().title;
	}

	String firstName() {
		int space = name.indexOf(' ');
		return space > 0 ? name.substring(0, space) : name;
	}
}
