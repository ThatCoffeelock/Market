package com.thatcoffeelock.hoogheheeren;

import java.util.Random;

import net.minecraft.ChatFormatting;

/** Who sits at the other end of the table: Dutch patricians mostly, with the odd Englishman, Spaniard or Frenchman. */
final class Names {
	private static final String[] DUTCH_HOUSES = {"van Houten", "van Brederode", "de Graeff", "Bicker", "Huydecoper", "van Beuningen", "Trip",
		"de Witt", "van Loon", "Six", "Pauw", "Reael", "van Aerssen", "van Wassenaer", "Valckenier", "Hooft", "Coymans", "van Egmond",
		"van Arkel", "Spiegel", "de Geer", "Calandrini", "van Oldenbarnevelt", "Boreel", "van der Does", "Schimmelpenninck", "Hasselaer",
		"van Swieten", "Corver", "Backer", "Tulp", "van Lennep"};
	private static final String[] MEN = {"Joost", "Cornelis", "Andries", "Jacob", "Willem", "Hendrick", "Frederik", "Johan", "Reynier",
		"Gerard", "Floris", "Adriaen", "Pieter", "Dirck", "Coenraad", "Maurits", "Lodewijk", "Ernst", "Diederik", "Balthasar"};
	private static final String[] WOMEN = {"Agneta", "Catharina", "Maria", "Johanna", "Elisabeth", "Geertruyd", "Machteld", "Sophia",
		"Margaretha", "Amalia", "Hester", "Petronella"};
	private static final String[] MEN_TITLES = {"Heer", "Jonkheer", "Burgemeester", "Graaf", "Baron", "Heer"};
	private static final String[] WOMEN_TITLES = {"Vrouwe", "Jonkvrouw", "Gravin", "Weduwe"};

	/** The foreigners: a house, the men's and women's titles and first names, and what the seat is called. */
	private record Nation(String adjective, String[] houses, String man, String woman, String[] men, String[] women, String seat) {
	}

	private static final Nation[] FOREIGN = {
		new Nation("English", new String[] {"Ashworth", "Courten", "Fairfax", "Pembroke", "Hawkins", "Cavendish"}, "Lord", "Lady",
			new String[] {"Edmund", "Thomas", "Richard", "Walter", "Francis"}, new String[] {"Anne", "Margaret", "Eleanor"}, "Hall"),
		new Nation("Spanish", new String[] {"de Alba", "de Zúñiga", "de Mendoza", "de Guzmán", "de Requesens"}, "Don", "Doña",
			new String[] {"Rodrigo", "Fernando", "Álvaro", "Diego", "Luis"}, new String[] {"Isabel", "Juana", "Catalina"}, "Castillo"),
		new Nation("French", new String[] {"de Vauban", "de Rohan", "de Montmorency", "d'Estrées", "de Condé"}, "Comte", "Comtesse",
			new String[] {"Henri", "Louis", "Philippe", "Gaston", "Armand"}, new String[] {"Marguerite", "Louise", "Anne-Marie"}, "Château"),
		new Nation("Danish", new String[] {"Rosenkrantz", "Gyldenstierne", "Brahe", "Ulfeldt"}, "Herr", "Fru",
			new String[] {"Christian", "Tycho", "Corfitz", "Knud"}, new String[] {"Kirsten", "Sophie", "Ellen"}, "Slot"),
	};

	/** House colours: the dye name (for banners, carpets and leather) and the chat colour that goes with it. */
	record Colour(String dye, ChatFormatting chat, int rgb) {
	}

	static final Colour[] COLOURS = {
		new Colour("red", ChatFormatting.RED, 0xB02E26),
		new Colour("blue", ChatFormatting.BLUE, 0x3C44AA),
		new Colour("yellow", ChatFormatting.YELLOW, 0xFED83D),
		new Colour("green", ChatFormatting.DARK_GREEN, 0x5E7C16),
		new Colour("purple", ChatFormatting.DARK_PURPLE, 0x8932B8),
		new Colour("black", ChatFormatting.DARK_GRAY, 0x1D1D21),
		new Colour("white", ChatFormatting.WHITE, 0xF9FFFE),
		new Colour("cyan", ChatFormatting.DARK_AQUA, 0x169C9C),
		new Colour("lime", ChatFormatting.GREEN, 0x80C71F),
		new Colour("light_blue", ChatFormatting.AQUA, 0x3AB3DA),
		new Colour("magenta", ChatFormatting.LIGHT_PURPLE, 0xC74EBD),
		new Colour("brown", ChatFormatting.GOLD, 0x835432),
		new Colour("gray", ChatFormatting.GRAY, 0x474F52),
	};

	/** Your colour: Orange, naturally. */
	static final Colour OURS = new Colour("orange", ChatFormatting.GOLD, 0xF9801D);

	/** Vanilla's default skins: every client has them. */
	private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};

	private static final String[] SOLDIERS = {"Pikeman", "Musketeer", "Halberdier", "Swordsman", "Arquebusier", "Sergeant"};

	private Names() {
	}

	static Colour colour(String dye) {
		for (Colour c : COLOURS) {
			if (c.dye().equals(dye)) {
				return c;
			}
		}
		return dye.equals(OURS.dye()) ? OURS : COLOURS[0];
	}

	static String skin(Random random) {
		return SKINS[random.nextInt(SKINS.length)];
	}

	/** "entity/player/slim/kai": the texture a mannequin's profile points at. */
	static String texture(boolean slim, String skin) {
		return "entity/player/" + (slim ? "slim" : "wide") + "/" + skin;
	}

	static String soldier(Random random) {
		return SOLDIERS[random.nextInt(SOLDIERS.length)];
	}

	/**
	 * Rolls a new head of house into {@code r} (name, title, sex, nation, skin). With {@code keepHouse}, it's an heir:
	 * same house, same nation, a new first name.
	 */
	static void lord(Rival r, Random random, boolean keepHouse) {
		boolean woman = random.nextInt(4) == 0;
		Nation nation = null;
		if (keepHouse) {
			for (Nation n : FOREIGN) {
				if (n.adjective().equals(r.nation)) {
					nation = n;
				}
			}
		} else {
			nation = random.nextInt(10) < 3 ? FOREIGN[random.nextInt(FOREIGN.length)] : null;
			r.nation = nation == null ? "" : nation.adjective();
			r.house = nation == null ? DUTCH_HOUSES[random.nextInt(DUTCH_HOUSES.length)] : nation.houses()[random.nextInt(nation.houses().length)];
			r.seatName = nation == null ? (random.nextBoolean() ? "Huis " : "Kasteel ") + seatWord(r.house) : nation.seat() + " " + seatWord(r.house);
		}
		r.woman = woman;
		if (nation == null) {
			r.first = woman ? WOMEN[random.nextInt(WOMEN.length)] : MEN[random.nextInt(MEN.length)];
			r.title = woman ? WOMEN_TITLES[random.nextInt(WOMEN_TITLES.length)] : MEN_TITLES[random.nextInt(MEN_TITLES.length)];
		} else {
			r.first = woman ? nation.women()[random.nextInt(nation.women().length)] : nation.men()[random.nextInt(nation.men().length)];
			r.title = woman ? nation.woman() : nation.man();
		}
		r.skin = skin(random);
	}

	/** "van Houten" -> "Houten", "de Graeff" -> "Graeff". */
	private static String seatWord(String house) {
		String[] words = house.split(" ");
		String last = words[words.length - 1];
		return Character.toUpperCase(last.charAt(0)) + last.substring(1);
	}
}
