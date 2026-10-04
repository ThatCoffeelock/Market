package com.thatcoffeelock.fuckillagers;

import java.util.UUID;

/** One bounty. Plain fields, so it saves as JSON. */
final class Contract {
	static final String POSTED = "posted";
	static final String ACTIVE = "active";
	static final String DONE = "done";
	static final String ABANDONED = "abandoned";

	String id;
	String owner;
	String ownerName;
	String tier;
	String site;
	String target;
	int x;
	int y;
	int z;
	/** posted (not built yet), active (built, boss alive), done (boss dead), abandoned. */
	String state = POSTED;
	String boss = "";

	Tier tier() {
		return Tier.byName(tier);
	}

	Tier.Site site() {
		return Tier.Site.byName(site);
	}

	boolean open() {
		return POSTED.equals(state) || ACTIVE.equals(state);
	}

	boolean ownedBy(UUID player) {
		return player.toString().equals(owner);
	}
}
