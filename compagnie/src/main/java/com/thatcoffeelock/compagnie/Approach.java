package com.thatcoffeelock.compagnie;

/** The three advisors: every job can be done (some of) these ways. */
enum Approach {
	FORCE("De Kapitein", "Force", "iron_sword", "Guns, blades and nerve. Fast and dangerous."),
	COIN("De Koopman", "Coin", "gold_ingot", "Bribes, contracts and friends in high places. Safe, but pricey."),
	GUILE("De Spion", "Guile", "spyglass", "Forged papers, quiet knives, back doors.");

	final String advisor;
	final String stat;
	final String icon;
	final String text;

	Approach(String advisor, String stat, String icon, String text) {
		this.advisor = advisor;
		this.stat = stat;
		this.icon = icon;
		this.text = text;
	}

	static Approach byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}
}
