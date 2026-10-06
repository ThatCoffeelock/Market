package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import org.jetbrains.annotations.Nullable;

/** One colony: its Town Hall and every building around it. Saved in <world>/colonycraft.json. */
public final class Colony {
	/** One building. Positions are in world coordinates; {@link #quarter} is its rotation in quarter turns. */
	public static final class Building {
		public final String id;
		public final BuildingType type;
		public int tier = 1;
		public final BlockPos origin;
		public final int quarter;
		/** One entry per job; null means the worker died and hasn't been replaced yet. */
		public final List<UUID> villagers = new ArrayList<>();
		/** Everything paid for this building so far, in cents (for the demolition refund). */
		public long spent;
		/** Storehouses only. */
		public boolean autosell;
		/** Storehouses only: the Warehouse mod's warehouse built into it (its id), when Warehouse is installed. */
		public @Nullable String warehouse;
		/** Train stations only: keep the Pickup Station topped up from the storehouses, so trains carry the goods away. */
		public boolean export;
		public SimpleContainer storage = new SimpleContainer(27);
		/** Cellblocks only: who's locked up in which cell. */
		public final Map<Integer, Prison.Prisoner> prisoners = new TreeMap<>();
		public transient Colony colony;

		public Building(String id, BuildingType type, BlockPos origin, int quarter) {
			this.id = id;
			this.type = type;
			this.origin = origin;
			this.quarter = quarter;
		}

		public int alive() {
			int n = 0;
			for (UUID v : villagers) {
				if (v != null) {
					n++;
				}
			}
			return n;
		}

		public int dead() {
			return villagers.size() - alive();
		}

		/** Storehouse size by tier: 27, then 54. */
		public int storageSize() {
			return tier <= 1 ? 27 : 54;
		}

		public void resizeStorage() {
			int size = storageSize();
			if (storage.getContainerSize() == size) {
				return;
			}
			SimpleContainer bigger = new SimpleContainer(size);
			for (int i = 0; i < storage.getContainerSize() && i < size; i++) {
				bigger.setItem(i, storage.getItem(i));
			}
			storage = bigger;
		}

		/** Is this world position inside the building (its footprint, from the floor to the roof)? */
		public boolean contains(BlockPos pos) {
			BlockPos local = Colonies.toLocal(origin, quarter, pos);
			return Math.abs(local.getX()) <= type.half && Math.abs(local.getZ()) <= type.depth
				&& local.getY() >= 0 && local.getY() <= type.height + 1;
		}

		public BlockPos world(BlockPos local) {
			return Colonies.toWorld(origin, quarter, local.getX(), local.getY(), local.getZ());
		}

		public String title() {
			return type.displayName + (tier > 1 ? " (Tier " + tier + ")" : "");
		}
	}

	public final String id;
	public String name;
	public final UUID owner;
	public String ownerName;
	public final String dimension;
	public final List<Building> buildings = new ArrayList<>();
	/** Wages weren't paid at the last payday: nobody works until they are. */
	public boolean striking;

	public Colony(String id, String name, UUID owner, String ownerName, String dimension) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.dimension = dimension;
	}

	public @Nullable Building townHall() {
		for (Building b : buildings) {
			if (b.type == BuildingType.TOWN_HALL) {
				return b;
			}
		}
		return null;
	}

	public int tier() {
		Building hall = townHall();
		return hall == null ? 1 : hall.tier;
	}

	/** Half-size of the claimed square around the Town Hall. */
	public int radius() {
		return 32 + 16 * (tier() - 1);
	}

	public int maxBuildings() {
		return 8 + 6 * (tier() - 1);
	}

	/** Building slots in use: everything but the Town Hall and the fortifications. */
	public int slotsUsed() {
		int n = 0;
		for (Building b : buildings) {
			if (b.type != BuildingType.TOWN_HALL && !b.type.fortification) {
				n++;
			}
		}
		return n;
	}

	public BlockPos centre() {
		Building hall = townHall();
		return hall == null ? BlockPos.ZERO : hall.origin;
	}

	public boolean claims(BlockPos pos) {
		BlockPos c = centre();
		return Math.abs(pos.getX() - c.getX()) <= radius() && Math.abs(pos.getZ() - c.getZ()) <= radius();
	}

	public int housing() {
		int beds = 0;
		for (Building b : buildings) {
			beds += b.type.housing(b.tier);
		}
		return beds;
	}

	/** Jobs that need a bed, filled or not (a dead worker still has a bed waiting). Iron golems don't sleep. */
	public int jobs() {
		int jobs = 0;
		for (Building b : buildings) {
			if (b.type.needsBeds()) {
				jobs += b.villagers.size();
			}
		}
		return jobs;
	}

	public int workers() {
		int alive = 0;
		for (Building b : buildings) {
			alive += b.alive();
		}
		return alive;
	}

	/** Wages, plus bread and water for the prisoners. */
	public long dailyWages() {
		long wages = 0;
		for (Building b : buildings) {
			wages += Bank.cents(b.type.wage) * b.alive();
			wages += Bank.cents(Prison.UPKEEP) * b.prisoners.size();
		}
		return wages;
	}

	public boolean isOwner(UUID player) {
		return owner.equals(player);
	}
}
