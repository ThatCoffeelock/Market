package com.thatcoffeelock.blimey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Refits, bought by the captain with materials from their own pockets (creative players get them free). Three tracks
 * of three levels: engines (top speed, and a thirstier burn at full throttle), fuel economy (less diesel for
 * everything) and cargo holds (one more 54-slot hold per level). None of it is cheap. The levels are part of the
 * airship's data, so they stay when it's folded up.
 */
final class Engineer {
	/** One price line: so many of this item. Items are looked up by id, so a renamed item can't break the build. */
	record Cost(String id, int count, String name) {
		Item item() {
			return Engineer.item(id);
		}
	}

	/** One refit: what it's called, what it does, and what it costs. */
	record Upgrade(String name, String blurb, List<Cost> costs) {
	}

	/** A line of refits. Index in {@code levels} = level; level 0 is the airship as it comes out of the box. */
	record Track(String title, String icon, List<Upgrade> levels, ToIntFunction<AirshipData> get, ObjIntConsumer<AirshipData> set) {
		int max() {
			return levels.size() - 1;
		}

		int level(AirshipData data) {
			return Math.max(0, Math.min(max(), get.applyAsInt(data)));
		}
	}

	private static Cost c(String id, int count, String name) {
		return new Cost("minecraft:" + id, count, name);
	}

	static final Track SPEED = new Track("Engines", "minecraft:blast_furnace", List.of(
		new Upgrade("Twin diesel engines", "As she came out of the box. Chugs along.", List.of()),
		new Upgrade("Bigger propellers", "+25% top speed. Burns more at full throttle.",
			List.of(c("iron_block", 6, "Block of Iron"), c("piston", 8, "Piston"), c("copper_ingot", 32, "Copper Ingot"))),
		new Upgrade("Turbochargers", "+50% top speed. Burns more at full throttle.",
			List.of(c("diamond_block", 2, "Block of Diamond"), c("blaze_rod", 16, "Blaze Rod"), c("piston", 8, "Piston"))),
		new Upgrade("Racing engines", "+80% top speed. Drinks diesel like it's free. It isn't.",
			List.of(c("netherite_ingot", 2, "Netherite Ingot"), c("phantom_membrane", 16, "Phantom Membrane"), c("blaze_rod", 16, "Blaze Rod")))),
		d -> d.speedLevel, (d, v) -> d.speedLevel = v);

	static final Track EFFICIENCY = new Track("Fuel economy", "minecraft:comparator", List.of(
		new Upgrade("Factory settings", "Every bucket of diesel lasts as long as it lasts.", List.of()),
		new Upgrade("Tuned carburettors", "-20% diesel for everything.",
			List.of(c("redstone_block", 6, "Block of Redstone"), c("comparator", 4, "Redstone Comparator"), c("gold_ingot", 16, "Gold Ingot"))),
		new Upgrade("Riveted gas cells", "-35% diesel: the envelope stops leaking lift.",
			List.of(c("iron_block", 12, "Block of Iron"), c("phantom_membrane", 12, "Phantom Membrane"), c("gold_block", 4, "Block of Gold"))),
		new Upgrade("Helium envelope", "-50% diesel. Lighter than air, smugger than you.",
			List.of(c("ghast_tear", 8, "Ghast Tear"), c("phantom_membrane", 24, "Phantom Membrane"), c("diamond_block", 2, "Block of Diamond")))),
		d -> d.efficiencyLevel, (d, v) -> d.efficiencyLevel = v);

	static final Track CARGO = new Track("Cargo holds", "minecraft:chest", List.of(
		new Upgrade("One hold", "Cargo A: 54 slots.", List.of()),
		new Upgrade("Second hold", "Adds Cargo B: 108 slots.",
			List.of(c("chest", 8, "Chest"), c("iron_block", 4, "Block of Iron"))),
		new Upgrade("Third hold", "Adds Cargo C: 162 slots.",
			List.of(c("barrel", 16, "Barrel"), c("iron_block", 8, "Block of Iron"))),
		new Upgrade("Fourth hold", "Adds Cargo D: 216 slots. Shulker-lined, so it weighs nothing.",
			List.of(c("shulker_shell", 8, "Shulker Shell"), c("iron_block", 8, "Block of Iron")))),
		d -> d.cargoLevel, (d, v) -> d.cargoLevel = v);

	static final List<Track> TRACKS = List.of(SPEED, EFFICIENCY, CARGO);

	private static final double[] SPEED_FACTOR = {1.0, 1.25, 1.5, 1.8};
	private static final double[] BURN_FACTOR = {1.0, 0.8, 0.65, 0.5};
	static final int MAX_LEVEL = 3;

	private static final Map<String, Item> ITEMS = new HashMap<>();

	private Engineer() {
	}

	/** An item by id; a barrier if this Minecraft doesn't have it (so it can't be bought by accident). */
	static Item item(String id) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id);
		return item == null || item == Items.AIR ? Items.BARRIER : item;
	}

	static int clamp(int level) {
		return Math.max(0, Math.min(MAX_LEVEL, level));
	}

	/** Top speed multiplier per Engines level. */
	static double speedFactor(int level) {
		return SPEED_FACTOR[clamp(level)];
	}

	/** Diesel multiplier per Fuel economy level. */
	static double burnFactor(int level) {
		return BURN_FACTOR[clamp(level)];
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
	static @Nullable Upgrade next(Track track, AirshipData data) {
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
			if (cost.item() == Items.BARRIER || count(player, cost.item()) < cost.count()) {
				return false;
			}
		}
		return true;
	}

	/** Buys the next refit on a track. Returns null when it worked, otherwise why not. */
	static @Nullable String upgrade(Player player, Airship ship, Track track) {
		if (!ship.isOwner(player) && !player.isCreative()) {
			return "Only the captain can order refits.";
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
		return null;
	}
}
