package com.thatcoffeelock.compagnie;

import java.util.List;
import java.util.Locale;

/**
 * Every job the Compagnie can take on. A job has a base chance of success for each of the three approaches (below 0:
 * that advisor won't touch it), takes some days, costs a stake (lean, standard or lavish, in Marks) and on success
 * pays back the stake times {@link #mult}, plus spoils. Some do something special instead of (or as well as) paying.
 */
enum JobType {
	// ---------------------------------------------------------------- trade
	HERRING_FLEET("Herring Fleet", Category.TRADE, "cod", 0, 2, new long[] {200, 400, 800}, 1.4, 0.40, 0.70, 0.50, true, Special.NONE,
		"Send the busses out for the herring. Dull, steady, smells.",
		new Spoil("cod", 16, 32), new Spoil("salmon", 8, 16), new Spoil("kelp", 8, 16, 0.5)),
	FUR_AND_TIMBER("Fur & Timber Contract", Category.TRADE, "spruce_log", 0, 3, new long[] {300, 600, 1200}, 1.5, 0.50, 0.75, 0.50, true,
		Special.NONE, "A northern buyer wants pelts and masts. Somebody has to sail up there.",
		new Spoil("leather", 8, 16), new Spoil("rabbit_hide", 4, 12, 0.6), new Spoil("spruce_log", 16, 32)),
	TULIP_SPECULATION("Tulip Speculation", Category.TRADE, "red_tulip", 0, 3, new long[] {400, 800, 1600}, 2.4, -1, 0.50, 0.45, false,
		Special.NONE, "Bulbs are going for the price of a canal house. What could possibly go wrong?",
		new Spoil("red_tulip", 4, 12), new Spoil("orange_tulip", 4, 12), new Spoil("white_tulip", 2, 8, 0.6), new Spoil("pink_tulip", 2, 8, 0.6)),
	SPICE_RUN("Spice Run to the Indies", Category.TRADE, "cocoa_beans", 1, 4, new long[] {600, 1200, 2400}, 1.7, 0.45, 0.70, 0.55, true,
		Special.NONE, "Pepper, nutmeg, cloves. Half a year's sailing in four days, if the winds are kind.",
		new Spoil("cocoa_beans", 8, 24), new Spoil("sugar", 16, 32), new Spoil("emerald", 1, 4), new Spoil("glow_berries", 4, 12, 0.5)),
	COFFEE_FROM_MOCHA("Coffee from Mocha", Category.TRADE, "brown_dye", 1, 4, new long[] {800, 1600, 3200}, 1.6, 0.45, 0.70, 0.50, true,
		Special.NONE, "The Turks guard the bean like gold. The Koopman knows a man who knows a man.",
		new Spoil("cocoa_beans", 16, 32), new Spoil("brown_dye", 4, 12), new Spoil("emerald", 2, 5)),
	UNDERWRITE("Underwrite a Voyage", Category.TRADE, "writable_book", 1, 3, new long[] {500, 1000, 2000}, 1.35, -1, 0.80, -1, true,
		Special.NONE, "Insure somebody else's ship. Free money, until it sinks."),
	LUXURY_EXPORT("Luxury Export", Category.TRADE, "honey_bottle", 0, 3, new long[] {100, 200, 400}, 0, 0.45, 0.70, 0.60, true,
		Special.EXPORT, "Ship the sealed bottles and cigars you carry to buyers abroad, who pay two and a half times what Amsterdam does. Bring the cargo to the desk."),
	CHARTER_SHIP("Charter Your Ship", Category.TRADE, "oak_boat", 1, 4, new long[] {50, 100, 200}, 0, 0.45, 0.75, 0.55, true, Special.CHARTER,
		"A merchant wants a ship for a season. Rent out the ship (or airship) you carry, packed: big money, and it comes back. Unless it sinks."),
	CORNER_THE_MARKET("Corner the Market", Category.TRADE, "gold_nugget", 1, 2, new long[] {800, 1600, 3200}, 1.3, -1, 0.70, 0.60, false,
		Special.CORNER, "Buy up every last barrel in Amsterdam, then name your price. Your next trade jobs pay 20% more."),
	PORCELAIN("Porcelain from the East", Category.TRADE, "decorated_pot", 2, 5, new long[] {2000, 4000, 8000}, 1.8, 0.35, 0.65, 0.55, true,
		Special.NONE, "Blue and white, by the shipload. Pack it well.",
		new Spoil("white_glazed_terracotta", 8, 16), new Spoil("blue_glazed_terracotta", 8, 16), new Spoil("decorated_pot", 2, 4),
		new Spoil("emerald", 4, 8)),
	SILK_ROAD("Silk and Indigo", Category.TRADE, "blue_dye", 2, 5, new long[] {1500, 3000, 6000}, 1.75, 0.40, 0.65, 0.55, true,
		Special.NONE, "Bolts of silk and casks of indigo from Coromandel.",
		new Spoil("string", 16, 32), new Spoil("blue_dye", 8, 24), new Spoil("purple_dye", 4, 12), new Spoil("emerald", 2, 6)),
	NORTHERN_PASSAGE("The Northern Passage", Category.TRADE, "blue_ice", 3, 6, new long[] {4000, 8000, 16000}, 2.0, 0.45, 0.45, 0.55, true,
		Special.NONE, "Barentsz never came back. Your crew might. The short way to China, through the ice.",
		new Spoil("blue_ice", 8, 16), new Spoil("emerald", 8, 16), new Spoil("diamond", 2, 6), new Spoil("trident", 1, 1, 0.25, true)),

