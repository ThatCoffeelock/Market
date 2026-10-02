package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a train remembers: owner, lock, whether it's running its route, which way it's going, how many
 * wagons it pulls and the cargo in them. Saved on the locomotive's root entity (a Fabric data attachment). Its heading is the
 * root's yaw, which always points at the locomotive's nose.
 */
public final class TrainData {
	/** Slots per wagon. */
	public static final int SLOTS = 27;
	public static final int MAX_WAGONS = 4;

	private record Slot(int slot, ItemStack stack) {
		static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("slot").forGetter(Slot::slot),
			ItemStack.CODEC.fieldOf("item").forGetter(Slot::stack)
		).apply(i, Slot::new));
	}

	public static final Codec<TrainData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("owner", "").forGetter(d -> d.owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(d -> d.ownerName),
		Codec.BOOL.optionalFieldOf("locked", false).forGetter(d -> d.locked),
		Codec.BOOL.optionalFieldOf("running", true).forGetter(d -> d.running),
		Codec.INT.optionalFieldOf("dir", 1).forGetter(d -> d.dir),
		Codec.LONG.optionalFieldOf("at", 0L).forGetter(d -> d.at.asLong()),
		Codec.LONG.optionalFieldOf("hauled", 0L).forGetter(d -> d.hauled),
		Codec.INT.optionalFieldOf("wagons", 1).forGetter(d -> d.cargo.size()),
		// one list for all wagons: slot 27 is the first slot of the second wagon
		Slot.CODEC.listOf().optionalFieldOf("cargo", List.of()).forGetter(TrainData::allSlots)
	).apply(i, TrainData::new));

	public String owner;
	public String ownerName;
	public boolean locked;
	/** Running its route by itself. When false it's parked, and whoever sits in the cab can drive it. */
	public boolean running;
	/** +1: locomotive first. -1: wagon first (it pushes the wagon back the other way). */
	public int dir;
	/** The rail under the locomotive. */
	public BlockPos at;
	/** Items delivered so far, for bragging rights. */
	public long hauled;
	/** One container per wagon, front (next to the locomotive) to back. Always at least one. */
	public final List<SimpleContainer> cargo = new ArrayList<>();

	public TrainData() {
		this("", "", false, true, 1, 0L, 0L, 1, List.of());
	}

	private TrainData(String owner, String ownerName, boolean locked, boolean running, int dir, long at, long hauled, int wagons, List<Slot> cargo) {
		this.owner = owner;
		this.ownerName = ownerName;
		this.locked = locked;
		this.running = running;
		this.dir = dir < 0 ? -1 : 1;
		this.at = BlockPos.of(at);
		this.hauled = hauled;
		int count = Math.max(1, Math.min(MAX_WAGONS, wagons));
		for (int w = 0; w < count; w++) {
			this.cargo.add(new SimpleContainer(SLOTS));
		}
		for (Slot slot : cargo) {
			int w = slot.slot / SLOTS;
			if (slot.slot >= 0 && w < count) {
				this.cargo.get(w).setItem(slot.slot % SLOTS, slot.stack.copy());
			}
		}
	}

	private static List<Slot> allSlots(TrainData data) {
		List<Slot> slots = new ArrayList<>();
		for (int w = 0; w < data.cargo.size(); w++) {
			for (Slot slot : slots(data.cargo.get(w))) {
				slots.add(new Slot(w * SLOTS + slot.slot, slot.stack));
			}
		}
		return slots;
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

	public int wagons() {
		return cargo.size();
	}

	public int totalSlots() {
		return cargo.size() * SLOTS;
	}

	public int usedSlots() {
		return allSlots(this).size();
	}

	public int usedSlots(int wagon) {
		return slots(cargo.get(wagon)).size();
	}

	public int itemCount() {
		int n = 0;
		for (SimpleContainer wagon : cargo) {
			for (int i = 0; i < wagon.getContainerSize(); i++) {
				n += wagon.getItem(i).getCount();
			}
		}
		return n;
	}

	public boolean isEmpty() {
		for (SimpleContainer wagon : cargo) {
			if (!wagon.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public boolean isFull() {
		for (SimpleContainer wagon : cargo) {
			for (int i = 0; i < wagon.getContainerSize(); i++) {
				if (wagon.getItem(i).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}
}
