package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a ship remembers: name, owner, lock and cargo. Saved on the ship's root entity (a Fabric
 * data attachment), and inside the Ship in a Bottle when it's packed up.
 */
public final class ShipData {
	public static final int BAY = 54;
	/** Gun ports: cannons slotted into the ship (when the Cannon mod is installed). */
	public static final int GUNS = ShipModel.GUN_PORTS.size();

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
		Codec.DOUBLE.optionalFieldOf("surface", 63.0).forGetter(d -> d.surface),
		Slot.CODEC.listOf().optionalFieldOf("cargo_a", List.of()).forGetter(d -> slots(d.cargoA)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_b", List.of()).forGetter(d -> slots(d.cargoB)),
		Slot.CODEC.listOf().optionalFieldOf("guns", List.of()).forGetter(d -> slots(d.guns))
	).apply(i, ShipData::new));

	private static final String[] NAMES = {
		"The Salty Pickle", "Unsinkable II", "HMS Pancake", "The Wet Blanket", "Boaty McBoatface",
		"The Soggy Biscuit", "Sea Legs", "The Leaky Bucket", "Knot Guilty", "The Flying Dutchman (Budget Edition)"};

	public String name;
	public String owner;
	public String ownerName;
	public boolean locked;
	/** World y of the water surface the ship floats on (local y = 0). */
	public double surface;
	public final SimpleContainer cargoA = new SimpleContainer(BAY);
	public final SimpleContainer cargoB = new SimpleContainer(BAY);
	public final SimpleContainer guns = new SimpleContainer(GUNS);

	public ShipData() {
		this.name = NAMES[(int) (Math.random() * NAMES.length)];
		this.owner = "";
		this.ownerName = "";
	}

	private ShipData(String name, String owner, String ownerName, boolean locked, double surface, List<Slot> a, List<Slot> b, List<Slot> guns) {
		this.name = name.isEmpty() ? NAMES[0] : name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.locked = locked;
		this.surface = surface;
		fill(cargoA, a);
		fill(cargoB, b);
		fill(this.guns, guns);
	}

	private static void fill(SimpleContainer container, List<Slot> slots) {
		for (Slot slot : slots) {
			if (slot.slot >= 0 && slot.slot < container.getContainerSize()) {
				container.setItem(slot.slot, slot.stack.copy());
			}
		}
	}

	private static List<Slot> slots(SimpleContainer container) {
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
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
}