	// ---------------------------------------------------------------- raid
	ESCORT_CONVOY("Escort a Convoy", Category.RAID, "shield", 0, 2, new long[] {300, 600, 1200}, 1.4, 0.80, 0.60, 0.40, true, Special.NONE,
		"Ride shotgun for a merchant fleet. Easy pay, unless it isn't.", new Spoil("iron_ingot", 4, 8, 0.5)),
	PIRATE_HUNT("Pirate Hunt", Category.RAID, "iron_sword", 0, 3, new long[] {400, 800, 1600}, 1.6, 0.65, 0.45, 0.50, true, Special.NONE,
		"Dunkirk privateers are taking our ships. Take theirs.",
		new Spoil("gold_nugget", 16, 32), new Spoil("iron_ingot", 8, 16), new Spoil("gunpowder", 4, 12), new Spoil("heart_of_the_sea", 1, 1, 0.1, true)),
	ILLAGER_CAMP("Raid an Illager Camp", Category.RAID, "crossbow", 0, 2, new long[] {300, 600, 1200}, 1.5, 0.70, 0.30, 0.55, false,
		Special.NONE, "Pillagers have been hitting the trade roads. Burn them out.",
		new Spoil("emerald", 3, 8), new Spoil("crossbow", 1, 1, 0.5), new Spoil("arrow", 16, 32), new Spoil("totem_of_undying", 1, 1, 0.15, true)),
	AMBUSH_SMUGGLERS("Ambush the Smugglers", Category.RAID, "barrel", 0, 2, new long[] {300, 600, 1200}, 1.5, 0.60, -1, 0.65, true, Special.NONE,
		"Somebody else is running untaxed cargo through our waters. Not for long.",
		new Spoil("sugar", 16, 32), new Spoil("gunpowder", 8, 16), new Spoil("glass_bottle", 8, 16)),
	LETTER_OF_MARQUE("Letter of Marque", Category.RAID, "paper", 1, 3, new long[] {800, 1600, 3200}, 1.9, 0.65, 0.35, 0.50, true, Special.NONE,
		"The States General say piracy is legal, as long as it's Spanish ships. Go legally pirate.",
		new Spoil("gold_ingot", 4, 12), new Spoil("gunpowder", 8, 16), new Spoil("emerald", 2, 6), new Spoil("red_banner", 1, 1, 0.5)),
	BURN_THE_RIVAL("Burn the English Warehouse", Category.RAID, "flint_and_steel", 1, 2, new long[] {500, 1000, 2000}, 1.2, 0.55, -1, 0.65,
		false, Special.RIVAL, "The English Company is undercutting us in Bantam. A fire would be so unfortunate. Your trade odds go up 10% for four days."),
	SIEGE_FOR_HIRE("Hire Out to a Siege", Category.RAID, "tnt", 1, 4, new long[] {600, 1200, 2400}, 1.6, 0.70, 0.50, -1, false, Special.NONE,
		"A minor lord wants a town taken and doesn't care how. Bring guns.",
		new Spoil("iron_block", 1, 3), new Spoil("gold_ingot", 4, 10), new Spoil("saddle", 1, 1, 0.3)),
	SACK_A_FORT("Sack a Spanish Fort", Category.RAID, "netherite_scrap", 2, 4, new long[] {2000, 4000, 8000}, 1.9, 0.60, 0.30, 0.55, true,
		Special.NONE, "Silver from Potosí sits in that fort. Not for much longer.",
		new Spoil("gold_block", 1, 3), new Spoil("iron_block", 2, 4), new Spoil("emerald_block", 1, 2, 0.5), new Spoil("diamond", 1, 3, 0.5)),

