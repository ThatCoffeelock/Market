package com.thatcoffeelock.ahoy;

import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Refits, bought by the captain with materials from their own pockets (creative players get them free). There are
 * three tracks: rigging (top speed), holds (extra cargo holds) and the canal drill (carve a waterway through land).
 * The levels are part of the ship's data, so they stay when the ship is bottled up.
 */
final class Shipwright {
	/** One price line: so many of this item. */
	record Cost(Item item, int count, String name) {
	}

	/** One refit: what it's called, what it does, and what it costs. */
	record Upgrade(String name, String blurb, List<Cost> costs) {
	}

	/** A line of refits. Index in {@code levels} = level; level 0 is the ship as it comes out of the bottle. */
	record Track(String title, Item icon, List<Upgrade> levels, ToIntFunction<ShipData> get, ObjIntConsumer<ShipData> set) {
		int max() {
			return levels.size() - 1;
		}

		int level(ShipData data) {
			return Math.max(0, Math.min(max(), get.applyAsInt(data)));
		}
	}

	static final Track SPEED = new Track("Rigging", Items.WOOL.white(), List.of(
		new Upgrade("Standard rigging", "As she came out of the bottle.", List.of()),
		new Upgrade("Extra canvas", "Bigger sails catch more wind. +20% top speed.",
			List.of(new Cost(Items.WOOL.white(), 16, "White Wool"), new Cost(Items.STRING, 8, "String"))),
		new Upgrade("Copper sheathing", "A smooth copper hull slips through the water. +40% top speed.",
			List.of(new Cost(Items.COPPER_INGOT, 32, "Copper Ingot"), new Cost(Items.WOOL.white(), 8, "White Wool"))),
		new Upgrade("Clipper rigging", "Featherlight sails of phantom membrane. +60% top speed.",
			List.of(new Cost(Items.PHANTOM_MEMBRANE, 8, "Phantom Membrane"), new Cost(Items.DIAMOND, 2, "Diamond")))),
		d -> d.speedLevel, (d, v) -> d.speedLevel = v);

	static final Track CARGO = new Track("Holds", Items.CHEST, List.of(
		new Upgrade("Two holds", "Cargo A and B: 108 slots.", List.of()),
		new Upgrade("Extra hold", "Adds Cargo C below the quarterdeck: 162 slots.",
			List.of(new Cost(Items.CHEST, 8, "Chest"), new Cost(Items.IRON_INGOT, 16, "Iron Ingot"))),
		new Upgrade("Deep hold", "Adds Cargo D in the bow: 216 slots.",
			List.of(new Cost(Items.BARREL, 16, "Barrel"), new Cost(Items.IRON_BLOCK, 4, "Block of Iron")))),
		d -> d.cargoLevel, (d, v) -> d.cargoLevel = v);

	static final Track DRILL = new Track("Canal drill", Items.DIAMOND_PICKAXE, List.of(
		new Upgrade("No drill", "Land stops the ship.", List.of()),
		new Upgrade("Canal drill", "A drill on the bow: switched on, the ship cuts a canal through land and fills it with water.",
			List.of(new Cost(Items.DIAMOND_PICKAXE, 2, "Diamond Pickaxe"), new Cost(Items.IRON_BLOCK, 8, "Block of Iron"),
				new Cost(Items.REDSTONE_BLOCK, 4, "Block of Redstone")))),
		d -> d.drillLevel, (d, v) -> d.drillLevel = v);

	static final List<Track> TRACKS = List.of(SPEED, CARGO, DRILL);

	/** Top speed multiplier per rigging level. */
	private static final double[] SPEED_FACTOR = {1.0, 1.2, 1.4, 1.6};
	static final int MAX_LEVEL = SPEED.max();

	private Shipwright() {
	}

	static int clamp(int level) {
		return Math.max(0, Math.min(MAX_LEVEL, level));
	}

	static double factor(int level) {
		return SPEED_FACTOR[clamp(level)];
	}

	static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> "–";
		};
	}

	/** The next refit on this track, or null when it's done. */
	static @Nullable Upgrade next(Track track, ShipData data) {
		int level = track.level(data);
		return level >= track.max() ? null : track.levels().get(level + 1);
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

	/** Buys the next rigging refit (the speed track). */
	static @Nullable String upgrade(Player player, Ship ship) {
		return upgrade(player, ship, SPEED);
	}

	/** Buys the next refit on a track. Returns null when it worked, otherwise why not. */
	static @Nullable String upgrade(Player player, Ship ship, Track track) {
		if (!ship.isOwner(player) && !player.isCreative()) {
			return "Only the captain can have the ship refitted.";
		}
		Upgrade next = next(track, ship.data);
		if (next == null) {
			return "Nothing left to refit there.";
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
		track.set().accept(ship.data, track.level(ship.data) + 1);
		if (track == DRILL) {
			ship.data.drillOn = false; // the captain switches it on when they mean it
		}
		return null;
	}
}
