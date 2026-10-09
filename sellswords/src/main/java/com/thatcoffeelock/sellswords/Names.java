package com.thatcoffeelock.sellswords;

import java.util.Random;

/** Every sellsword is Dutch. Nobody knows why. They split the bill, too. */
final class Names {
	private static final String[] MEN = {"Joost", "Klaas", "Pieter", "Jan", "Henk", "Kees", "Gerrit", "Bram", "Sjaak", "Daan", "Wouter",
		"Maarten", "Thijs", "Ruud", "Freek", "Joop", "Harm", "Bart", "Floris", "Willem", "Sander", "Jeroen", "Teun", "Huub", "Rutger",
		"Dirk", "Hessel", "Sietse", "Gijs", "Barend", "Cor", "Frits", "Lodewijk", "Piet", "Arjen", "Wim", "Evert", "Tjerk"};
	private static final String[] WOMEN = {"Anouk", "Femke", "Sanne", "Marieke", "Fenna", "Griet", "Truus", "Annelies", "Ilse", "Mirjam",
		"Lieke", "Joke", "Geertje", "Hennie", "Saskia", "Wilhelmina", "Roos", "Bep", "Fleur", "Tjitske"};
	private static final String[] SURNAMES = {"de Vries", "van Dijk", "Bakker", "Jansen", "Visser", "Smit", "Meijer", "de Boer", "Mulder",
		"de Groot", "Bos", "Vos", "Peters", "Hendriks", "van Leeuwen", "Dekker", "Brouwer", "de Wit", "Dijkstra", "Kok", "van der Meer",
		"Schouten", "Vermeulen", "van den Berg", "Hoekstra", "Koopman", "van Rijn", "de Ruyter", "Haverkamp", "Zwart", "Bloemendaal",
		"van Oranje-Nassau (no relation)", "Kaaskop", "Stroopwafel", "Hagelslag", "van Gelder", "Postma", "Terpstra"};
	/** Vanilla's default skins: every client has them, so no skin server is needed. */
	private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};

	private Names() {
	}

	record Person(String name, boolean slim, String skin) {
	}

	static Person roll(Random random) {
		boolean woman = random.nextInt(3) == 0;
		String first = woman ? WOMEN[random.nextInt(WOMEN.length)] : MEN[random.nextInt(MEN.length)];
		String last = SURNAMES[random.nextInt(SURNAMES.length)];
		if (last.contains("(") && random.nextInt(3) != 0) {
			last = SURNAMES[random.nextInt(4)]; // the joke surnames are rarer
		}
		return new Person(first + " " + last, woman, SKINS[random.nextInt(SKINS.length)]);
	}

	/** "entity/player/slim/kai": the texture a mannequin's profile points at. */
	static String texture(Person p) {
		return "entity/player/" + (p.slim() ? "slim" : "wide") + "/" + p.skin();
	}
}
