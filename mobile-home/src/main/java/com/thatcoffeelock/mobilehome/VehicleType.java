package com.thatcoffeelock.mobilehome;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Stats, seats and the block-display model of each vehicle.
 *
 * Local coordinates: origin is the bottom-centre of the vehicle, +Z is the front, +X is the left side
 * (left-hand drive, so the driver sits on +X), +Y is up. Model parts are boxes: min corner + size.
 */
public enum VehicleType {
	VAN("van", "Camper Van", 1.2, 2.5, 2.45, 3.2f, 2.6f,
		0.5, 0.02, 4.5f, false, new double[] {0.6, 1.1}, 1, 5.0,
		"minecraft:block.note_block.didgeridoo", 1.4f,
		new Seat[] {
			new Seat("Driver", 0.55, 0.85, 1.25),
			new Seat("Shotgun", -0.55, 0.85, 1.25),
			new Seat("Back seat (left)", 0.55, 0.85, -0.3),
			new Seat("Back seat (right)", -0.55, 0.85, -0.3)}),
	TANK("tank", "Tank", 1.55, 2.6, 2.7, 3.4f, 2.8f,
		0.3, 0.012, 3.0f, true, new double[] {0.6, 1.1, 1.6, 2.1}, 2, 7.0,
		"minecraft:event.raid.horn", 1.6f,
		new Seat[] {
			new Seat("Driver", 0.45, 1.45, 0.1),
			new Seat("Gunner", -0.45, 1.45, 0.1),
			new Seat("Commander (head out of the hatch)", 0.0, 2.72, -0.7)});

	/** One seat. The seat entity sits at this point; a seated player's eyes end up about 1 block higher. */
	public record Seat(String name, double x, double y, double z) {
	}

	/** One box of the model. */
	public record Part(String block, float x, float y, float z, float sx, float sy, float sz, boolean glow) {
	}

	/** Every fuel tick from a furnace is worth this many driving ticks. One coal = 160 seconds of van driving. */
	public static final int FUEL_EFFICIENCY = 2;
	/** Tank capacity: 64 coal. */
	public static final int FUEL_CAPACITY = 1600 * FUEL_EFFICIENCY * 64;

	public final String id;
	public final String displayName;
	public final double halfWidth;
	public final double halfLength;
	public final double height;
	public final float hitboxWidth;
	public final float hitboxHeight;
	public final double maxSpeed;
	public final double accel;
	public final float turnRate;
	/** Tanks spin on the spot. Vans need to be rolling to steer. */
	public final boolean pivotTurn;
	public final double[] steps;
	public final int fuelPerTick;
	public final double repelRadius;
	public final String horn;
	public final float hornPitch;
	public final Seat[] seats;

	VehicleType(String id, String displayName, double halfWidth, double halfLength, double height, float hitboxWidth, float hitboxHeight,
				double maxSpeed, double accel, float turnRate, boolean pivotTurn, double[] steps, int fuelPerTick, double repelRadius,
				String horn, float hornPitch, Seat[] seats) {
		this.id = id;
		this.displayName = displayName;
		this.halfWidth = halfWidth;
		this.halfLength = halfLength;
		this.height = height;
		this.hitboxWidth = hitboxWidth;
		this.hitboxHeight = hitboxHeight;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.turnRate = turnRate;
		this.pivotTurn = pivotTurn;
		this.steps = steps;
		this.fuelPerTick = fuelPerTick;
		this.repelRadius = repelRadius;
		this.horn = horn;
		this.hornPitch = hornPitch;
		this.seats = seats;
	}

	public Item kitItem() {
		return this == TANK ? Items.FURNACE_MINECART : Items.CHEST_MINECART;
	}

