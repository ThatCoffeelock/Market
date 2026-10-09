package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The rope: lowered from the bomb hatch, it hangs down to the ground (up to {@link #MAX} blocks) and follows the
 * airship as it flies. Passengers climb down it to put boots on the ground; people on the ground grab the bottom end
 * and get hauled up into a free seat. Climbers are moved along it every tick, so they go where the airship goes.
 * Shift lets go (with a parachute). Nobody on the rope takes fall damage.
 */
final class Rope {
	static final int MAX = 48;
	/** Blocks per tick along the rope, both ways. */
	static final double SPEED = 0.4;

	enum Way { UP, DOWN }

	/** Someone on the rope: which way they're going, and how high their feet are. */
	static final class Climber {
		final ServerPlayer player;
		Way way;
		double at;
		int ticks;

		Climber(ServerPlayer player, Way way, double at) {
			this.player = player;
			this.way = way;
			this.at = at;
		}
	}

	private final Airship ship;
	boolean down;
	/** How long it hangs right now, and whether its end touches the ground. */
	double length;
	double bottom;
	boolean touches;
	/** Climbs that ended on the ground (for the smoke test). */
	int landed;
	final List<Climber> climbers = new ArrayList<>();
	private @Nullable Entity line;
	private @Nullable Entity grab;
	private double shown = -1;

	Rope(Airship ship) {
		this.ship = ship;
	}

	// ---------------------------------------------------------------- where it is

	private double[] hatch() {
		return Airship.toWorld(ship.root.getX(), ship.root.getZ(), ship.root.getYRot(), 0, AirshipModel.HATCH_Z);
	}

	/** Where the rope leaves the gondola. */
	double top() {
		return ship.root.getY() - 0.05;
	}

	/** How far down the ground is under the hatch, up to the rope's length. */
	private void measure(double[] h) {
		double topY = top();
		int start = (int) Math.floor(topY - 0.01);
		touches = false;
		bottom = topY - MAX;
		for (int y = start; y >= start - MAX && y >= ship.level.getMinY(); y--) {
			BlockPos p = BlockPos.containing(h[0], y, h[1]);
			if (!ship.level.isLoaded(p)) {
				break;
			}
			BlockState state = ship.level.getBlockState(p);
			if (!state.getCollisionShape(ship.level, p).isEmpty() || !state.getFluidState().isEmpty()) {
				bottom = Math.min(topY, y + 1.0);
				touches = true;
				break;
			}
		}
		length = Math.max(0.3, topY - bottom);
	}

	// ---------------------------------------------------------------- the captain's switch

	/** Lowers or reels in the rope. Returns why not, or null. */
	@Nullable String toggle() {
		if (down) {
			if (!climbers.isEmpty()) {
				return "Someone's still on the rope. Reeling them in like a fish would be rude.";
			}
			reelIn();
			Cmd.sound(ship.level, "minecraft:block.wool.place", ship.root.getX(), ship.root.getY(), ship.root.getZ(), 1.0f, 0.8f);
			return null;
		}
		if (ship.isGrounded()) {
			return "You're parked. Use the door, like a normal person.";
		}
		down = true;
		Cmd.sound(ship.level, "minecraft:block.wool.place", ship.root.getX(), ship.root.getY(), ship.root.getZ(), 1.0f, 0.6f);
		tick();
		return null;
	}

	private void reelIn() {
		down = false;
		shown = -1;
		if (line != null) {
			line.discard();
			line = null;
		}
		if (grab != null) {
			grab.discard();
			grab = null;
		}
	}

	/** Packed up, unloaded or gone: everyone on it lets go, and it's reeled in. */
	void release() {
		for (Climber c : new ArrayList<>(climbers)) {
			letGo(c.player, "The rope's gone. Parachute deployed!");
		}
		climbers.clear();
		reelIn();
	}

	boolean isGrab(Entity entity) {
		return grab != null && entity == grab;
	}

	boolean isClimbing(Entity entity) {
		for (Climber c : climbers) {
			if (c.player == entity) {
				return true;
			}
		}
		return false;
	}

	void forget(ServerPlayer player) {
		climbers.removeIf(c -> c.player == player);
	}

	/** Entities the rope has right now (for the smoke test). */
	int entities() {
		return (line != null && !line.isRemoved() ? 1 : 0) + (grab != null && !grab.isRemoved() ? 1 : 0);
	}

	// ---------------------------------------------------------------- climbing

	/** A passenger climbs down. Returns why not, or null. */
	@Nullable String climbDown(ServerPlayer player) {
		if (!down) {
			return "The rope isn't down. Ask the captain (menu → Lower the rope).";
		}
		if (ship.seatOf(player) < 0) {
			return "You have to be aboard to climb down.";
		}
		player.stopRiding();
		start(player, Way.DOWN, top() - 1.9);
		player.sendSystemMessage(Component.literal("Down the rope you go. Shift lets go.").withStyle(ChatFormatting.YELLOW));
		return null;
	}

	/** Someone on the ground grabbed the end. Returns why not, or null. */
	@Nullable String climbUp(ServerPlayer player) {
		if (!down) {
			return null;
		}
		if (isClimbing(player)) {
			return null;
		}
		boolean seat = false;
		for (int i = 1; i < ship.seatCount(); i++) {
			seat |= ship.seatFree(i);
		}
		if (!seat && !(ship.isOwner(player) && ship.seatFree(AirshipModel.CAPTAIN))) {
			return "No free seat up there. Wave at them instead.";
		}
		start(player, Way.UP, Math.max(bottom, player.getY()));
		player.sendSystemMessage(Component.literal("You grab the rope and they haul you up. Shift lets go.").withStyle(ChatFormatting.YELLOW));
		return null;
	}

	void start(ServerPlayer player, Way way, double at) {
		forget(player);
		climbers.add(new Climber(player, way, at));
		Cmd.sound(ship.level, "minecraft:block.ladder.step", player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f);
	}

	private void letGo(ServerPlayer player, String why) {
		Cmd.run(ship.level, "effect give " + player.getUUID() + " minecraft:slow_falling 20 0 true");
		player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.YELLOW));
	}

	// ---------------------------------------------------------------- every tick

	void tick() {
		if (!down) {
			return;
		}
		double[] h = hatch();
		measure(h);
		draw(h);
		climb(h);
	}

	private void draw(double[] h) {
		double topY = top();
		if (line == null || line.isRemoved()) {
			line = ship.summonMarker("minecraft:block_display", "block_state:\"minecraft:brown_wool\",teleport_duration:2,Tags:[\"blimey_rope\"],"
				+ "transformation:" + stretch(length), h[0], topY, h[1]);
			shown = length;
		} else {
			line.setPos(h[0], topY, h[1]);
			if (Math.abs(length - shown) > 0.25) {
				Cmd.run(ship.level, "data merge entity " + line.getUUID() + " {interpolation_duration:2,start_interpolation:0,transformation:" + stretch(length) + "}");
				shown = length;
			}
		}
		if (grab == null || grab.isRemoved()) {
			grab = ship.summonMarker("minecraft:interaction", "width:1.6f,height:2.4f,response:1b,Tags:[\"blimey_rope_end\"]", h[0], bottom, h[1]);
		} else {
			grab.setPos(h[0], bottom, h[1]);
		}
	}

	/** A thin brown line hanging this far down from the entity's position. */
	private static String stretch(double length) {
		return "{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[-0.05f," + Cmd.f(-length) + "f,-0.05f],scale:[0.1f,"
			+ Cmd.f(length) + "f,0.1f]}";
	}

	private void climb(double[] h) {
		for (Climber c : new ArrayList<>(climbers)) {
			ServerPlayer p = c.player;
			if (p.isRemoved() || p.isDeadOrDying() || p.level() != ship.level || p.getVehicle() != null) {
				climbers.remove(c);
				continue;
			}
			c.ticks++;
			if (c.ticks > 10 && p.isShiftKeyDown()) {
				climbers.remove(c);
				letGo(p, "You let go of the rope! Parachute deployed.");
				continue;
			}
			c.at += c.way == Way.DOWN ? -SPEED : SPEED;
			if (c.way == Way.DOWN && c.at <= bottom) {
				climbers.remove(c);
				c.at = bottom;
				tp(p, h, bottom);
				if (touches) {
					landed++;
					p.sendSystemMessage(Component.literal("Boots on the ground.").withStyle(ChatFormatting.GREEN));
				} else {
					letGo(p, "You ran out of rope! Parachute deployed.");
				}
				continue;
			}
			if (c.way == Way.UP && c.at >= top() - 1.9) {
				int seat = ship.pickSeat(p);
				if (seat >= 0 && ship.seat(p, seat)) {
					climbers.remove(c);
					p.sendSystemMessage(Component.literal("Hauled aboard (" + ship.seatName(seat) + ").").withStyle(ChatFormatting.GOLD));
					continue;
				}
				c.way = Way.DOWN;
				p.sendSystemMessage(Component.literal("Somebody took your seat. Back down you go.").withStyle(ChatFormatting.RED));
			}
			tp(p, h, c.at);
			if (c.ticks % 6 == 0) {
				Cmd.sound(ship.level, "minecraft:block.ladder.step", h[0], c.at, h[1], 0.6f, 1.0f);
			}
		}
	}

	private void tp(ServerPlayer p, double[] h, double y) {
		Cmd.run(ship.level, "tp " + p.getUUID() + " " + Cmd.pos(h[0], y, h[1]));
	}
}
