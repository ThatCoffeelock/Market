package com.thatcoffeelock.ahoy;

import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Speed upgrades. A ship's rigging goes from level 0 (as launched) to level 3; each level raises its top speed
 * (and how quickly it gets there). The owner pays for them in materials from their own pockets; creative players
 * get them for free. The level is part of the ship's data, so it stays when the ship is bottled up.
 */
final class Shipwright {
	/** One price line: so many of this item. */
	record Cost(Item item, int count, String name) {
	}

	/** One upgrade: what it's called, what it costs, and the speed multiplier once it's done. */
	record Upgrade(String name, String blurb, double factor, List<Cost> costs) {
	}

	/** Index = level. Level 0 is the ship as it comes out of the bottle. */
	static final List<Upgrade> LEVELS = List.of(
		new Upgrade("Standard rigging", "As she came out of the bottle.", 1.0, List.of()),
		new Upgrade("Extra canvas", "Bigger sails catch more wind.", 1.2,
			List.of(new Cost(Items.WOOL.white(), 16, "White Wool"), new Cost(Items.STRING, 8, "String"))),
		new Upgrade("Copper sheathing", "A smooth copper hull slips through the water.", 1.4,
			List.of(new Cost(Items.COPPER_INGOT, 32, "Copper Ingot"), new Cost(Items.WOOL.white(), 8, "White Wool"))),
		new Upgrade("Clipper rigging", "Featherlight sails of phantom membrane. Fastest thing afloat.", 1.6,
			List.of(new Cost(Items.PHANTOM_MEMBRANE, 8, "Phantom Membrane"), new Cost(Items.DIAMOND, 2, "Diamond"))));

	static final int MAX_LEVEL = LEVELS.size() - 1;

	private Shipwright() {
	}

	static int clamp(int level) {
		return Math.max(0, Math.min(MAX_LEVEL, level));
	}

	static double factor(int level) {
		return LEVELS.get(clamp(level)).factor();
	}

	static String roman(int level) {
		return switch (clamp(level)) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> "–";
		};
	}

	/** The next upgrade for this ship, or null when it's fully rigged. */
	static @Nullable Upgrade next(ShipData data) {
		int level = clamp(data.speedLevel);
		return level >= MAX_LEVEL ? null : LEVELS.get(level + 1);
	}

	static int count(Player player, Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(Player player, Item item, int amount) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				int used = Math.min(amount, stack.getCount());
				stack.shrink(used);
				amount -= used;
			}
		}
		inv.setChanged();
	}

	/** Can this player pay for the next upgrade right now? */
	static boolean canAfford(Player player, Upgrade upgrade) {
		if (player.isCreative()) {
			return true;
		}
		for (Cost cost : upgrade.costs()) {
			if (count(player, cost.item()) < cost.count()) {
				return false;
			}
		}
		return true;
	}

	/** Buys the next upgrade. Returns null when it worked, otherwise why not. */
	static @Nullable String upgrade(Player player, Ship ship) {
		if (!ship.isOwner(player) && !player.isCreative()) {
			return "Only the captain can have the ship refitted.";
		}
		Upgrade next = next(ship.data);
		if (next == null) {
			return "She's already as fast as a ship can be.";
		}
		if (!canAfford(player, next)) {
			StringBuilder missing = new StringBuilder("You need:");
			for (Cost cost : next.costs()) {
				missing.append(' ').append(cost.count()).append(' ').append(cost.name()).append(',');
			}
			missing.setLength(missing.length() - 1);
			return missing.toString();
		}
		if (!player.isCreative()) {
			for (Cost cost : next.costs()) {
				take(player, cost.item(), cost.count());
			}
		}
		ship.data.speedLevel = clamp(ship.data.speedLevel + 1);
		return null;
	}
}
