package com.thatcoffeelock.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Three perks per skill, five ranks each, one perk point per rank. A skill only ever earns 10 points, so you can
 * max two perks or spread out, but never take everything: that's the specialising part.
 * {@link #perRank} is the effect of one rank (a fraction, or a count for the "up to N blocks" perks).
 */
public enum Perk {
	// Combat
	BRUTE(Skill.COMBAT, "Brute", "iron_axe", 0.05, "+%s melee damage"),
	EXECUTIONER(Skill.COMBAT, "Executioner", "wither_skeleton_skull", 0.06, "+%s melee damage against targets below 30%% health"),
	THICK_SKIN(Skill.COMBAT, "Thick Skin", "leather_chestplate", 0.03, "-%s damage taken from mobs"),

	// Marksmanship
	EAGLE_EYE(Skill.MARKSMANSHIP, "Eagle Eye", "spyglass", 0.05, "+%s ranged damage"),
	LONG_SHOT(Skill.MARKSMANSHIP, "Long Shot", "target", 0.06, "+%s ranged damage on hits from 20+ blocks away"),
	RECOVERY(Skill.MARKSMANSHIP, "Recovery", "arrow", 0.10, "%s chance to get your arrow back when it hits"),

	// Mining
	PROSPECTOR(Skill.MINING, "Prospector", "raw_gold", 0.05, "%s chance of double drops from ores"),
	VEIN_MINER(Skill.MINING, "Vein Miner", "diamond_ore", 2, "Sneak-mine an ore to also mine up to %s connected ores of the same kind"),
	DEEP_DELVER(Skill.MINING, "Deep Delver", "deepslate", 0.06, "+%s pickaxe speed below Y=0"),

	// Woodcutting
	LUMBERJACK(Skill.WOODCUTTING, "Lumberjack", "oak_log", 0.05, "%s chance of double logs"),
	TIMBER(Skill.WOODCUTTING, "Timber", "golden_axe", 8, "Sneak-chop a log to also fell up to %s logs above it"),
	FORESTER(Skill.WOODCUTTING, "Forester", "oak_sapling", 0.20, "%s chance to replant a sapling when you chop the bottom log"),

	// Excavation
	TREASURE_HUNTER(Skill.EXCAVATION, "Treasure Hunter", "brush", 0.004, "%s chance per block to dig up a small treasure"),
	BULK_DIG(Skill.EXCAVATION, "Bulk Dig", "gravel", 0.05, "%s chance of double drops from dirt, sand, gravel and clay"),
	MOLE(Skill.EXCAVATION, "Mole", "rooted_dirt", 0.06, "+%s shovel speed"),

	// Farming
	GREEN_THUMB(Skill.FARMING, "Green Thumb", "wheat_seeds", 0.20, "%s chance to replant a crop as you harvest it"),
	RANCHER(Skill.FARMING, "Rancher", "wheat", 0.08, "%s chance of twins when you breed animals"),
	BUTCHER(Skill.FARMING, "Butcher", "porkchop", 0.08, "%s chance of double drops from animals you kill"),

	// Fishing
	ANGLER(Skill.FISHING, "Angler", "cod", 0.05, "+%s chance of a bonus fish"),
	TREASURE_SENSE(Skill.FISHING, "Treasure Sense", "nautilus_shell", 0.01, "%s chance per catch of a bonus treasure"),
	LUCKY_CHARM(Skill.FISHING, "Lucky Charm", "rabbit_foot", 0.4, "+%s Luck (better fishing loot, like Luck of the Sea)"),

	// Horseriding
	BONDED(Skill.HORSERIDING, "Bonded", "golden_carrot", 0.10, "Your mount takes %s less damage while you ride it"),
	JUMPER(Skill.HORSERIDING, "Jumper", "rabbit_hide", 0.04, "+%s mount jump strength"),
	CAVALRY(Skill.HORSERIDING, "Cavalry", "iron_horse_armor", 0.05, "+%s melee damage while mounted"),

	// Sailing
	SEA_LEGS(Skill.SAILING, "Sea Legs", "heart_of_the_sea", 0.10, "-%s drowning damage"),
	MARINER(Skill.SAILING, "Mariner", "oak_boat", 0.05, "+%s chance of a bonus fish while fishing from a boat"),
	DEEP_LUNGS(Skill.SAILING, "Deep Lungs", "turtle_helmet", 0.2, "+%s Oxygen (hold your breath longer, like Respiration)"),

	// Enchanting
	LAPIS_SAVER(Skill.ENCHANTING, "Lapis Saver", "lapis_lazuli", 0.10, "%s chance to get your lapis back"),
	SCHOLAR(Skill.ENCHANTING, "Scholar", "experience_bottle", 0.05, "+%s experience from orbs"),
	MANA_WELL(Skill.ENCHANTING, "Mana Well", "lapis_block", 0.06, "+%s chance to get the levels back"),

	// Blacksmithing
	TEMPERED(Skill.BLACKSMITHING, "Tempered", "iron_ingot", 0.05, "+%s chance your gear takes no wear"),
	THRIFTY_SMITH(Skill.BLACKSMITHING, "Thrifty Smith", "iron_nugget", 0.08, "%s chance to get a material back when you craft gear"),
	ARMORER(Skill.BLACKSMITHING, "Armorer", "iron_chestplate", 0.03, "-%s damage taken while wearing 3+ armor pieces"),

	// Brewing
	THRIFTY_ALCHEMIST(Skill.BREWING, "Thrifty Alchemist", "nether_wart", 0.05, "+%s chance the ingredient isn't used up"),
	POTENT(Skill.BREWING, "Potent", "glowstone_dust", 0.08, "Potions you drink last %s longer"),
	IRON_STOMACH(Skill.BREWING, "Iron Stomach", "spider_eye", 0.10, "Harmful effects wear off %s faster"),

	// Mercantile
	HAGGLER(Skill.MERCANTILE, "Haggler", "gold_ingot", 0.02, "+%s Market sell prices"),
	BULK_BUYER(Skill.MERCANTILE, "Bulk Buyer", "chest", 0.02, "-%s Market buy prices"),
	SILVER_TONGUE(Skill.MERCANTILE, "Silver Tongue", "emerald", 0.05, "-%s villager trade prices"),

	// Wildcatting
	ROUGHNECK(Skill.WILDCATTING, "Roughneck", "iron_pickaxe", 0.06, "+%s Drill Rig speed"),
	REFINER(Skill.WILDCATTING, "Refiner", "blast_furnace", 0.05, "%s chance of a bonus bucket of diesel"),
	DOWSER(Skill.WILDCATTING, "Dowser", "stick", 8, "Your Dowsing Rod reaches %s blocks further");

	public static final int MAX_RANK = 5;

	public final Skill skill;
	public final String title;
	public final String icon;
	public final double perRank;
	private final String text;

	Perk(Skill skill, String title, String icon, double perRank, String text) {
		this.skill = skill;
		this.title = title;
		this.icon = icon;
		this.perRank = perRank;
		this.text = text;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Effect at a rank: a fraction for most perks, a whole number for the "up to N" ones. */
	public double value(int rank) {
		return perRank * rank;
	}

	public String describe(int rank) {
		double v = value(rank);
		String shown;
		if (perRank >= 1) {
			shown = String.valueOf((int) v);
		} else if (this == LUCKY_CHARM || this == DEEP_LUNGS) {
			shown = String.format(Locale.ROOT, "%.1f", v);
		} else {
			shown = Skill.pct(v);
		}
		return String.format(text, shown);
	}

	public static List<Perk> of(Skill skill) {
		List<Perk> list = new ArrayList<>();
		for (Perk perk : values()) {
			if (perk.skill == skill) {
				list.add(perk);
			}
		}
		return list;
	}

	public static @Nullable Perk byId(String id) {
		for (Perk perk : values()) {
			if (perk.id().equalsIgnoreCase(id)) {
				return perk;
			}
		}
		return null;
	}
}
