package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.thatcoffeelock.cannon.CannonApi;
import com.thatcoffeelock.cannon.CannonItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The cannons slotted into one ship. The slots are in the ship's data (they're saved, and travel in the bottle); this
 * builds a Cannon mount for each one that holds a cannon, carries it along with the ship every tick, and takes it
 * down again when the cannon is taken out or the ship goes away. Gunners sit behind their cannon; when they get off
 * they go back to the seat they came from. Cannonballs come from their pockets first, then from the cargo holds.
 */
final class CannonDeck implements GunDeck, CannonApi.Crew {
	private enum Closing { NO, ASHORE, QUIETLY }

	private final Ship ship;
	private final CannonApi.Mount[] mounts = new CannonApi.Mount[ShipData.GUNS];
	/** Where each gunner was sitting before they stepped up to a cannon. */
	private final Map<UUID, Integer> returnSeat = new HashMap<>();
	private Closing closing = Closing.NO;
	private int age;

	CannonDeck(Ship ship) {
		this.ship = ship;
	}

	// ---------------------------------------------------------------- building and moving

	@Override
	public boolean accepts(ItemStack stack) {
		return CannonItems.isCannon(stack);
	}

	@Override
	public void tick() {
		age++;
		for (int port = 0; port < mounts.length; port++) {
			CannonApi.Mount mount = sync(port);
			if (mount == null) {
				continue;
			}
			place(port, mount);
			// a cannon nobody's using only needs to be looked at now and then
			if (mount.gunner() != null || mount.reloadTicks() > 0 || age % 10 == 0) {
				mount.tick();
			}
		}
	}

	/** The cannon for this port: built if there's one slotted in and none standing, taken down if the slot's empty. */
	private @Nullable CannonApi.Mount sync(int port) {
		CannonApi.Mount mount = mounts[port];
		boolean wanted = accepts(ship.data.guns.getItem(port));
		if (mount != null && (mount.isRemoved() || !wanted)) {
			if (!mount.isRemoved()) {
				mount.remove();
			}
			mounts[port] = null;
			mount = null;
		}
		if (mount == null && wanted && !ship.isRemoved()) {
			Vec3 at = world(port);
			mount = CannonApi.mount(ship.level, at.x, at.y, at.z, heading(port), ship.data.owner, ship.data.ownerName, this);
			mounts[port] = mount;
		}
		return mount;
	}

	private Vec3 world(int port) {
		ShipModel.GunPort p = ShipModel.GUN_PORTS.get(port);
		double[] w = Ship.toWorld(ship.root.getX(), ship.root.getZ(), ship.root.getYRot(), p.x(), p.z());
		return new Vec3(w[0], ship.root.getY() + ShipModel.DECK_Y, w[1]);
	}

	private float heading(int port) {
		return ship.root.getYRot() + ShipModel.GUN_PORTS.get(port).yaw();
	}

	private void place(int port, CannonApi.Mount mount) {
		Vec3 at = world(port);
		mount.moveTo(at.x, at.y, at.z, heading(port));
	}

	@Override
	public boolean mounted(int port) {
		return mounts[port] != null && !mounts[port].isRemoved();
	}

	@Override
	public @Nullable Vec3 where(int port) {
		return mounted(port) ? mounts[port].entity().position() : null;
	}

	// ---------------------------------------------------------------- gunners

	@Override
	public @Nullable ServerPlayer gunner(int port) {
		return mounted(port) ? mounts[port].gunner() : null;
	}

	@Override
	public List<ServerPlayer> gunners() {
		List<ServerPlayer> found = new ArrayList<>();
		for (int port = 0; port < mounts.length; port++) {
			ServerPlayer gunner = gunner(port);
			if (gunner != null) {
				found.add(gunner);
			}
		}
		return found;
	}

	@Override
	public boolean isManning(Entity entity) {
		for (ServerPlayer gunner : gunners()) {
			if (gunner == entity) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int reload(int port) {
		return mounted(port) ? mounts[port].reloadTicks() : 0;
	}

	@Override
	public boolean fire(int port) {
		return mounted(port) && mounts[port].fire(null);
	}

	@Override
	public boolean man(ServerPlayer player, int port, int fromSeat) {
		CannonApi.Mount mount = sync(port);
		if (mount == null || (mount.gunner() != null && mount.gunner() != player)) {
			return false;
		}
		place(port, mount);
		mount.tick(); // builds the gunner's seat if there isn't one yet
		if (fromSeat >= 0) {
			returnSeat.put(player.getUUID(), fromSeat);
		}
		if (!mount.man(player)) {
			returnSeat.remove(player.getUUID());
			return false;
		}
		Ships.rememberRider(player.getUUID(), ship);
		return true;
	}

	/** The gunner got off (or was put off): back to where they were sitting, or any free seat, or ashore. */
	@Override
	public void dismounted(ServerPlayer player) {
		Integer previous = returnSeat.remove(player.getUUID());
		if (player.getVehicle() != null || player.isRemoved() || player.isDeadOrDying()) {
			return; // gone, or stepped straight onto another seat
		}
		if (closing == Closing.QUIETLY) {
			Ships.forgetRider(player.getUUID(), ship);
			return;
		}
		if (closing == Closing.NO) {
			if (previous != null && ship.seatFree(previous) && ship.seat(player, previous)) {
				return;
			}
			int free = ship.pickSeat(player);
			if (free >= 0 && ship.seat(player, free)) {
				return;
			}
		}
		Ships.forgetRider(player.getUUID(), ship);
		ship.goAshore(player, previous == null ? 1 : previous);
	}

	// ---------------------------------------------------------------- ammunition

	@Override
	public boolean takeBall(ServerPlayer gunner) {
		if (CannonItems.takeCannonball(gunner)) {
			return true;
		}
		for (Container hold : ship.holds()) {
			for (int i = 0; i < hold.getContainerSize(); i++) {
				ItemStack stack = hold.getItem(i);
				if (CannonItems.isCannonball(stack)) {
					stack.shrink(1);
					hold.setChanged();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public int countBalls(ServerPlayer gunner) {
		return CannonItems.countCannonballs(gunner) + ballsInHold();
	}

	@Override
	public int ballsInHold() {
		int n = 0;
		for (Container hold : ship.holds()) {
			for (int i = 0; i < hold.getContainerSize(); i++) {
				ItemStack stack = hold.getItem(i);
				if (CannonItems.isCannonball(stack)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- taking down

	@Override
	public void shutdown(boolean ashore) {
		closing = ashore ? Closing.ASHORE : Closing.QUIETLY;
		for (int port = 0; port < mounts.length; port++) {
			if (mounts[port] != null && !mounts[port].isRemoved()) {
				mounts[port].remove();
			}
			mounts[port] = null;
		}
		returnSeat.clear();
		closing = Closing.NO;
	}
}
