package com.thatcoffeelock.fossilfool;

import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Rig Workshop: upgrades for a Drill Rig, bought by its owner with materials from their own pockets (creative
 * players get them free), like the Shipwright's refits in Ahoy. Four tracks: shaft size, engine speed, fuel
 * efficiency and holds. The levels stay with the rig, also when it's packed up.
 */
final class Workshop {
	/** One price line: so many of this item. */
	record Cost(Item item, int count, String name) {
	}

	/** One upgrade: what it's called, what it does, and what it costs. */
	record Upgrade(String name, String blurb, List<Cost> costs) {
	}

	/** A line of upgrades. Index in {@code levels} = level; level 0 is the rig as crafted. */
	record Track(String key, String title, Item icon, List<Upgrade> levels, ToIntFunction<Rig> get, ObjIntConsumer<Rig> set) {
		int max() {
			return levels.size() - 1;
		}

		int level(Rig rig) {
			return Math.max(0, Math.min(max(), get.applyAsInt(rig)));
		}
	}

	static final Track SIZE = new Track("size", "Shaft size", Items.DIAMOND_PICKAXE, List.of(
		new Upgrade("5×5 bit", "As crafted: a 5×5 shaft.", List.of()),
		new Upgrade("Wide bit", "A 7×7 shaft from the layer it's on now. Almost twice the ore per layer.",
			List.of(new Cost(Items.IRON_BLOCK, 8, "Block of Iron"), new Cost(Items.DIAMOND, 4, "Diamond"))),
		new Upgrade("Strip-mine bit", "A 9×9 shaft. Over three times the ore per layer, and a hole you can lose a village in.",
			List.of(new Cost(Items.IRON_BLOCK, 16, "Block of Iron"), new Cost(Items.DIAMOND_BLOCK, 2, "Block of Diamond")))),
		r -> r.sizeLevel, (r, v) -> r.sizeLevel = v);

	static final Track SPEED = new Track("speed", "Engine", Items.PISTON, List.of(
		new Upgrade("Steam engine", "As crafted.", List.of()),
		new Upgrade("Bigger boiler", "+25% drilling and pumping speed.",
			List.of(new Cost(Items.COPPER_INGOT, 32, "Copper Ingot"), new Cost(Items.BLAST_FURNACE, 1, "Blast Furnace"))),
		new Upgrade("Twin engines", "+50% drilling and pumping speed.",
			List.of(new Cost(Items.PISTON, 8, "Piston"), new Cost(Items.REDSTONE_BLOCK, 4, "Block of Redstone"))),
		new Upgrade("Diamond drill head", "+75% drilling and pumping speed.",
			List.of(new Cost(Items.DIAMOND, 8, "Diamond"), new Cost(Items.REDSTONE_BLOCK, 8, "Block of Redstone")))),
		r -> r.speedLevel, (r, v) -> r.speedLevel = v);

	static final Track EFFICIENCY = new Track("efficiency", "Fuel efficiency", Items.BLAZE_POWDER, List.of(
		new Upgrade("Plain firebox", "As crafted.", List.of()),
		new Upgrade("Lagged boiler", "+20% from every item of fuel.",
			List.of(new Cost(Items.BRICK, 32, "Brick"), new Cost(Items.IRON_INGOT, 8, "Iron Ingot"))),
		new Upgrade("Superheater", "+40% from every item of fuel.",
			List.of(new Cost(Items.GOLD_INGOT, 8, "Gold Ingot"), new Cost(Items.BLAZE_ROD, 4, "Blaze Rod"))),
		new Upgrade("Compound engine", "+60% from every item of fuel.",
			List.of(new Cost(Items.DIAMOND, 4, "Diamond"), new Cost(Items.BLAZE_ROD, 8, "Blaze Rod")))),
		r -> r.effLevel, (r, v) -> r.effLevel = v);

	static final Track HOLD = new Track("hold", "Holds", Items.CHEST, List.of(
		new Upgrade("Two holds", "As crafted: 18 slots of ore, 18 of stone.", List.of()),
		new Upgrade("Extra bins", "36 slots of ore and 36 of stone (two pages in the rig's screen).",
			List.of(new Cost(Items.CHEST, 8, "Chest"), new Cost(Items.IRON_INGOT, 16, "Iron Ingot"))),
		new Upgrade("Deep bins", "54 slots of ore and 54 of stone (three pages).",
			List.of(new Cost(Items.BARREL, 16, "Barrel"), new Cost(Items.IRON_BLOCK, 4, "Block of Iron")))),
		r -> r.holdLevel, (r, v) -> r.holdLevel = v);