	public static VehicleType byId(String id) {
		for (VehicleType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return VAN;
	}

	// ---------------------------------------------------------------- models

	private static final String BLACK = "minecraft:black_concrete";
	private static final String WHITE = "minecraft:white_concrete";
	private static final String GRAY = "minecraft:gray_concrete";
	private static final String IRON = "minecraft:iron_block";
	private static final String BARREL = "minecraft:barrel";
	private static final String OLIVE = "minecraft:green_terracotta";

	private static void box(List<Part> parts, String block, double x, double y, double z, double sx, double sy, double sz) {
		parts.add(new Part(block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, false));
	}

	private static void light(List<Part> parts, String block, double x, double y, double z, double sx, double sy, double sz) {
		parts.add(new Part(block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, true));
	}

	public List<Part> parts() {
		List<Part> parts = new ArrayList<>();
		if (this == TANK) {
			tank(parts);
		} else {
			van(parts);
		}
		return parts;
	}

	/** A split-screen hippie bus. Light blue and white, wood floor, bed in the back, barrels everywhere. */
	private static void van(List<Part> p) {
		String body = "minecraft:light_blue_concrete";
		String glass = "minecraft:light_gray_stained_glass";

		// wheels + hubcaps
		for (double zc : new double[] {1.55, -1.55}) {
			box(p, BLACK, 0.95, 0, zc - 0.35, 0.3, 0.7, 0.7);
			box(p, BLACK, -1.25, 0, zc - 0.35, 0.3, 0.7, 0.7);
			box(p, IRON, 1.25, 0.2, zc - 0.15, 0.03, 0.3, 0.3);
			box(p, IRON, -1.28, 0.2, zc - 0.15, 0.03, 0.3, 0.3);
		}
		box(p, BLACK, -1.0, 0.3, -2.4, 2.0, 0.15, 4.8);                // chassis
		box(p, "minecraft:spruce_planks", -1.2, 0.45, -2.5, 2.4, 0.1, 5.0); // floor

		// lower body (hollow, so it looks like a room from the inside)
		box(p, body, 1.1, 0.55, -2.5, 0.1, 0.75, 5.0);
		box(p, body, -1.2, 0.55, -2.5, 0.1, 0.75, 5.0);
		box(p, body, -1.1, 0.55, 2.4, 2.2, 0.75, 0.1);
		box(p, body, -1.1, 0.55, -2.5, 2.2, 0.75, 0.1);
		// white belt line
		box(p, WHITE, 1.1, 1.3, -2.5, 0.1, 0.1, 5.0);
		box(p, WHITE, -1.2, 1.3, -2.5, 0.1, 0.1, 5.0);
		box(p, WHITE, -1.1, 1.3, 2.4, 2.2, 0.1, 0.1);
		box(p, WHITE, -1.1, 1.3, -2.5, 2.2, 0.1, 0.1);
		// windows all round
		box(p, glass, 1.13, 1.4, -2.45, 0.04, 0.9, 4.9);
		box(p, glass, -1.17, 1.4, -2.45, 0.04, 0.9, 4.9);
		box(p, glass, -1.15, 1.4, 2.43, 2.3, 0.9, 0.04);
		box(p, glass, -1.15, 1.4, -2.47, 2.3, 0.9, 0.04);
		// pillars, including the split windshield
		for (double[] pillar : new double[][] {{1.1, 2.4}, {-1.2, 2.4}, {1.1, -2.5}, {-1.2, -2.5}, {1.1, 0.35}, {-1.2, 0.35}, {-0.05, 2.4}}) {
			box(p, WHITE, pillar[0], 1.4, pillar[1], 0.1, 0.9, 0.1);
		}
		box(p, WHITE, -1.2, 2.3, -2.5, 2.4, 0.12, 5.0); // roof

		// face: badge, bumpers, lights, and a Dutch yellow number plate
		box(p, IRON, -0.15, 0.95, 2.5, 0.3, 0.3, 0.03);
		box(p, "minecraft:light_gray_concrete", -1.25, 0.35, 2.48, 2.5, 0.18, 0.1);
		box(p, "minecraft:light_gray_concrete", -1.25, 0.35, -2.58, 2.5, 0.18, 0.1);
		light(p, "minecraft:sea_lantern", 0.6, 0.8, 2.5, 0.35, 0.3, 0.03);
		light(p, "minecraft:sea_lantern", -0.95, 0.8, 2.5, 0.35, 0.3, 0.03);
		light(p, "minecraft:redstone_block", 0.85, 0.85, -2.53, 0.2, 0.3, 0.03);
		light(p, "minecraft:redstone_block", -1.05, 0.85, -2.53, 0.2, 0.3, 0.03);
		box(p, "minecraft:yellow_concrete", -0.3, 0.55, -2.54, 0.6, 0.15, 0.02);

		// cockpit
		box(p, GRAY, -1.1, 1.05, 1.95, 2.2, 0.3, 0.45);   // dashboard
		box(p, BLACK, 0.35, 1.25, 1.8, 0.4, 0.4, 0.05);   // steering wheel (it's square, don't ask)
		String seat = "minecraft:brown_wool";
		box(p, seat, 0.25, 0.55, 0.95, 0.6, 0.3, 0.6);
		box(p, seat, 0.25, 0.85, 0.83, 0.6, 0.7, 0.12);
		box(p, seat, -0.85, 0.55, 0.95, 0.6, 0.3, 0.6);
		box(p, seat, -0.85, 0.85, 0.83, 0.6, 0.7, 0.12);
		box(p, seat, -1.1, 0.55, -0.6, 2.2, 0.3, 0.6);    // back bench
		box(p, seat, -1.1, 0.85, -0.72, 2.2, 0.6, 0.12);

		// the "home" part: a bed and the storage units
		box(p, "minecraft:red_wool", 0.0, 0.55, -2.4, 1.1, 0.3, 1.6);
		box(p, "minecraft:white_wool", 0.1, 0.85, -2.35, 0.9, 0.12, 0.35);
		box(p, BARREL, -1.1, 0.55, -2.4, 0.55, 0.55, 0.55);
		box(p, BARREL, -0.55, 0.55, -2.4, 0.55, 0.55, 0.55);
		box(p, BARREL, -1.1, 1.1, -2.4, 0.55, 0.55, 0.55);
		box(p, BARREL, -1.1, 0.55, -1.85, 0.55, 0.55, 0.55);

		// roof rack with more storage and a surfboard, because vibes
		box(p, GRAY, 0.85, 2.42, -2.2, 0.06, 0.08, 3.8);
		box(p, GRAY, -0.91, 2.42, -2.2, 0.06, 0.08, 3.8);
		box(p, GRAY, -0.91, 2.42, -2.2, 1.82, 0.06, 0.06);
		box(p, GRAY, -0.91, 2.42, 1.54, 1.82, 0.06, 0.06);
		box(p, BLACK, -0.8, 2.45, -0.6, 1.6, 0.35, 1.5);
		box(p, BARREL, -0.75, 2.45, -2.0, 0.6, 0.6, 0.6);
		box(p, BARREL, 0.1, 2.45, -2.0, 0.6, 0.6, 0.6);
		box(p, "minecraft:orange_concrete", -0.3, 2.82, -0.9, 0.6, 0.06, 2.4);
	}

	/** Olive drab, treads, a bunker of a turret with vision slits, and crates on the back deck. */
	private static void tank(List<Part> p) {
		// tracks, road wheels and fenders
		box(p, BLACK, 1.0, 0, -2.6, 0.5, 0.8, 5.2);
		box(p, BLACK, -1.5, 0, -2.6, 0.5, 0.8, 5.2);
		for (double zc = -2; zc <= 2; zc++) {
			box(p, GRAY, 1.5, 0.12, zc - 0.25, 0.03, 0.5, 0.5);
			box(p, GRAY, -1.53, 0.12, zc - 0.25, 0.03, 0.5, 0.5);
		}
		box(p, OLIVE, 0.95, 0.8, -2.65, 0.6, 0.06, 5.3);
		box(p, OLIVE, -1.55, 0.8, -2.65, 0.6, 0.06, 5.3);

		// hull + glacis + headlights
		box(p, OLIVE, -1.0, 0.3, -2.4, 2.0, 1.0, 4.8);
		box(p, OLIVE, -1.0, 0.3, 2.4, 2.0, 0.6, 0.15);
		light(p, "minecraft:sea_lantern", 0.55, 0.65, 2.55, 0.25, 0.2, 0.03);
		light(p, "minecraft:sea_lantern", -0.8, 0.65, 2.55, 0.25, 0.2, 0.03);

		// turret: armoured lower walls, a band of vision slits, corner posts, roof
		box(p, OLIVE, 0.85, 1.3, -1.3, 0.1, 0.8, 2.2);
		box(p, OLIVE, -0.95, 1.3, -1.3, 0.1, 0.8, 2.2);
		box(p, OLIVE, -0.85, 1.3, 0.8, 1.7, 0.8, 0.1);
		box(p, OLIVE, -0.85, 1.3, -1.3, 1.7, 0.8, 0.1);
		String slit = "minecraft:gray_stained_glass";
		box(p, slit, 0.87, 2.1, -1.28, 0.06, 0.5, 2.16);
		box(p, slit, -0.93, 2.1, -1.28, 0.06, 0.5, 2.16);
		box(p, slit, -0.85, 2.1, 0.82, 1.7, 0.5, 0.06);
		box(p, slit, -0.85, 2.1, -1.28, 1.7, 0.5, 0.06);
		for (double[] post : new double[][] {{0.85, 0.8}, {-0.95, 0.8}, {0.85, -1.3}, {-0.95, -1.3}}) {
			box(p, OLIVE, post[0], 2.1, post[1], 0.1, 0.5, 0.1);
		}
		box(p, OLIVE, -0.95, 2.6, -1.3, 1.9, 0.12, 2.2);
		box(p, OLIVE, -0.3, 2.72, -1.25, 0.6, 0.5, 0.06);  // open hatch lid
		box(p, "minecraft:white_concrete", 0.96, 1.5, -0.4, 0.02, 0.35, 0.35); // unit marking

		// the gun: mantlet, barrel, muzzle brake
		box(p, OLIVE, -0.35, 1.85, 0.9, 0.7, 0.5, 0.3);
		box(p, OLIVE, -0.11, 1.99, 1.2, 0.22, 0.22, 2.8);
		box(p, BLACK, -0.17, 1.93, 3.9, 0.34, 0.34, 0.3);

		// antenna (an end rod stretched upwards)
		box(p, "minecraft:end_rod", -1.15, 2.72, -1.75, 1.0, 1.4, 1.0);

		// seats inside the turret
		box(p, "minecraft:black_wool", 0.15, 1.3, -0.2, 0.6, 0.15, 0.6);
		box(p, "minecraft:black_wool", -0.75, 1.3, -0.2, 0.6, 0.15, 0.6);

		// storage units: barrels and a crate on the back deck, jerry cans and a stowage box on the fenders
		box(p, BARREL, 0.3, 1.3, -2.35, 0.55, 0.55, 0.55);
		box(p, BARREL, -0.95, 1.3, -2.35, 0.55, 0.55, 0.55);
		box(p, "minecraft:spruce_planks", -0.35, 1.3, -2.35, 0.6, 0.45, 0.9);
		box(p, "minecraft:red_concrete", 1.05, 0.86, -2.4, 0.15, 0.4, 0.3);
		box(p, "minecraft:red_concrete", 1.05, 0.86, -2.05, 0.15, 0.4, 0.3);
		box(p, "minecraft:dark_oak_planks", -1.5, 0.86, -1.0, 0.45, 0.35, 1.6);

		// a bit of camo
		String camo = "minecraft:brown_terracotta";
		box(p, camo, 0.2, 1.3, 1.4, 0.6, 0.02, 0.7);
		box(p, camo, -0.8, 1.3, 1.9, 0.5, 0.02, 0.4);
		box(p, camo, 0.96, 1.4, 0.1, 0.02, 0.4, 0.5);
		box(p, camo, -0.97, 1.6, -1.0, 0.02, 0.3, 0.6);
	}
}
