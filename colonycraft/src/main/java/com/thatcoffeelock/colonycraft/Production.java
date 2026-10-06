package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a day of work produces. Numbers are per worker per day; higher tiers make each worker more
 * productive on top of adding workers. There's a bit of luck in every harvest.
 */
final class Production {
	private record Yield(Item item, double perWorker) {
	}

	/** Workshop recipe: this many of the input become this many of the output. */
	record Recipe(Item input, int in, Item output, int out) {
	}

	private static final List<Yield> FARM = List.of(
		new Yield(Items.WHEAT, 48), new Yield(Items.CARROT, 24), new Yield(Items.POTATO, 24),
		new Yield(Items.BEETROOT, 16), new Yield(Items.PUMPKIN, 4), new Yield(Items.MELON_SLICE, 16));
	private static final List<Yield> LUMBER = List.of(
		new Yield(Items.OAK_LOG, 32), new Yield(Items.SPRUCE_LOG, 16), new Yield(Items.BIRCH_LOG, 16),
		new Yield(Items.STICK, 16), new Yield(Items.OAK_SAPLING, 2), new Yield(Items.APPLE, 2));
	private static final List<Yield> MINE = List.of(
		new Yield(Items.COBBLESTONE, 64), new Yield(Items.COAL, 16), new Yield(Items.RAW_IRON, 8),
		new Yield(Items.RAW_COPPER, 12), new Yield(Items.RAW_GOLD, 3), new Yield(Items.REDSTONE, 8),
		new Yield(Items.LAPIS_LAZULI, 6), new Yield(Items.DIAMOND, 0.25), new Yield(Items.EMERALD, 0.1));

	private static final List<Yield> FISHERY = List.of(
		new Yield(Items.COD, 24), new Yield(Items.SALMON, 12), new Yield(Items.TROPICAL_FISH, 2), new Yield(Items.PUFFERFISH, 1),
		new Yield(Items.INK_SAC, 3), new Yield(Items.KELP, 6), new Yield(Items.NAUTILUS_SHELL, 0.05));

	static final List<Recipe> WORKSHOP = List.of(
		new Recipe(Items.OAK_LOG, 1, Items.OAK_PLANKS, 4), new Recipe(Items.SPRUCE_LOG, 1, Items.SPRUCE_PLANKS, 4),
		new Recipe(Items.BIRCH_LOG, 1, Items.BIRCH_PLANKS, 4), new Recipe(Items.RAW_IRON, 1, Items.IRON_INGOT, 1),
		new Recipe(Items.RAW_COPPER, 1, Items.COPPER_INGOT, 1), new Recipe(Items.RAW_GOLD, 1, Items.GOLD_INGOT, 1),
		new Recipe(Items.COBBLESTONE, 1, Items.STONE, 1), new Recipe(Items.WHEAT, 3, Items.BREAD, 1),
		new Recipe(Items.POTATO, 1, Items.BAKED_POTATO, 1), new Recipe(Items.COD, 1, Items.COOKED_COD, 1),
		new Recipe(Items.SALMON, 1, Items.COOKED_SALMON, 1));
	/** How many input items one workshop worker gets through in a day. */
	static final int WORKSHOP_PER_WORKER = 64;

	private Production() {
	}

	static double tierBonus(int tier) {
		return tier <= 1 ? 1.0 : tier == 2 ? 1.5 : 2.0;
	}

	/** Tobacco leaves a planter brings in per day. */
	static final double TOBACCO_PER_WORKER = 12;

	/**
	 * A tobacco farm's day (with the Havana mod): leaves, the odd seed, and from tier 2 the curing barn turns some of
	 * the leaves into cured tobacco (two in five), from tier 3 some into aged tobacco too (one in six).
	 */
	static List<ItemStack> tobacco(int tier, int workers, Random random) {
		List<ItemStack> out = new ArrayList<>();
		if (workers <= 0 || !HavanaLink.present()) {
			return out;
		}
		int leaves = (int) Math.round(TOBACCO_PER_WORKER * workers * tierBonus(tier) * (0.8 + random.nextDouble() * 0.4));
		int cured = tier >= 2 ? leaves * 2 / 5 : 0;
		int aged = tier >= 3 ? leaves / 6 : 0;
		leaves -= cured + aged;
		add(out, "leaf", leaves);
		add(out, "cured", cured);
		add(out, "aged", aged);
		add(out, "seeds", random.nextInt(workers + 1));
		return out;
	}

	private static void add(List<ItemStack> out, String kind, int count) {
		while (count > 0) {
			int n = Math.min(count, 64);
			ItemStack stack = HavanaLink.tobacco(kind, n);
			if (stack.isEmpty()) {
				return;
			}
			out.add(stack);
			count -= n;
		}
	}

	/** Raw goods from farms, lumber camps and mines. */
	static List<ItemStack> gather(BuildingType type, int tier, int workers, Random random) {
		List<Yield> yields = switch (type) {
			case FARM -> FARM;
			case LUMBER_CAMP -> LUMBER;
			case MINE -> MINE;
			case FISHERY -> FISHERY;
			default -> List.of();
		};
		List<ItemStack> out = new ArrayList<>();
		if (workers <= 0) {
			return out;
		}
		for (Yield y : yields) {
			double expected = y.perWorker * workers * tierBonus(tier) * (0.8 + random.nextDouble() * 0.4);
			int count = (int) expected + (random.nextDouble() < expected - Math.floor(expected) ? 1 : 0);
			while (count > 0) {
				int n = Math.min(count, y.item.getDefaultMaxStackSize());
				out.add(new ItemStack(y.item, n));
				count -= n;
			}
		}
		return out;
	}
}
