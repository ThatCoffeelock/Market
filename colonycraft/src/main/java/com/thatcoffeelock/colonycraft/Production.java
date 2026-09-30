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

	static final List<Recipe> WORKSHOP = List.of(
		new Recipe(Items.OAK_LOG, 1, Items.OAK_PLANKS, 4), new Recipe(Items.SPRUCE_LOG, 1, Items.SPRUCE_PLANKS, 4),
		new Recipe(Items.BIRCH_LOG, 1, Items.BIRCH_PLANKS, 4), new Recipe(Items.RAW_IRON, 1, Items.IRON_INGOT, 1),
		new Recipe(Items.RAW_COPPER, 1, Items.COPPER_INGOT, 1), new Recipe(Items.RAW_GOLD, 1, Items.GOLD_INGOT, 1),
		new Recipe(Items.COBBLESTONE, 1, Items.STONE, 1), new Recipe(Items.WHEAT, 3, Items.BREAD, 1),
		new Recipe(Items.POTATO, 1, Items.BAKED_POTATO, 1));
	/** How many input items one workshop worker gets through in a day. */
	static final int WORKSHOP_PER_WORKER = 64;

	private Production() {
	}

	static double tierBonus(int tier) {
		return tier <= 1 ? 1.0 : tier == 2 ? 1.5 : 2.0;
	}

	/** Raw goods from farms, lumber camps and mines. */
	static List<ItemStack> gather(BuildingType type, int tier, int workers, Random random) {
		List<Yield> yields = switch (type) {
			case FARM -> FARM;
			case LUMBER_CAMP -> LUMBER;
			case MINE -> MINE;
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
