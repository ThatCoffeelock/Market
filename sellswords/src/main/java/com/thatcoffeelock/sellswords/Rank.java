package com.thatcoffeelock.sellswords;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The ranks. Everybody starts as a Recruit with a crossbow and a sword. The first promotion picks a path for good:
 * the Ranged path ends as a Musketeer (one huge shot, slow to reload), the Melee path as a Foestopper Bulwark (a wall
 * of netherite with a shield who drags monsters off everyone else). Every step costs gold ingots, doubling each time.
 */
public enum Rank {
	//            path         tier title                   hp  armor tough melee ranged cd  range  kb    regen speed gold  shield  icon
	RECRUIT(Path.NONE, 0, "Recruit", 24, 4, 0, 5, 4, 30, 16, 0.0, 1, 0.30, 0, false, Items.WOODEN_SWORD),
	CROSSBOWMAN(Path.RANGED, 1, "Crossbowman", 26, 6, 0, 5, 6, 26, 20, 0.0, 1, 0.30, 5, false, Items.CROSSBOW),
	MARKSMAN(Path.RANGED, 2, "Marksman", 30, 8, 0, 6, 8, 24, 24, 0.1, 1, 0.31, 10, false, Items.SPYGLASS),
	SHARPSHOOTER(Path.RANGED, 3, "Sharpshooter", 34, 10, 1, 6, 10, 22, 28, 0.1, 2, 0.31, 20, false, Items.TARGET),
	MUSKETEER(Path.RANGED, 4, "Musketeer", 40, 10, 2, 7, 20, 50, 36, 0.2, 2, 0.31, 40, false, Items.FIREWORK_STAR),
	SWORDSMAN(Path.MELEE, 1, "Swordsman", 32, 9, 0, 7, 4, 34, 14, 0.2, 1, 0.31, 5, false, Items.IRON_SWORD),
	MAN_AT_ARMS(Path.MELEE, 2, "Man-at-Arms", 40, 13, 2, 9, 4, 34, 14, 0.3, 2, 0.31, 10, false, Items.IRON_CHESTPLATE),
	VANGUARD(Path.MELEE, 3, "Vanguard", 52, 16, 4, 11, 0, 0, 0, 0.5, 2, 0.32, 20, true, Items.SHIELD),
	BULWARK(Path.MELEE, 4, "Foestopper Bulwark", 72, 20, 8, 14, 0, 0, 0, 1.0, 3, 0.30, 40, true, Items.NETHERITE_CHESTPLATE);

	public enum Path {
		NONE("Recruit", ChatFormatting.WHITE),
		RANGED("Ranged", ChatFormatting.AQUA),
		MELEE("Melee", ChatFormatting.RED);

		public final String label;
		public final ChatFormatting color;

		Path(String label, ChatFormatting color) {
			this.label = label;
			this.color = color;
		}
	}

	/** Leather dyed Dutch orange, and brown and black for trousers and boots. */
	static final int ORANGE = 0xF9801D;
	static final int BROWN = 0x835432;
	static final int BLACK = 0x1D1D21;

	public final Path path;
	/** 0 for a recruit, 1 to 4 along a path. */
	public final int tier;
	public final String title;
	public final double health;
	public final double armor;
	public final double toughness;
	public final float melee;
	/** Damage of one crossbow bolt or musket ball; 0 for the shield-bearers, who don't shoot. */
	public final float ranged;
	/** Ticks between shots. */
	public final int shootCooldown;
	/** How far they shoot, in blocks. */
	public final double range;
	public final double knockbackResistance;
	/** Health back every five seconds. */
	public final float regen;
	public final double speed;
	/** Gold ingots to be promoted to this rank. */
	public final int gold;
	/** Sword and shield: no crossbow, and frontal hits are partly blocked. */
	public final boolean shield;
	public final Item icon;

