package com.thatcoffeelock.mobilehome;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a vehicle remembers: owner, fuel, lock and its storage. Saved on the vehicle entity itself
 * (a Fabric data attachment), and copied into the item when you pack the vehicle up.
 */
public final class VehicleData {
	public static final int SLOTS = 54;

	private record Slot(int slot, ItemStack stack) {
		static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("slot").forGetter(Slot::slot),
			ItemStack.CODEC.fieldOf("item").forGetter(Slot::stack)
		).apply(i, Slot::new));
	}

	public static final Codec<VehicleData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("type").forGetter(d -> d.type.id),
		Codec.STRING.optionalFieldOf("owner", "").forGetter(d -> d.owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(d -> d.ownerName),
		Codec.INT.optionalFieldOf("fuel", 0).forGetter(d -> d.fuel),
		Codec.BOOL.optionalFieldOf("locked", false).forGetter(d -> d.locked),
		Slot.CODEC.listOf().optionalFieldOf("storage", List.of()).forGetter(VehicleData::slots)
	).apply(i, VehicleData::new));

	public final VehicleType type;
	public String owner;
	public String ownerName;
	public int fuel;
	public boolean locked;
	public final SimpleContainer storage = new SimpleContainer(SLOTS);

	public VehicleData(VehicleType type) {
		this.type = type;
		this.owner = "";
		this.ownerName = "";
	}

	private VehicleData(String type, String owner, String ownerName, int fuel, boolean locked, List<Slot> slots) {
		this.type = VehicleType.byId(type);
		this.owner = owner;
		this.ownerName = ownerName;
		this.fuel = Math.max(0, fuel);
		this.locked = locked;
		for (Slot slot : slots) {
			if (slot.slot >= 0 && slot.slot < SLOTS) {
				storage.setItem(slot.slot, slot.stack.copy());
			}
		}
	}

	private List<Slot> slots() {
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = storage.getItem(i);
			if (!stack.isEmpty()) {
				slots.add(new Slot(i, stack.copy()));
			}
		}
		return slots;
	}

	public int usedSlots() {
		int n = 0;
		for (int i = 0; i < SLOTS; i++) {
			if (!storage.getItem(i).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	public int fuelPercent() {
		return (int) Math.min(100, Math.ceil(fuel * 100.0 / VehicleType.FUEL_CAPACITY));
	}

	/** Roughly how long you can keep your foot down, e.g. "12 min". */
	public String fuelTime() {
		int seconds = fuel / type.fuelPerTick / 20;
		if (seconds >= 3600) {
			return (seconds / 3600) + "h " + (seconds % 3600 / 60) + "m";
		}
		return seconds >= 60 ? (seconds / 60) + " min" : seconds + " s";
	}
}
