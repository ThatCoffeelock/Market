package com.thatcoffeelock.hoogheheeren;

/**
 * What a rival wants. It sets how much they care about your Honour (keeping your word) and your Fear (what happens
 * to people who cross you), where their opinion of you starts, and what they like to do at dawn.
 */
enum Temperament {
	MERCHANT("Merchant Prince", "Lives for the deal. Loves trade pacts and partners who keep their word; hates wars that cost money.",
		0.40, 0.00, 5),
	WARLORD("Warlord", "Respects strength and nothing else. Raises regiments, raids, demands tribute, declares war.", 0.00, 0.45, -5),
	SCHEMER("Schemer", "Trusts nobody. Spies, sabotages, and whispers poison in everyone's ear.", 0.15, 0.15, -15),
	MISER("Miser", "Hoards gold. Loves gifts of money, hates paying for anything, least of all an army.", 0.20, 0.10, 0),
	ZEALOT("Zealot", "A Calvinist of iron principle. Keeps every pact, never forgives a broken one, frowns on drink.", 0.50, 0.00, -5),
	SPENDTHRIFT("Spendthrift", "Throws money around: feasts, gifts, regiments. Easy to like, easy to bankrupt.", 0.20, 0.10, 10);

	final String title;
	final String text;
	/** Opinion per point of your Honour above 50, and of your Fear above 30. */
	final double honour;
	final double fear;
	final int base;

	Temperament(String title, String text, double honour, double fear, int base) {
		this.title = title;
		this.text = text;
		this.honour = honour;
		this.fear = fear;
		this.base = base;
	}

	static Temperament byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return MERCHANT;
		}
	}
}
