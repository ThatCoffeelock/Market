package com.thatcoffeelock.specerij;

import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * The six spices. Each one grows only in its home biomes (or in a {@link Hortus}, at half speed), so the good ones
 * are far away: that's the point. To a vanilla client a growing plant is short grass, and a ripe one a vanilla
 * plant that looks the part.
 */
enum Spice {
	PEPPER("Pepper", Set.of("jungle", "sparse_jungle"), "minecraft:sweet_berry_bush[age=3]", "jungle_sapling",
		"Green Peppercorns", "sweet_berries", "Black Pepper", "black_dye", 3, 5, 0.75, 3,
		"The spice that paid for the Golden Age. Bites back."),
	NUTMEG("Nutmeg", Set.of("mangrove_swamp"), "minecraft:flowering_azalea", "mangrove_propagule",
		"Nutmeg Fruit", "apple", "Nutmeg", "cocoa_beans", 2, 3, 1.5, 6,
		"In 1667 the Dutch swapped Manhattan for an island of these. They thought they'd won."),
	CLOVES("Cloves", Set.of("bamboo_jungle"), "minecraft:torchflower", "pitcher_pod",
		"Clove Buds", "torchflower_seeds", "Cloves", "brown_dye", 3, 4, 0.9, 3.5,
		"Picked as buds, before they flower. Patience, but not too much."),
	CINNAMON("Cinnamon", Set.of("savanna", "savanna_plateau", "windswept_savanna"), "minecraft:dead_bush", "acacia_sapling",
		"Cinnamon Bark", "dead_bush", "Cinnamon Quills", "stick", 2, 4, 1, 4,
		"Bark off a tree, rolled up and dried. Somebody was very hungry once."),
	TEA("Tea", Set.of("cherry_grove", "windswept_hills", "windswept_forest"), "minecraft:fern", "cherry_sapling",
		"Tea Leaves", "fern", "Green Tea", "kelp", 4, 6, 0.4, 1.5,
		"Dry it for green tea. Leave it two more days for black."),
	SAFFRON("Saffron", Set.of("meadow", "flower_forest"), "minecraft:allium", "brown_mushroom",
		"Saffron Crocus", "allium", "Saffron", "red_dye", 1, 2, 3, 15,
		"Three threads per flower. Worth more than its weight in gold, and it knows it.");

	final String title;
	/** Biome ids (minecraft: namespace, path only) it grows in on its own. */
	final Set<String> homes;
	/** The block state a ripe plant is. */
	final String ripe;
	final String cuttingModel;
	final String freshName;
	final String freshModel;
	final String driedName;
	final String driedModel;
	/** Fresh spice from one harvest. */
	final int harvestMin;
	final int harvestMax;
	/** Market price, in Marks, of one fresh and one dried. */
	final double freshPrice;
	final double driedPrice;
	final String story;

	Spice(String title, Set<String> homes, String ripe, String cuttingModel, String freshName, String freshModel, String driedName,
		String driedModel, int harvestMin, int harvestMax, double freshPrice, double driedPrice, String story) {
		this.title = title;
		this.homes = homes;
		this.ripe = ripe;
		this.cuttingModel = cuttingModel;
		this.freshName = freshName;
		this.freshModel = freshModel;
		this.driedName = driedName;
		this.driedModel = driedModel;
		this.harvestMin = harvestMin;
		this.harvestMax = harvestMax;
		this.freshPrice = freshPrice;
		this.driedPrice = driedPrice;
		this.story = story;
	}

	String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The ripe block's id without its properties: "minecraft:sweet_berry_bush". */
	String ripeBlock() {
		int bracket = ripe.indexOf('[');
		return bracket < 0 ? ripe : ripe.substring(0, bracket);
	}

	/** Black tea: only tea ages past dried. Its price. */
	static final double BLACK_TEA_PRICE = 3;

	/** "jungle, sparse jungle" */
	String homeNames() {
		return String.join(", ", homes.stream().sorted().map(h -> h.replace('_', ' ')).toList());
	}

	static @Nullable Spice byId(String id) {
		for (Spice s : values()) {
			if (s.id().equalsIgnoreCase(id)) {
				return s;
			}
		}
		return null;
	}

	/** The spice that grows wild in this biome ("minecraft:jungle" or "jungle"), or null. */
	static @Nullable Spice forBiome(String biome) {
		String path = biome.substring(biome.indexOf(':') + 1);
		for (Spice s : values()) {
			if (s.homes.contains(path)) {
				return s;
			}
		}
		return null;
	}
}