	Rank(Path path, int tier, String title, double health, double armor, double toughness, float melee, float ranged, int shootCooldown,
		double range, double knockbackResistance, float regen, double speed, int gold, boolean shield, Item icon) {
		this.path = path;
		this.tier = tier;
		this.title = title;
		this.health = health;
		this.armor = armor;
		this.toughness = toughness;
		this.melee = melee;
		this.ranged = ranged;
		this.shootCooldown = shootCooldown;
		this.range = range;
		this.knockbackResistance = knockbackResistance;
		this.regen = regen;
		this.speed = speed;
		this.gold = gold;
		this.shield = shield;
		this.icon = icon;
	}

	public boolean shoots() {
		return ranged > 0;
	}

	public boolean musket() {
		return this == MUSKETEER;
	}

	/** Ticks between sword blows. */
	public int meleeCooldown() {
		return this == BULWARK ? 22 : path == Path.MELEE ? 18 : 20;
	}

	/** Share of a hit from the front a shield soaks up. */
	public double block() {
		return this == BULWARK ? 0.6 : this == VANGUARD ? 0.4 : 0;
	}

	/** Bulwarks shout monsters off everyone else and onto themselves. */
	public boolean taunts() {
		return this == BULWARK;
	}

	/** Musket balls go through the first thing they hit into the next. */
	public int pierce() {
		return this == MUSKETEER ? 1 : 0;
	}

	/** The rank after this one on a path, or null at the top (or for a recruit, who has two). */
	public @Nullable Rank next() {
		for (Rank r : values()) {
			if (path != Path.NONE && r.path == path && r.tier == tier + 1) {
				return r;
			}
		}
		return null;
	}

	/** The ranks of a path, in order. */
	public static List<Rank> of(Path path) {
		List<Rank> list = new ArrayList<>();
		for (Rank r : values()) {
			if (r.path == path) {
				list.add(r);
			}
		}
		return list;
	}

	/** Can a mercenary of this rank be promoted straight to {@code to}? */
	public boolean canBecome(Rank to) {
		if (this == RECRUIT) {
			return to.tier == 1;
		}
		return to.path == path && to.tier == tier + 1;
	}

	public static Rank byName(@Nullable String name) {
		if (name != null) {
			for (Rank r : values()) {
				if (r.name().equalsIgnoreCase(name)) {
					return r;
				}
			}
		}
		return RECRUIT;
	}

	public String stars() {
		return "★".repeat(tier) + "☆".repeat(4 - tier);
	}

	/** One line about what this rank is good at. */
	public String blurb() {
		return switch (this) {
			case RECRUIT -> "A crossbow, a sword and a lot of enthusiasm.";
			case CROSSBOWMAN -> "Shoots further and faster than a recruit.";
			case MARKSMAN -> "Hits harder from further away.";
			case SHARPSHOOTER -> "A steady hand: hard bolts at long range.";
			case MUSKETEER -> "A musket: one enormous shot that goes through the first thing it hits. Slow to reload.";
			case SWORDSMAN -> "Closes in with the sword. Still carries a crossbow.";
			case MAN_AT_ARMS -> "Iron from head to toe, and the sword arm to match.";
			case VANGUARD -> "Trades the crossbow for a shield that blocks 40% of hits from the front.";
			case BULWARK -> "Netherite and a shield that blocks 60% from the front. Can't be knocked back, and shouts every monster nearby off you and onto himself.";
		};
	}

	// ---------------------------------------------------------------- gear (what the body wears; it's never dropped)

	/** The sword in their hand. */
	public Item sword() {
		return switch (this) {
			case VANGUARD -> Items.DIAMOND_SWORD;
			case BULWARK -> Items.NETHERITE_SWORD;
			default -> Items.IRON_SWORD;
		};
	}

	/** One armour piece: the plain item (always works) and the same with dye or trim as item-argument components. */
	record Piece(String slot, Item item, String components) {
	}

	private static String dyed(int rgb) {
		return "dyed_color=" + rgb;
	}