	// ---------------------------------------------------------------- explore
	CHART_THE_COAST("Chart the Coast", Category.EXPLORE, "map", 0, 2, new long[] {200, 400, 800}, 1.1, 0.50, 0.50, 0.75, true, Special.CHART, "Send a surveyor along the shore. They come back with a chart to a wreck, a ruin or a monument.",
		List.of(), List.of("minecraft:shipwreck", "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm", "minecraft:monument")),
	OLD_PIRATES_MAP("An Old Pirate's Map", Category.EXPLORE, "filled_map", 1, 3, new long[] {400, 800, 1600}, 1.0, 0.45, 0.55, 0.75, false,
		Special.CHART, "A dying sailor sold us half a map. Find the other half, and the treasure.",
		List.of(), List.of("minecraft:buried_treasure")),
	HAUNTED_CASTLE("The Haunted Castle", Category.EXPLORE, "skeleton_skull", 1, 3, new long[] {500, 1000, 2000}, 1.2, 0.55, 0.40, 0.70, false,
		Special.CHART, "Peasants won't go near the old castle. Scout it, and mark the way in.",
		List.of(), List.of("hamlets:ruined_castle", "hamlets:haunted_cottage", "hamlets:dungeon", "minecraft:mansion")),
	FIND_A_VILLAGE("Find a Market Town", Category.EXPLORE, "bell", 0, 2, new long[] {150, 300, 600}, 1.2, 0.50, 0.70, 0.60, false, Special.CHART, "New customers! Somewhere out there is a village that's never heard of our prices.",
		List.of(), List.of("minecraft:village_plains", "minecraft:village_taiga", "minecraft:village_savanna", "minecraft:village_desert", "minecraft:village_snowy")),
	TRIAL_RUMOURS("Rumours of the Trials", Category.EXPLORE, "trial_key", 2, 3, new long[] {1000, 2000, 4000}, 1.2, 0.55, 0.40, 0.65, false,
		Special.CHART, "Copper halls under the hills, they say, and keys that open vaults. Mark them.",
		List.of(), List.of("minecraft:trial_chambers")),
	OIL_SURVEY("Oil Survey", Category.EXPLORE, "bucket", 1, 3, new long[] {300, 600, 1200}, 0, 0.40, 0.50, 0.70, false, Special.OIL,
		"A surveyor with a dowsing rod and a mule. They come back with the spot to sink a Fossil Fool drill."),
	LOST_COLONY("The Lost Colony", Category.EXPLORE, "bell", 1, 3, new long[] {400, 800, 1600}, 0, 0.50, 0.65, 0.55, false, Special.SACKS,
		"Settlers who lost their way, and would work for anyone who feeds them. Bring them home: in sacks, for your colonies."),
	RELIC_EXPEDITION("Relic Expedition", Category.EXPLORE, "nautilus_shell", 2, 5, new long[] {1500, 3000, 6000}, 1.3, 0.45, 0.45, 0.60, true,
		Special.NONE, "Ruined temples, sunken cities, old gods' trinkets. The museum in Leiden pays well.",
		new Spoil("nautilus_shell", 1, 3), new Spoil("diamond", 2, 6), new Spoil("music_disc_otherside", 1, 1, 0.2, true),
		new Spoil("golden_apple", 1, 2, 0.5)),
	NETHER_EXPEDITION("Expedition Beyond the Portal", Category.EXPLORE, "blaze_rod", 2, 4, new long[] {1200, 2400, 4800}, 1.5, 0.55, 0.30, 0.50,
		false, Special.NONE, "There's a colony to be made in the burning lands. Or at least some loot.",
		new Spoil("blaze_rod", 6, 12), new Spoil("quartz", 16, 32), new Spoil("glowstone_dust", 8, 16), new Spoil("ancient_debris", 1, 2, 0.4, true)),
	DEEP_DARK("The Deep Dark Ruins", Category.EXPLORE, "echo_shard", 3, 5, new long[] {3000, 6000, 12000}, 1.4, 0.40, 0.30, 0.65, false,
		Special.CHART, "A city under the world, and something in it that hears everything. Mark it, quietly.",
		List.of(), List.of("minecraft:ancient_city")),

