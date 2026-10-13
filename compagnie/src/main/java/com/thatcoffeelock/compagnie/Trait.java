package com.thatcoffeelock.compagnie;

import java.util.Random;

/** What an agent is like. One or two each, rolled when they turn up at the hiring board. */
enum Trait {
	LUCKY("Lucky", "+8% odds on every job."),
	VETERAN("Veteran", "+5% odds on every job. Asks for 20% more pay."),
	DRUNKARD("Drunkard", "Works for 40% less pay. -6% odds. Smells of genever."),
	GREEDY("Greedy", "Pockets 15% of the Marks a job brings in."),
	COWARD("Coward", "-10% odds the Kapitein's way. Never dies: runs, and comes back wounded."),
	NAVIGATOR("Navigator", "+10% odds at sea, and voyages take a day less."),
	SILVER_TONGUED("Silver-tongued", "+10% odds the Koopman's way."),
	BUTCHER("Butcher", "+10% odds the Kapitein's way, and 10% more loot."),
	SHADOW("Shadow", "+10% odds the Spion's way.");

	final String title;
	final String text;

	Trait(String title, String text) {
		this.title = title;
		this.text = text;
	}

	static Trait roll(Random random) {
		return values()[random.nextInt(values().length)];
	}

	static Trait byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}
}
