package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;

/**
 * The ship's look: about 50 stretched block displays, plus where people sit.
 *
 * Local coordinates: x across (+x is port, the left side when facing the bow), y up with 0 at the
 * water surface, z along the ship with +z the bow. Parts are boxes given as min corner + size.
 */
public final class ShipModel {
	/** A box of the model. Blocks are plain ids (no properties) so they work as display block states. */
	public record Part(String block, float x, float y, float z, float sx, float sy, float sz, boolean glow) {
	}

	/** A place to sit. y is the seat height above the water surface. */
	public record Spot(String name, double x, double y, double z) {
	}

	/** Spot 0 is always the captain, standing at the wheel. */
	public static final List<Spot> SPOTS = List.of(
		new Spot("Captain", 0, 1.35, -4.8),
		new Spot("Port bench", 2.1, 1.75, -2), new Spot("Starboard bench", -2.1, 1.75, -2),
		new Spot("Port bench", 2.1, 1.75, 0.5), new Spot("Starboard bench", -2.1, 1.75, 0.5),
		new Spot("Bow (port)", 0.9, 1.35, 5), new Spot("Bow (starboard)", -0.9, 1.35, 5),
		new Spot("Quarterdeck (port)", 1, 2.7, -6.5), new Spot("Quarterdeck (starboard)", -1, 2.7, -6.5));

	/** Clickable hitboxes along the ship (z positions), each 7 wide. */
	public static final double[] HITBOX_Z = {-5, 0.5, 6};
	/** Collision squares along the hull (z centres), each 6.4 wide. */
	public static final double[] HULL_Z = {-5, 0.5, 6};
	public static final double HULL_HALF = 3.2;

	private ShipModel() {
	}

	private static void box(List<Part> p, String block, double x, double y, double z, double sx, double sy, double sz) {
		p.add(new Part("minecraft:" + block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, false));
	}

	private static void light(List<Part> p, String block, double x, double y, double z, double sx, double sy, double sz) {
		p.add(new Part("minecraft:" + block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, true));
	}

	public static List<Part> parts() {
		List<Part> p = new ArrayList<>();
		String hull = "dark_oak_planks";
		String deck = "spruce_planks";

		// hull: a long body, tapering to the bow, square stern
		box(p, hull, -3, -1, -7.5, 6, 2.25, 13.5);
		box(p, hull, -2.2, -0.8, 6, 4.4, 2.05, 2);
		box(p, hull, -1.2, -0.5, 8, 2.4, 1.75, 1.5);
		box(p, hull, -0.5, -0.2, 9.5, 1, 1.45, 0.8);
		// a pale stripe along the sides
		box(p, "birch_planks", -3.02, 0.7, -7.5, 6.04, 0.2, 13.5);
		box(p, "birch_planks", -2.22, 0.7, 6, 4.44, 0.2, 2);
		// deck boards on top
		box(p, deck, -2.8, 1.25, -4.9, 5.6, 0.02, 10.8);
		box(p, deck, -2.0, 1.25, 5.9, 4, 0.02, 2);
		// railings
		box(p, deck, 2.85, 1.25, -7.5, 0.12, 0.55, 13.5);
		box(p, deck, -2.97, 1.25, -7.5, 0.12, 0.55, 13.5);
		box(p, deck, 2.05, 1.25, 6, 0.12, 0.55, 2);
		box(p, deck, -2.17, 1.25, 6, 0.12, 0.55, 2);
		box(p, deck, 1.05, 1.25, 8, 0.12, 0.45, 1.5);
		box(p, deck, -1.17, 1.25, 8, 0.12, 0.45, 1.5);

		// quarterdeck (raised stern) with windows and lanterns
		box(p, hull, -2.9, 1.25, -7.5, 5.8, 1.4, 2.6);
		box(p, deck, -2.8, 2.65, -7.4, 5.6, 0.02, 2.4);
		light(p, "yellow_stained_glass", -2, 1.6, -7.53, 1.2, 0.6, 0.04);
		light(p, "yellow_stained_glass", 0.8, 1.6, -7.53, 1.2, 0.6, 0.04);
		light(p, "lantern", 2.3, 2.67, -7.4, 0.5, 0.5, 0.5);
		light(p, "lantern", -2.8, 2.67, -7.4, 0.5, 0.5, 0.5);
		light(p, "lantern", -0.25, 1.8, 9.2, 0.5, 0.5, 0.5);

		// the wheel
		box(p, "grindstone", -0.45, 1.3, -4.3, 0.9, 0.9, 0.9);

		// main mast, yards, sails, pennant
		box(p, "spruce_log", -0.2, 1.25, 1.8, 0.4, 9.5, 0.4);
		box(p, "stripped_spruce_log", -3.3, 8.6, 1.85, 6.6, 0.25, 0.25);
		box(p, "stripped_spruce_log", -2.5, 10.1, 1.85, 5, 0.2, 0.2);
		box(p, "white_wool", -3.1, 4.2, 2.25, 6.2, 4.4, 0.1);
		box(p, "white_wool", -2.3, 8.9, 2.25, 4.6, 1.2, 0.1);
		box(p, "red_wool", 0.2, 10.4, 1.95, 1.2, 0.35, 0.05);
		// mizzen mast and sail
		box(p, "spruce_log", -0.18, 2.65, -3.2, 0.36, 6, 0.36);
		box(p, "stripped_spruce_log", -2.3, 7.8, -3.15, 4.6, 0.22, 0.22);
		box(p, "white_wool", -2.1, 4.4, -2.75, 4.2, 3.4, 0.1);
		// bowsprit and jib
		box(p, "spruce_log", -0.12, 1.3, 9.5, 0.24, 0.24, 3.2);
		box(p, "white_wool", -0.04, 1.6, 9.8, 0.08, 3.2, 1.8);

		// benches along the sides
		box(p, deck, 1.8, 1.27, -3, 0.9, 0.45, 4.3);
		box(p, deck, -2.7, 1.27, -3, 0.9, 0.45, 4.3);

		// cargo on deck: barrels and crates
		box(p, "barrel", 0.8, 1.27, -1.8, 0.8, 0.8, 0.8);
		box(p, "barrel", -1.6, 1.27, -1.8, 0.8, 0.8, 0.8);
		box(p, "barrel", 0.8, 1.27, 3.4, 0.8, 0.8, 0.8);
		box(p, "barrel", 0.8, 2.07, 3.4, 0.8, 0.8, 0.8);
		box(p, "spruce_planks", -1.8, 1.27, 3.2, 1.2, 1.0, 1.2);
		box(p, "barrel", -1.6, 1.27, 4.5, 0.8, 0.8, 0.8);
		// a coil of rope and the anchor on the bow
		box(p, "brown_wool", 1.2, 1.27, 6.4, 0.6, 0.2, 0.6);
		box(p, "iron_block", -2.32, 0.3, 7, 0.1, 0.9, 0.3);
		box(p, "iron_block", -2.32, 0.2, 6.8, 0.1, 0.12, 0.7);
		return p;
	}
}
