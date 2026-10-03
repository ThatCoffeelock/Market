package com.thatcoffeelock.overenchant;

import net.minecraft.world.item.enchantment.Enchantment;

/**
 * What an enchantment costs at the enchanting table. An enchantment's cost grows in a straight line with its level, and
 * the table can only reach so high, so once the maximum goes up the new top levels would be out of reach. With
 * {@link OverenchantConfig#compressCosts} the whole range of levels is squeezed into the old cost range: the new top
 * level costs what the old top level did.
 */
public final class OverenchantCosts {
	private OverenchantCosts() {
	}

	/** The cost of {@code level}, given what vanilla's own formula says. {@code max}: the top of the range rather than the bottom. */
	public static int cost(Enchantment enchantment, int level, boolean max, int vanilla) {
		if (!OverenchantConfig.get().compressCosts) {
			return vanilla;
		}
		int oldMax = enchantment.definition().maxLevel();
		int newMax = enchantment.getMaxLevel();
		if (newMax <= oldMax || oldMax <= 1 || level <= 1) {
			return vanilla;
		}
		Enchantment.Cost cost = max ? enchantment.definition().maxCost() : enchantment.definition().minCost();
		// level newMax lands on oldMax, level 1 stays on 1, and everything in between is spread evenly
		double effective = 1 + (level - 1) * (oldMax - 1.0) / (newMax - 1.0);
		return (int) Math.round(cost.base() + cost.perLevelAboveFirst() * (effective - 1));
	}
}