	// ---------------------------------------------------------------- shady
	PRESSGANG("Pressgang a Crew", Category.SHADY, "lead", 0, 1, new long[] {100, 200, 400}, 0, 0.70, -1, 0.50, false, Special.RECRUIT,
		"Get a few sailors drunk in the harbour inns. They'll wake up employed. A free agent turns up at the hiring board."),
	SMUGGLE("Smuggle Past Customs", Category.SHADY, "ender_eye", 0, 2, new long[] {400, 800, 1600}, 2.0, 0.35, 0.55, 0.60, true, Special.NONE,
		"Untaxed cargo, unmarked crates, a harbourmaster looking the other way. Huge margins, if nobody looks the right way."),
	BRIBE_HARBOURMASTER("Bribe the Harbourmaster", Category.SHADY, "emerald", 1, 1, new long[] {300, 600, 1200}, 0, -1, 0.80, 0.60, false,
		Special.HARBOUR, "A purse for the harbourmaster, and our ships sail first. Sea jobs get 10% better odds for five days."),
	SPREAD_A_RUMOUR("Spread a Rumour on the Exchange", Category.SHADY, "feather", 1, 2, new long[] {500, 1000, 2000}, 1.6, -1, 0.55, 0.65,
		false, Special.NONE, "A whisper that a fleet went down. Prices crash. We buy. The fleet turns up. Prices soar."),
	FORGE_A_CHARTER("Forge a Charter", Category.SHADY, "name_tag", 1, 4, new long[] {1500, 3000, 6000}, 0, -1, 0.50, 0.55, false, Special.REP,
		"A charter with the States General's seal on it, more or less. The company's reputation leaps up."),
	BLACKMAIL("Blackmail a Burgomaster", Category.SHADY, "written_book", 2, 3, new long[] {800, 1600, 3200}, 1.8, -1, 0.45, 0.60, false,
		Special.NONE, "The burgomaster has a second family in Haarlem. He'd hate for anyone to find out."),
	CLIP_THE_COIN("Clip the Coin", Category.SHADY, "gold_nugget", 2, 3, new long[] {1000, 2000, 4000}, 2.0, -1, 0.40, 0.55, false, Special.NONE,
		"Shave a little silver off every guilder that passes through. Nobody notices. Usually."),

