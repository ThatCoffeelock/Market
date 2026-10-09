package com.thatcoffeelock.skills;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * The twenty-one skills. Every level gives a small passive bonus ({@link #perLevel} percent per level, so level 100 is
 * 100x that), and every 10 levels gives one perk point to spend in this skill's own perks. A maxed skill (passive +
 * all the perks you can afford) makes you roughly twice as good at that thing as an unskilled player.
 */
public enum Skill {
	COMBAT("Combat", "iron_sword", ChatFormatting.RED, 0.25,
		"Melee damage dealt to mobs and players.",
		"+%s melee damage"),
	MARKSMANSHIP("Marksmanship", "bow", ChatFormatting.GOLD, 0.25,
		"Bow, crossbow, trident and Flintlock gun damage. Long shots count extra.",
		"+%s ranged damage"),
	MINING("Mining", "iron_pickaxe", ChatFormatting.GRAY, 0.3,
		"Breaking natural stone and ores. Rarer ore, more XP.",
		"+%s pickaxe speed"),
	WOODCUTTING("Woodcutting", "iron_axe", ChatFormatting.DARK_GREEN, 0.3,
		"Chopping natural logs.",
		"+%s axe speed"),
	EXCAVATION("Excavation", "iron_shovel", ChatFormatting.YELLOW, 0.3,
		"Digging natural dirt, sand, gravel, clay, snow and soul sand.",
		"+%s shovel speed"),
	FARMING("Farming", "golden_hoe", ChatFormatting.GREEN, 0.3,
		"Harvesting ripe crops, melons and pumpkins, and breeding animals.",
		"%s chance of a double harvest"),
	FISHING("Fishing", "fishing_rod", ChatFormatting.AQUA, 0.25,
		"Catching things with a fishing rod.",
		"%s chance of a bonus fish"),
	HORSERIDING("Horseriding", "saddle", ChatFormatting.GOLD, 0.2,
		"Distance ridden on horses, donkeys, camels, pigs and striders.",
		"+%s mount speed"),
	SAILING("Sailing", "oak_boat", ChatFormatting.BLUE, 0.25,
		"Distance travelled by boat, raft or ship.",
		"+%s swim speed"),
	ENCHANTING("Enchanting", "enchanting_table", ChatFormatting.LIGHT_PURPLE, 0.3,
		"Enchanting items at an enchanting table.",
		"%s chance to get the levels back"),
	BLACKSMITHING("Blacksmithing", "anvil", ChatFormatting.DARK_GRAY, 0.25,
		"Crafting tools, weapons and armor, and smithing upgrades.",
		"%s chance your gear takes no wear"),
	BREWING("Brewing", "brewing_stand", ChatFormatting.DARK_PURPLE, 0.25,
		"Brewing potions (the last player to open the stand gets the XP).",
		"%s chance the ingredient isn't used up"),
	MERCANTILE("Mercantile", "emerald", ChatFormatting.DARK_AQUA, 0.1,
		"Trading with villagers and selling or buying at the Market.",
		"+%s Market sell prices"),
	WILDCATTING("Wildcatting", "bucket", ChatFormatting.DARK_RED, 0.25,
		"Striking, pumping and refining oil, and drilling with a Drill Rig (Fossil Fool).",
		"+%s fuel efficiency in Drill Rigs and Refineries"),
	TREASURE_HUNTING("Treasure Hunting", "filled_map", ChatFormatting.YELLOW, 0.3,
		"Opening structure chests nobody has opened yet, and finding Riches relics.",
		"%s chance of a bonus item in unopened structure chests"),
	BOUNTY_HUNTING("Bounty Hunting", "crossbow", ChatFormatting.DARK_RED, 0.3,
		"Killing illagers, and selling fingers and skulls at a Bounty Station.",
		"+%s damage against illagers"),
	ARTILLERY("Artillery", "tnt", ChatFormatting.RED, 0.3,
		"Firing cannons, and catching mobs in the blast.",
		"%s faster cannon reloads"),
	GOVERNANCE("Governance", "bell", ChatFormatting.GOLD, 0.2,
		"Running a colony: paying wages, building, and dealing with prisoners.",
		"-%s colony wages"),
	CONNOISSEUR("Connoisseur", "brown_dye", ChatFormatting.DARK_GREEN, 0.5,
		"Growing tobacco, rolling cigars and smoking them.",
		"+%s cigar effect duration"),
	PILOTEERING("Piloteering", "elytra", ChatFormatting.AQUA, 0.3,
		"Flying a Blimey airship as its captain, dropping bombs from one, and steering a happy ghast.",
		"-%1$s airship diesel, +%1$s happy ghast speed"),
	LEADERSHIP("Leadership", "goat_horn", ChatFormatting.BLUE, 0.25,
		"Leading Sellswords mercenaries: hiring them, promoting them, and every kill they make.",
		"+%s mercenary health, and room for 1 more mercenary per 25 levels");

	public static final int MAX_LEVEL = 100;
	/** One perk point per this many levels: 10 points at level 100. */
	public static final int LEVELS_PER_POINT = 10;

	public final String title;
	public final String icon;
	public final ChatFormatting color;
	/** Passive bonus in percent per level. */
	public final double perLevel;
	public final String xpFrom;
	private final String passive;

	Skill(String title, String icon, ChatFormatting color, double perLevel, String xpFrom, String passive) {
		this.title = title;
		this.icon = icon;
		this.color = color;
		this.perLevel = perLevel;
		this.xpFrom = xpFrom;
		this.passive = passive;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The passive bonus at a level, as a fraction (0.25 = 25%). */
	public double passive(int level) {
		return perLevel * level / 100.0;
	}

	public String passiveText(int level) {
		return String.format(passive, pct(passive(level)));
	}

	public static @Nullable Skill byId(String id) {
		for (Skill skill : values()) {
			if (skill.id().equalsIgnoreCase(id)) {
				return skill;
			}
		}
		return null;
	}

	static String pct(double fraction) {
		double p = fraction * 100.0;
		return (p == Math.rint(p) ? String.format(Locale.ROOT, "%d", (long) p) : String.format(Locale.ROOT, "%.1f", p)) + "%";
	}

	// ---------------------------------------------------------------- the XP curve

	/** TOTAL[n] = XP needed to reach level n. Each level costs 4.5% more than the one before. */
	private static final double[] TOTAL = new double[MAX_LEVEL + 1];

	static {
		for (int n = 1; n <= MAX_LEVEL; n++) {
			TOTAL[n] = TOTAL[n - 1] + xpForLevel(n);
		}
	}

	/** XP to go from level n-1 to level n. Level 1 costs 100, level 50 about 860, level 100 about 7,800. */
	public static double xpForLevel(int n) {
		return 100.0 * Math.pow(1.045, n - 1);
	}

	public static double totalFor(int level) {
		return TOTAL[Math.max(0, Math.min(MAX_LEVEL, level))];
	}

	public static int levelFor(double xp) {
		int level = 0;
		while (level < MAX_LEVEL && xp >= TOTAL[level + 1]) {
			level++;
		}
		return level;
	}

	/** Progress towards the next level, 0..1 (1 at max level). */
	public static double progress(double xp) {
		int level = levelFor(xp);
		if (level >= MAX_LEVEL) {
			return 1.0;
		}
		return (xp - TOTAL[level]) / (TOTAL[level + 1] - TOTAL[level]);
	}
}
