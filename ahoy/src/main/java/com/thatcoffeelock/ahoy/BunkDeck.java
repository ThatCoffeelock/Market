package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

/**
 * The beds slotted into a ship's bunks, and the people sleeping in them.
 *
 * A ship is made of display entities, and the game only lets a player sleep where there is a real bed block. So a
 * sleeper gets a small hidden bed up in the sky above the ship (just for the game to find), while their body rides an
 * invisible seat on the bunk. The sky bed is taken away again when they wake up. Rough water wakes them.
 */
final class BunkDeck {
	private static final Set<String> COLOURS = Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
	private static final double MAX_DRIFT = 6;

	private static final class Sleeper {
		final UUID player;
		final int bunk;
		final int fromSeat;
		final BlockPos foot;
		final BlockPos head;
		final double x;
		final double z;
		final Entity seat;

		Sleeper(UUID player, int bunk, int fromSeat, BlockPos foot, BlockPos head, double x, double z, Entity seat) {
			this.player = player;
			this.bunk = bunk;
			this.fromSeat = fromSeat;
			this.foot = foot;
			this.head = head;
			this.x = x;
			this.z = z;
			this.seat = seat;
		}
	}

	private final Ship ship;
	private final Entity[] bodies = new Entity[ShipModel.BUNKS.size()];
	private final String[] drawn = new String[ShipModel.BUNKS.size()];
	private final List<Sleeper> sleepers = new ArrayList<>();
	private float placedYaw = Float.NaN;

	BunkDeck(Ship ship) {
		this.ship = ship;
	}

	static boolean isBed(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return id.startsWith("minecraft:") && id.endsWith("_bed");
	}

	private static String colour(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		String colour = id.substring(0, id.length() - "_bed".length());
		return COLOURS.contains(colour) ? colour : "yellow"; // the straw bed
	}

	// ---------------------------------------------------------------- drawing the bunks

	void tick() {
		float yaw = ship.root.getYRot();
		boolean turned = yaw != placedYaw;
		placedYaw = yaw;
		for (int i = 0; i < bodies.length; i++) {
			ItemStack bed = ship.data.bunks.getItem(i);
			String wanted = isBed(bed) ? colour(bed) : null;
			if (bodies[i] != null && (bodies[i].isRemoved() || !String.valueOf(wanted).equals(drawn[i]))) {
				if (!bodies[i].isRemoved()) {
					discardBody(i);
				}
				bodies[i] = null;
			}
			if (bodies[i] == null && wanted != null && !ship.isRemoved()) {
				ShipModel.Bunk b = ShipModel.BUNKS.get(i);
				double[] w = Ship.toWorld(ship.root.getX(), ship.root.getZ(), yaw, b.x(), b.z());
				bodies[i] = ship.summonMarker("minecraft:item_display", bodyNbt(wanted, yaw), w[0], ship.root.getY() + ShipModel.DECK_Y, w[1]);
				drawn[i] = wanted;
			}
			if (bodies[i] != null) {
				ShipModel.Bunk b = ShipModel.BUNKS.get(i);
				double[] w = Ship.toWorld(ship.root.getX(), ship.root.getZ(), yaw, b.x(), b.z());
				bodies[i].setPos(w[0], ship.root.getY() + ShipModel.DECK_Y, w[1]);
				if (turned) {
					bodies[i].setYRot(yaw);
					for (Entity part : bodies[i].getPassengers()) {
						part.setYRot(yaw);
					}
				}
			}
		}
		keepSleepers(yaw);
	}

	private static String bunkPart(String block, double x, double y, double z, double sx, double sy, double sz, float yaw) {
		return "{id:\"minecraft:block_display\",block_state:\"minecraft:" + block + "\",Tags:[\"ahoy_part\"],Rotation:[" + Cmd.f(yaw)
			+ "f,0f],teleport_duration:2,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:["
			+ Cmd.f(x) + "f," + Cmd.f(y) + "f," + Cmd.f(z) + "f],scale:[" + Cmd.f(sx) + "f," + Cmd.f(sy) + "f," + Cmd.f(sz) + "f]}}";
	}

	/** A bed drawn from boxes: wooden frame, coloured mattress, white pillow at the head (the bow end). */
	private static String bodyNbt(String colour, float yaw) {
		return "Rotation:[" + Cmd.f(yaw) + "f,0f],teleport_duration:2,Tags:[\"ahoy_part\"],Passengers:["
			+ bunkPart("dark_oak_planks", -0.5, 0, -1, 1, 0.2, 2, yaw) + ","
			+ bunkPart(colour + "_wool", -0.46, 0.2, -0.96, 0.92, 0.22, 1.92, yaw) + ","
			+ bunkPart("white_wool", -0.36, 0.42, 0.45, 0.72, 0.12, 0.46, yaw) + "]";
	}

	private void discardBody(int i) {
		for (Entity part : new ArrayList<>(bodies[i].getPassengers())) {
			part.discard();
		}
		bodies[i].discard();
	}

	boolean drawn(int bunk) {
		return bodies[bunk] != null && !bodies[bunk].isRemoved();
	}

	// ---------------------------------------------------------------- sleeping

	List<ServerPlayer> sleepers() {
		List<ServerPlayer> found = new ArrayList<>();
		for (Sleeper s : sleepers) {
			ServerPlayer p = ship.level.getServer().getPlayerList().getPlayer(s.player);
			if (p != null) {
				found.add(p);
			}
		}
		return found;
	}

	boolean isSleeping(Entity entity) {
		for (Sleeper s : sleepers) {
			if (s.player.equals(entity.getUUID())) {
				return true;
			}
		}
		return false;
	}