	// ---------------------------------------------------------------- only from the Ledger and the agents page
	COLLECT_DEBT("Collect a Debt", Category.SPECIAL, "iron_axe", 0, 1, new long[] {50, 150, 400}, 0, 0.70, 0.55, 0.60, false, Special.COLLECT,
		"Somebody owes the Compagnie money. Remind them."),
	RESCUE("Rescue an Agent", Category.SPECIAL, "iron_bars", 0, 2, new long[] {200, 500, 1200}, 0, 0.60, 0.50, 0.65, false, Special.RESCUE,
		"One of ours is in a cell somewhere. Get them out, cheaper than the ransom. Or not.");

	enum Category {
		TRADE("Trade", "map"),
		RAID("Raid", "iron_sword"),
		EXPLORE("Explore", "compass"),
		SHADY("Shady", "ender_eye"),
		SPECIAL("Special", "paper");

		final String title;
		final String icon;

		Category(String title, String icon) {
			this.title = title;
			this.icon = icon;
		}
	}

	enum Special {
		NONE, CHART, CORNER, RIVAL, HARBOUR, REP, RECRUIT, COLLECT, RESCUE, EXPORT, CHARTER, OIL, SACKS
	}

	/** An item that comes back from a job: so many of it, with this chance, maybe only on a triumph. */
	record Spoil(String item, int min, int max, double chance, boolean triumphOnly) {
		Spoil(String item, int min, int max) {
			this(item, min, max, 1.0, false);
		}

		Spoil(String item, int min, int max, double chance) {
			this(item, min, max, chance, false);
		}
	}

	final String title;
	final Category category;
	final String icon;
	/** Rank#ordinal() needed before it shows up on the board. */
	final int minRank;
	final int days;
	/** Lean, standard and lavish, in Marks. */
	final long[] stakes;
	/** On success, the stake comes back times this. 0: the job pays in something else. */
	final double mult;
	/** Base chance of success by Approach#ordinal(); below 0 means that advisor won't touch it. */
	final double[] odds;
	final boolean sea;
	final Special special;
	final String text;
	final List<Spoil> spoils;
	/** CHART jobs: the structures (by id) it may find, in order of preference. */
	final List<String> charts;

	JobType(String title, Category category, String icon, int minRank, int days, long[] stakes, double mult, double force, double coin,
		double guile, boolean sea, Special special, String text, Spoil... spoils) {
		this(title, category, icon, minRank, days, stakes, mult, force, coin, guile, sea, special, text, List.of(spoils), List.of());
	}

	JobType(String title, Category category, String icon, int minRank, int days, long[] stakes, double mult, double force, double coin,
		double guile, boolean sea, Special special, String text, List<Spoil> spoils, List<String> charts) {
		this.title = title;
		this.category = category;
		this.icon = icon;
		this.minRank = minRank;
		this.days = days;
		this.stakes = stakes;
		this.mult = mult;
		this.odds = new double[] {force, coin, guile};
		this.sea = sea;
		this.special = special;
		this.text = text;
		this.spoils = spoils;
		this.charts = charts;
	}

	String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	boolean allows(Approach a) {
		return odds[a.ordinal()] >= 0;
	}

	/** Trade jobs and sea voyages: what the harbourmaster, the rival and cornering the market affect. */
	boolean trade() {
		return category == Category.TRADE;
	}

	/** Does it show up on the job board? (Collecting debts and rescues are started from elsewhere.) */
	boolean onBoard() {
		return category != Category.SPECIAL;
	}

	/** Jobs that need another mod only show up when it's there. */
	boolean available() {
		return switch (this) {
			case LUXURY_EXPORT -> Links.loaded("proost") || Links.loaded("havana");
			case CHARTER_SHIP -> Links.loaded("ahoy") || Links.loaded("blimey");
			case OIL_SURVEY -> Links.loaded("fossilfool");
			case LOST_COLONY -> Links.loaded("burlapsack");
			default -> true;
		};
	}

	/** Jobs that carry something from the player's inventory. */
	boolean carries() {
		return special == Special.EXPORT || special == Special.CHARTER;
	}

	static JobType byId(String id) {
		for (JobType t : values()) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}
}
