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
		Slot.CODEC.listOf().optionalFieldOf("guns", List.of()).forGetter(d -> slots(d.guns)),
		Slot.CODEC.listOf().optionalFieldOf("bunks", List.of()).forGetter(d -> slots(d.bunks)),
		Codec.INT.optionalFieldOf("speed_level", 0).forGetter(d -> d.speedLevel),
		Slot.CODEC.listOf().optionalFieldOf("cargo_c", List.of()).forGetter(d -> slots(d.cargoC)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_d", List.of()).forGetter(d -> slots(d.cargoD)),
		Codec.INT.optionalFieldOf("cargo_level", 0).forGetter(d -> d.cargoLevel),
		Codec.INT.optionalFieldOf("drill_level", 0).forGetter(d -> d.drillLevel),
		Codec.BOOL.optionalFieldOf("drill_on", false).forGetter(d -> d.drillOn)
	).apply(i, ShipData::new));

	private static final String[] NAMES = {
		"The Salty Pickle", "Unsinkable II", "HMS Pancake", "The Wet Blanket", "Boaty McBoatface",
		"The Soggy Biscuit", "Sea Legs", "The Leaky Bucket", "Knot Guilty", "The Flying Dutchman (Budget Edition)"};

	public String name;
	public String owner;
	public String ownerName;
	public boolean locked;
	/** Rigging upgrades bought at the Shipwright (0 = as launched, see {@link Shipwright}). */
	public int speedLevel;
	/** Extra holds bought at the Shipwright: 1 unlocks Cargo C, 2 also Cargo D. */
	public int cargoLevel;
	/** 1 when the ship has a canal drill; {@link #drillOn} is the captain's switch. */
	public int drillLevel;
	public boolean drillOn;
	/** World y of the water surface the ship floats on (local y = 0). */
	public double surface;
	public final SimpleContainer cargoA = new SimpleContainer(BAY);
	public final SimpleContainer cargoB = new SimpleContainer(BAY);
	public final SimpleContainer cargoC = new SimpleContainer(BAY);
	public final SimpleContainer cargoD = new SimpleContainer(BAY);
	public final SimpleContainer guns = new SimpleContainer(GUNS);
	/** Bunks: beds slotted into the foredeck, to sleep in. */
	public final SimpleContainer bunks = new SimpleContainer(ShipModel.BUNKS.size());

	public ShipData() {
		this.name = NAMES[(int) (Math.random() * NAMES.length)];
		this.owner = "";
		this.ownerName = "";
	}

	private ShipData(String name, String owner, String ownerName, boolean locked, double surface, List<Slot> a, List<Slot> b, List<Slot> guns, List<Slot> bunks, int speedLevel,
		List<Slot> c, List<Slot> d, int cargoLevel, int drillLevel, boolean drillOn) {
		this.name = name.isEmpty() ? NAMES[0] : name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.locked = locked;
		this.surface = surface;
		fill(cargoA, a);
		fill(cargoB, b);
		fill(this.guns, guns);
		fill(this.bunks, bunks);
		this.speedLevel = Shipwright.clamp(speedLevel);
		fill(cargoC, c);
		fill(cargoD, d);
		this.cargoLevel = Math.max(0, Math.min(2, cargoLevel));
		this.drillLevel = Math.max(0, Math.min(1, drillLevel));
		this.drillOn = drillOn && this.drillLevel > 0;
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
		return slots(cargoA).size() + slots(cargoB).size() + slots(cargoC).size() + slots(cargoD).size();
	}

	/** The holds this ship has: A and B always, C and D once they're built. */
	public List<SimpleContainer> holds() {
		List<SimpleContainer> holds = new ArrayList<>(List.of(cargoA, cargoB));
		if (cargoLevel >= 1) {
			holds.add(cargoC);
		}
		if (cargoLevel >= 2) {
			holds.add(cargoD);
		}
		return holds;
	}

	public int capacity() {
		return BAY * holds().size();
	}

	void clearCargo() {
		cargoA.clearContent();
		cargoB.clearContent();
		cargoC.clearContent();
		cargoD.clearContent();
	}
}
