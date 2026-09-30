package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a ship remembers. Saved on the ship's root entity (a Fabric data attachment), and inside
 * the Ship in a Bottle when it's packed up. {@link #blocks} is the ship's layout in its own frame
 * (bow towards +z), including anything players built onto it.
 */
public final class ShipData {
	public static final int BAY = 54;

	private record Slot(int slot, ItemStack stack) {
		static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("slot").forGetter(Slot::slot),
			ItemStack.CODEC.fieldOf("item").forGetter(Slot::stack)
		).apply(i, Slot::new));
	}

	public static final Codec<ShipData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(d -> d.name),
		Codec.STRING.optionalFieldOf("owner", "").forGetter(d -> d.owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(d -> d.ownerName),
		Codec.BOOL.optionalFieldOf("locked", false).forGetter(d -> d.locked),
		Codec.BOOL.optionalFieldOf("sailing", false).forGetter(d -> d.sailing),
		Codec.INT.optionalFieldOf("waterline", 62).forGetter(d -> d.waterline),
		ShipTemplate.ShipBlock.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(d -> d.blocks),
		Slot.CODEC.listOf().optionalFieldOf("cargo_a", List.of()).forGetter(d -> slots(d.cargoA)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_b", List.of()).forGetter(d -> slots(d.cargoB))
	).apply(i, ShipData::new));

	private static final String[] NAMES = {
		"The Salty Pickle", "Unsinkable II", "HMS Pancake", "The Wet Blanket", "Boaty McBoatface",
		"The Soggy Biscuit", "Sea Legs", "The Leaky Bucket", "Knot Guilty", "The Flying Dutchman (Budget Edition)"};

	public String name;
	public String owner;
	public String ownerName;
	public boolean locked;
	public boolean sailing;
	/** World y of the top water layer, i.e. local y = 0. */
	public int waterline;
	public List<ShipTemplate.ShipBlock> blocks;
	public final SimpleContainer cargoA = new SimpleContainer(BAY);
	public final SimpleContainer cargoB = new SimpleContainer(BAY);

	public ShipData(List<ShipTemplate.ShipBlock> blocks) {
		this.name = NAMES[(int) (Math.random() * NAMES.length)];
		this.owner = "";
		this.ownerName = "";
		this.blocks = new ArrayList<>(blocks);
	}

	private ShipData(String name, String owner, String ownerName, boolean locked, boolean sailing, int waterline,
					 List<ShipTemplate.ShipBlock> blocks, List<Slot> a, List<Slot> b) {
		this.name = name.isEmpty() ? NAMES[0] : name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.locked = locked;
		this.sailing = sailing;
		this.waterline = waterline;
		this.blocks = new ArrayList<>(blocks);
		fill(cargoA, a);
		fill(cargoB, b);
	}

	private static void fill(SimpleContainer container, List<Slot> slots) {
		for (Slot slot : slots) {
			if (slot.slot >= 0 && slot.slot < BAY) {
				container.setItem(slot.slot, slot.stack.copy());
			}
		}
	}

	private static List<Slot> slots(SimpleContainer container) {
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < BAY; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				slots.add(new Slot(i, stack.copy()));
			}
		}
		return slots;
	}

	public int usedSlots() {
		return slots(cargoA).size() + slots(cargoB).size();
	}

	/** Puts an item in the hold. Returns what didn't fit. */
	public ItemStack stow(ItemStack stack) {
		ItemStack rest = cargoA.addItem(stack);
		return rest.isEmpty() ? rest : cargoB.addItem(rest);
	}
}