	private static String trim(String pattern, String material) {
		return "trim={pattern:\"minecraft:" + pattern + "\",material:\"minecraft:" + material + "\"}";
	}

	/** Head, chest, legs and feet. */
	List<Piece> armour() {
		return switch (this) {
			case RECRUIT -> List.of(
				new Piece("armor.chest", Items.LEATHER_CHESTPLATE, dyed(ORANGE)),
				new Piece("armor.legs", Items.LEATHER_LEGGINGS, dyed(BROWN)),
				new Piece("armor.feet", Items.LEATHER_BOOTS, dyed(BROWN)));
			case CROSSBOWMAN -> List.of(
				new Piece("armor.head", Items.LEATHER_HELMET, dyed(ORANGE)),
				new Piece("armor.chest", Items.LEATHER_CHESTPLATE, dyed(ORANGE)),
				new Piece("armor.legs", Items.CHAINMAIL_LEGGINGS, ""),
				new Piece("armor.feet", Items.LEATHER_BOOTS, dyed(BROWN)));
			case MARKSMAN -> List.of(
				new Piece("armor.head", Items.LEATHER_HELMET, dyed(ORANGE)),
				new Piece("armor.chest", Items.CHAINMAIL_CHESTPLATE, ""),
				new Piece("armor.legs", Items.CHAINMAIL_LEGGINGS, ""),
				new Piece("armor.feet", Items.LEATHER_BOOTS, dyed(BROWN)));
			case SHARPSHOOTER -> List.of(
				new Piece("armor.head", Items.CHAINMAIL_HELMET, ""),
				new Piece("armor.chest", Items.CHAINMAIL_CHESTPLATE, trim("sentry", "gold")),
				new Piece("armor.legs", Items.CHAINMAIL_LEGGINGS, ""),
				new Piece("armor.feet", Items.IRON_BOOTS, ""));
			case MUSKETEER -> List.of(
				new Piece("armor.head", Items.LEATHER_HELMET, dyed(BLACK)),
				new Piece("armor.chest", Items.LEATHER_CHESTPLATE, dyed(ORANGE) + "," + trim("wayfinder", "gold")),
				new Piece("armor.legs", Items.LEATHER_LEGGINGS, dyed(BLACK) + "," + trim("wayfinder", "gold")),
				new Piece("armor.feet", Items.LEATHER_BOOTS, dyed(BLACK)));
			case SWORDSMAN -> List.of(
				new Piece("armor.head", Items.LEATHER_HELMET, dyed(ORANGE)),
				new Piece("armor.chest", Items.IRON_CHESTPLATE, ""),
				new Piece("armor.legs", Items.LEATHER_LEGGINGS, dyed(BROWN)),
				new Piece("armor.feet", Items.LEATHER_BOOTS, dyed(BROWN)));
			case MAN_AT_ARMS -> List.of(
				new Piece("armor.head", Items.IRON_HELMET, ""),
				new Piece("armor.chest", Items.IRON_CHESTPLATE, ""),
				new Piece("armor.legs", Items.IRON_LEGGINGS, ""),
				new Piece("armor.feet", Items.IRON_BOOTS, ""));
			case VANGUARD -> List.of(
				new Piece("armor.head", Items.IRON_HELMET, trim("ward", "gold")),
				new Piece("armor.chest", Items.IRON_CHESTPLATE, trim("ward", "gold")),
				new Piece("armor.legs", Items.IRON_LEGGINGS, trim("ward", "gold")),
				new Piece("armor.feet", Items.IRON_BOOTS, trim("ward", "gold")));
			case BULWARK -> List.of(
				new Piece("armor.head", Items.NETHERITE_HELMET, trim("silence", "gold")),
				new Piece("armor.chest", Items.NETHERITE_CHESTPLATE, trim("silence", "gold")),
				new Piece("armor.legs", Items.NETHERITE_LEGGINGS, trim("silence", "gold")),
				new Piece("armor.feet", Items.NETHERITE_BOOTS, trim("silence", "gold")));
		};
	}
}
