package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a train remembers: owner, lock, whether it's running its route, which way it's going and the
 * cargo in the wagon. Saved on the locomotive's root entity (a Fabric data attachment). Its heading is the
 * root's yaw, which always points at the locomotive's nose.
 */
public final class TrainData {
	public static final int SLOTS = 27;

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
		Slot.CODEC.listOf().optionalFieldOf("cargo", List.of()).forGetter(d -> slots(d.cargo))
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
	public final SimpleContainer cargo = new SimpleContainer(SLOTS);

	public TrainData() {
		this("", "", false, true, 1, 0L, 0L, List.of());
	}

	private TrainData(String owner, String ownerName, boolean locked, boolean running, int dir, long at, long hauled, List<Slot> cargo) {
		this.owner = owner;
		this.ownerName = ownerName;
		this.locked = locked;
		this.running = running;
		this.dir = dir < 0 ? -1 : 1;
		this.at = BlockPos.of(at);
		this.hauled = hauled;
		for (Slot slot : cargo) {
			if (slot.slot >= 0 && slot.slot < SLOTS) {
				this.cargo.setItem(slot.slot, slot.stack.copy());
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
		return slots(cargo).size();
	}

	public int itemCount() {
		int n = 0;
		for (int i = 0; i < cargo.getContainerSize(); i++) {
			n += cargo.getItem(i).getCount();
		}
		return n;
	}
}