	static final List<Track> TRACKS = List.of(SIZE, SPEED, EFFICIENCY, HOLD);

	private Workshop() {
	}

	/** How much faster the engine runs at this level. */
	static double speedFactor(int level) {
		return 1.0 + 0.25 * Math.max(0, Math.min(SPEED.max(), level));
	}

	/** How much more every item of fuel is worth at this level (on top of Wildcatting's bonus). */
	static double fuelBonus(int level) {
		return 0.2 * Math.max(0, Math.min(EFFICIENCY.max(), level));
	}

	static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> "–";
		};
	}

	/** The next upgrade on this track, or null when it's done. */
	static @Nullable Upgrade next(Track track, Rig rig) {
		int level = track.level(rig);
		return level >= track.max() ? null : track.levels().get(level + 1);
	}

	static int count(Player player, Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			// only plain items: a packed-up rig is a piston too, but it isn't payment
			if (stack.is(item) && OilItems.kind(stack).isEmpty()) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(Player player, Item item, int amount) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item) && OilItems.kind(stack).isEmpty()) {
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

	/** Why this rig can't take the next upgrade on this track right now, or null if it can (payment aside). */
	static @Nullable String blocked(Track track, Rig rig) {
		if (next(track, rig) == null) {
			return "Nothing left to upgrade there.";
		}
		if (track == SIZE) {
			int half = rig.half() + 1;
			for (Rig other : Rigs.BY_ID.values()) {
				if (other != rig && other.dimension.equals(rig.dimension)
					&& Math.abs(other.cx - rig.cx) <= half + other.half() + 1 && Math.abs(other.cz - rig.cz) <= half + other.half() + 1) {
					return "Another Drill Rig is too close: the wider shafts would meet.";
				}
			}
		}
		return null;
	}

	/** Buys the next upgrade on a track. Returns null when it worked, otherwise why not. */
	static @Nullable String upgrade(Player player, ServerLevel level, Rig rig, Track track) {
		if (!(player instanceof net.minecraft.server.level.ServerPlayer sp) || (!rig.isOwner(sp) && !player.isCreative())) {
			return "Only " + rig.ownerName + " can upgrade this rig.";
		}
		String why = blocked(track, rig);
		if (why != null) {
			return why;
		}
		Upgrade next = next(track, rig);
		if (!canAfford(player, next)) {
			StringBuilder missing = new StringBuilder("You need:");
			for (Cost cost : next.costs()) {
				missing.append(' ').append(cost.count()).append(' ').append(cost.name()).append(',');
			}
			missing.setLength(missing.length() - 1);
			return missing.append('.').toString();
		}
		if (!player.isCreative()) {
			for (Cost cost : next.costs()) {
				take(player, cost.item(), cost.count());
			}
		}
		apply(level, rig, track, track.level(rig) + 1);
		return null;
	}

	/** Sets a track's level and makes it so: wider shaft (and model, and cofferdam), bigger holds. */
	static void apply(ServerLevel level, Rig rig, Track track, int value) {
		track.set().accept(rig, Math.max(0, Math.min(track.max(), value)));
		if (track == HOLD) {
			rig.resizeHolds();
		}
		if (track == SIZE) {
			// start the layer again at the new width: what's already dug is air, and air is free
			rig.cell = 0;
			if (rig.offshore()) {
				rig.buildCofferdam(level);
				rig.drainY = rig.deck;
			}
			Rigs.summon(level, rig);
		}
		Store.changed();
	}

	// ---------------------------------------------------------------- on the rig item

	static void write(Rig rig, CompoundTag tag) {
		for (Track track : TRACKS) {
			if (track.level(rig) > 0) {
				tag.putInt(track.key(), track.level(rig));
			}
		}
	}

	/** Levels from a packed-up rig (call before the rig is placed: they decide its size). */
	static void read(Rig rig, CompoundTag tag) {
		for (Track track : TRACKS) {
			track.set().accept(rig, Math.max(0, Math.min(track.max(), tag.getIntOr(track.key(), 0))));
		}
		rig.resizeHolds();
	}

	static boolean any(Rig rig) {
		for (Track track : TRACKS) {
			if (track.level(rig) > 0) {
				return true;
			}
		}
		return false;
	}
}
