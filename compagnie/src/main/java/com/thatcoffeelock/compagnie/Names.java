package com.thatcoffeelock.compagnie;

import java.util.Random;

/** Who works for the Compagnie, and who borrows from it. Everyone is Dutch. The money is too, briefly. */
final class Names {
	private static final String[] FIRST = {"Joost", "Klaas", "Pieter", "Jan", "Kees", "Gerrit", "Bram", "Daan", "Wouter", "Maarten", "Thijs",
		"Floris", "Willem", "Jeroen", "Rutger", "Dirk", "Barend", "Cornelis", "Abel", "Jacob", "Hendrick", "Abraham", "Anouk", "Femke",
		"Griet", "Truus", "Saskia", "Wilhelmina", "Geertje", "Aaltje", "Machteld", "Judith"};
	private static final String[] SURNAMES = {"de Vries", "van Dijk", "Bakker", "Jansen", "Visser", "Smit", "de Boer", "Mulder", "de Groot",
		"Bos", "Vos", "Hendriks", "van Leeuwen", "Dekker", "de Wit", "Kok", "van der Meer", "Schouten", "Hoekstra", "Koopman", "van Rijn",
		"Tasman", "Coen", "van Diemen", "Hudson", "Barentsz", "Heemskerck", "Speelman", "Stuyvesant"};
	private static final String[] NICKNAMES = {"the Shark", "Peg-Leg", "One-Eye", "the Lucky", "Old Salt", "the Cheese", "Two Knives",
		"the Lawyer", "Herring", "the Quiet", "Stormcrow", "the Accountant"};
	private static final String[] TRADES = {"the Baker", "the Brewer", "the Cooper", "the Weaver", "the Printer", "the Shipwright",
		"the Tulip Grower", "the Cheesemonger", "the Rope Maker", "the Painter", "the Miller", "the Glassblower", "the Widow",
		"the Burgomaster's Nephew", "the Herring Packer", "the Notary", "the Organ Builder", "the Clog Carver"};

	private Names() {
	}

	/** "Jan 'the Shark' Coen", now and then, or plain "Griet Bakker". */
	static String agent(Random random) {
		String first = FIRST[random.nextInt(FIRST.length)];
		String last = SURNAMES[random.nextInt(SURNAMES.length)];
		return random.nextInt(4) == 0 ? first + " '" + NICKNAMES[random.nextInt(NICKNAMES.length)] + "' " + last : first + " " + last;
	}

	/** "Pieter the Baker". */
	static String borrower(Random random) {
		return FIRST[random.nextInt(FIRST.length)] + " " + TRADES[random.nextInt(TRADES.length)];
	}

	/** Ship names for the telegrams. */
	private static final String[] SHIPS = {"Gouden Leeuw", "Halve Maen", "Batavia", "Duyfken", "Zeven Provinciën", "Amsterdam",
		"Witte Olifant", "Vergulde Draeck", "Eendracht", "Zeehaen", "Heemskerck", "Mauritius"};

	static String ship(Random random) {
		return SHIPS[random.nextInt(SHIPS.length)];
	}
}