	@Nullable ServerPlayer sleeperIn(int bunk) {
		for (Sleeper s : sleepers) {
			if (s.bunk == bunk) {
				return ship.level.getServer().getPlayerList().getPlayer(s.player);
			}
		}
		return null;
	}

	/** Lies the player down in that bunk. Returns null if it worked, otherwise why not. */
	@Nullable String lieDown(ServerPlayer player, int bunk) {
		if (!drawn(bunk)) {
			return "That bunk has no bed in it yet.";
		}
		if (isSleeping(player)) {
			return "You're already lying down.";
		}
		for (Sleeper s : sleepers) {
			if (s.bunk == bunk) {
				return "Somebody's already in that bunk.";
			}
		}
		if (ship.level.isBrightOutside()) {
			return "You can only sleep at night or during a thunderstorm.";
		}
		if (ship.speed > 0.05) {
			return "The ship is under way. Drop the sails first, nobody sleeps through this.";
		}
		int fromSeat = ship.seatOf(player);
		ShipModel.Bunk b = ShipModel.BUNKS.get(bunk);
		double[] w = Ship.toWorld(ship.root.getX(), ship.root.getZ(), ship.root.getYRot(), b.x(), b.z());
		double y = ship.root.getY() + ShipModel.DECK_Y + 0.5;
		Entity seat = ship.summonMarker("minecraft:item_display", "teleport_duration:2,Tags:[\"ahoy_seat\"]", w[0], y, w[1]);
		if (seat == null) {
			return "Couldn't make the bunk.";
		}
		// the game wants a real bed to sleep in: a hidden one in the sky above the ship, aimed along it
		Direction facing = Direction.fromYRot(ship.root.getYRot());
		BlockPos foot = BlockPos.containing(w[0], ship.root.getY() + 24, w[1]);
		BlockPos head = foot.relative(facing);
		BlockState bed = Blocks.STRAW_BED.defaultBlockState().setValue(AbstractBedBlock.FACING, facing);
		ship.level.setBlock(foot, bed.setValue(AbstractBedBlock.PART, BedPart.FOOT), 2);
		ship.level.setBlock(head, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD), 2);

		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		player.startSleeping(foot);
		player.startRiding(seat, true, false);
		sleepers.add(new Sleeper(player.getUUID(), bunk, fromSeat, foot, head, ship.root.getX(), ship.root.getZ(), seat));
		Ships.rememberRider(player.getUUID(), ship);
		return null;
	}

	/** Called every tick: wakes anyone the game woke (morning, damage), who got out, or whose ship is on the move. */
	private void keepSleepers(float yaw) {
		for (Sleeper s : new ArrayList<>(sleepers)) {
			ServerPlayer p = ship.level.getServer().getPlayerList().getPlayer(s.player);
			if (p == null || p.isRemoved() || p.isDeadOrDying()) {
				cleanup(s);
				continue;
			}
			double drift = Math.hypot(ship.root.getX() - s.x, ship.root.getZ() - s.z);
			if (!p.isSleeping() || p.getVehicle() != s.seat) {
				wake(p, true);
			} else if (drift > MAX_DRIFT) {
				p.sendSystemMessage(Component.literal("Rough water! You woke up.").withStyle(ChatFormatting.YELLOW));
				wake(p, true);
			} else {
				ShipModel.Bunk b = ShipModel.BUNKS.get(s.bunk);
				double[] w = Ship.toWorld(ship.root.getX(), ship.root.getZ(), yaw, b.x(), b.z());
				s.seat.setPos(w[0], ship.root.getY() + ShipModel.DECK_Y + 0.5, w[1]);
				s.seat.setYRot(yaw);
			}
		}
	}

	/** Gets the player up, takes the sky bed away and puts them back in a seat (if {@code back}) or just leaves them. */
	void wake(ServerPlayer player, boolean back) {
		Sleeper found = null;
		for (Sleeper s : sleepers) {
			if (s.player.equals(player.getUUID())) {
				found = s;
			}
		}
		if (found == null) {
			return;
		}
		final Sleeper s = found;
		if (player.getVehicle() == s.seat) {
			player.stopRiding();
		}
		if (player.isSleeping()) {
			player.stopSleeping();
		}
		cleanup(s);
		if (!back) {
			Ships.forgetRider(player.getUUID(), ship);
			return;
		}
		int free = s.fromSeat >= 0 && ship.seatFree(s.fromSeat) ? s.fromSeat : ship.pickSeat(player);
		if (free < 0 || !ship.seat(player, free)) {
			Ships.forgetRider(player.getUUID(), ship);
			ship.goAshore(player, 1);
		}
	}

	private void cleanup(Sleeper s) {
		sleepers.remove(s);
		ship.level.setBlock(s.head, Blocks.AIR.defaultBlockState(), 2 | 16);
		ship.level.setBlock(s.foot, Blocks.AIR.defaultBlockState(), 2 | 16);
		if (!s.seat.isRemoved()) {
			s.seat.ejectPassengers();
			s.seat.discard();
		}
	}

	/** The ship is going away: everyone gets up; {@code ashore} puts them on dry land instead of in a seat. */
	void shutdown(boolean ashore) {
		for (Sleeper s : new ArrayList<>(sleepers)) {
			ServerPlayer p = ship.level.getServer().getPlayerList().getPlayer(s.player);
			if (p == null) {
				cleanup(s);
				continue;
			}
			wake(p, false);
			if (ashore) {
				ship.goAshore(p, 1);
			}
		}
		for (int i = 0; i < bodies.length; i++) {
			if (bodies[i] != null && !bodies[i].isRemoved()) {
				discardBody(i);
			}
			bodies[i] = null;
			drawn[i] = null;
		}
	}
}
