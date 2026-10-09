package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything an airship remembers: name, owner, lock, refits, diesel and cargo. Saved on the airship's root entity
 * (a Fabric data attachment), and inside the Flat-Pack Airship when it's folded up.
 */
public final class AirshipData {
	public static final int BAY = 54;
	/** Fuel tank slots: buckets of diesel go in, empty buckets come out. */
	public static final int TANK = 9;

	private record Slot(int slot, ItemStack stack) {
		static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("slot").forGetter(Slot::slot),
			ItemStack.CODEC.fieldOf("item").forGetter(Slot::stack)
		).apply(i, Slot::new));
	}

	public static final Codec<AirshipData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(d -> d.name),
		Codec.STRING.optionalFieldOf("owner", "").forGetter(d -> d.owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(d -> d.ownerName),
		Codec.BOOL.optionalFieldOf("locked", false).forGetter(d -> d.locked),
		Codec.DOUBLE.optionalFieldOf("fuel", 0.0).forGetter(d -> d.fuel),
		Codec.INT.optionalFieldOf("speed_level", 0).forGetter(d -> d.speedLevel),
		Codec.INT.optionalFieldOf("efficiency_level", 0).forGetter(d -> d.efficiencyLevel),
		Codec.INT.optionalFieldOf("cargo_level", 0).forGetter(d -> d.cargoLevel),
		Slot.CODEC.listOf().optionalFieldOf("tank", List.of()).forGetter(d -> slots(d.tank)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_a", List.of()).forGetter(d -> slots(d.cargoA)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_b", List.of()).forGetter(d -> slots(d.cargoB)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_c", List.of()).forGetter(d -> slots(d.cargoC)),
		Slot.CODEC.listOf().optionalFieldOf("cargo_d", List.of()).forGetter(d -> slots(d.cargoD))
	).apply(i, AirshipData::new));

	private static final String[] NAMES = {
		"The Iron Sausage", "Lead Balloon", "HMS Overcompensating", "The Flying Brick", "Hindenburg II (Don't Ask)",
		"The Gas Bag", "Cloud Botherer", "The Sky Whale", "Altitude Problem", "The Diesel Dumpling"};

	public String name;
	public String owner;
	public String ownerName;
	public boolean locked;
	/** Diesel already in the engines, in burn units (one bucket = config ticksPerBucket). */
	public double fuel;
	/** Refits bought from the Engineer (see {@link Engineer}). */
	public int speedLevel;
	public int efficiencyLevel;
	/** 0 = one hold; each level adds one, up to four. */
	public int cargoLevel;
	public final SimpleContainer tank = new SimpleContainer(TANK);
	public final SimpleContainer cargoA = new SimpleContainer(BAY);
	public final SimpleContainer cargoB = new SimpleContainer(BAY);
	public final SimpleContainer cargoC = new SimpleContainer(BAY);
	public final SimpleContainer cargoD = new SimpleContainer(BAY);

	public AirshipData() {
		this.name = NAMES[(int) (Math.random() * NAMES.length)];
		this.owner = "";
		this.ownerName = "";
	}

	private AirshipData(String name, String owner, String ownerName, boolean locked, double fuel, int speedLevel, int efficiencyLevel,
		int cargoLevel, List<Slot> tank, List<Slot> a, List<Slot> b, List<Slot> c, List<Slot> d) {
		this.name = name.isEmpty() ? NAMES[0] : name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.locked = locked;
		this.fuel = Math.max(0, fuel);
		this.speedLevel = Engineer.clamp(speedLevel);
		this.efficiencyLevel = Engineer.clamp(efficiencyLevel);
		this.cargoLevel = Engineer.clamp(cargoLevel);
		fill(this.tank, tank);
		fill(cargoA, a);
		fill(cargoB, b);
		fill(cargoC, c);
		fill(cargoD, d);
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

	/** The holds this airship has: A always, then B, C and D as they're built. */
	public List<SimpleContainer> holds() {
		List<SimpleContainer> all = List.of(cargoA, cargoB, cargoC, cargoD);
		return new ArrayList<>(all.subList(0, 1 + Math.max(0, Math.min(3, cargoLevel))));
	}

	public int capacity() {
		return BAY * holds().size();
	}

	public int usedSlots() {
		int n = 0;
		for (SimpleContainer hold : List.of(cargoA, cargoB, cargoC, cargoD)) {
			n += slots(hold).size();
		}
		return n;
	}

	/** Full buckets of diesel waiting in the tank. */
	public int dieselAboard() {
		int n = 0;
		for (int i = 0; i < tank.getContainerSize(); i++) {
			ItemStack stack = tank.getItem(i);
			if (BlimeyItems.isDiesel(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	void clearContents() {
		tank.clearContent();
		cargoA.clearContent();
		cargoB.clearContent();
		cargoC.clearContent();
		cargoD.clearContent();
	}
}
