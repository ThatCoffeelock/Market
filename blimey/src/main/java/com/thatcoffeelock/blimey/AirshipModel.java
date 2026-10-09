package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.List;

/**
 * The airship's look: about 70 stretched block displays, plus where people sit and what it bumps into.
 *
 * Local coordinates: x across (+x is port, the left side when facing the bow), y up with 0 at the bottom of the
 * gondola (the keel, which is what lands on the ground), z along the ship with +z the bow. Parts are boxes given
 * as min corner + size. Every block here is a plain id that other mods' models already use on 26.3: one bad id and
 * the whole summon command fails.
 */
public final class AirshipModel {
	/** A box of the model. */
	public record Part(String block, float x, float y, float z, float sx, float sy, float sz, boolean glow) {
	}

	/** A place to sit. y is the seat height above the keel. */
	public record Spot(String name, double x, double y, double z) {
	}

	/** Something the airship can bump into: an upright square column, centred on a local x/z point. */
	public record Bulk(double x, double z, double half, double y0, double y1) {
	}

	/** Spot 0 is the captain at the wheel, spot 1 the bombardier over the bomb hatch. */
	public static final List<Spot> SPOTS = List.of(
		new Spot("Captain", 0, 0.45, 2.8),
		new Spot("Bombardier", 0, 0.45, 0.4),
		new Spot("Port bench (fore)", 1.3, 0.85, -1.6), new Spot("Starboard bench (fore)", -1.3, 0.85, -1.6),
		new Spot("Port bench (aft)", 1.3, 0.85, -3.2), new Spot("Starboard bench (aft)", -1.3, 0.85, -3.2));
	public static final int CAPTAIN = 0;
	public static final int BOMBARDIER = 1;

	/** Where bombs leave the ship: just under the hatch in the gondola floor. */
	public static final double HATCH_Y = -0.6;
	public static final double HATCH_Z = 0.4;

	/** Clickable hitboxes along the gondola (z positions). */
	public static final double[] HITBOX_Z = {-3, 0.2, 3.2};
	public static final float HITBOX_WIDTH = 4.0f;
	public static final float HITBOX_HEIGHT = 2.6f;

	/** Where the engine exhausts are, for smoke. */
	public static final double[][] EXHAUSTS = {{4.15, 3.7, -1.9}, {-4.15, 3.7, -1.9}};

	/** The gondola, the engines, the envelope and the tail, as columns. */
	public static final List<Bulk> BULK = List.of(
		new Bulk(0, -3, 1.9, 0, 2.4), new Bulk(0, 0, 1.9, 0, 2.4), new Bulk(0, 3, 1.9, 0, 2.4), new Bulk(0, 5, 1.3, 0, 2.2),
		new Bulk(4.15, -0.9, 1.0, 1.9, 4.2), new Bulk(-4.15, -0.9, 1.0, 1.9, 4.2),
		new Bulk(0, -10, 3.6, 3.9, 11.1), new Bulk(0, -6, 3.6, 3.9, 11.1), new Bulk(0, -2, 3.6, 3.9, 11.1),
		new Bulk(0, 2, 3.6, 3.9, 11.1), new Bulk(0, 6, 3.6, 3.9, 11.1), new Bulk(0, 10, 3.0, 4.5, 10.5),
		new Bulk(0, -11.5, 2.5, 3.8, 13.1));
	/** The envelope's top, above the keel (for the ceiling). */
	public static final double TOP = 13.1;

	private AirshipModel() {
	}

	private static void box(List<Part> p, String block, double x, double y, double z, double sx, double sy, double sz) {
		p.add(new Part("minecraft:" + block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, false));
	}

	private static void light(List<Part> p, String block, double x, double y, double z, double sx, double sy, double sz) {
		p.add(new Part("minecraft:" + block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, true));
	}

	/** Two overlapping boxes, one wide and one tall, make a section of the envelope look round from the front. */
	private static void section(List<Part> p, String block, double z, double length, double half, double yMid, double radius) {
		box(p, block, -half, yMid - radius * 0.72, z, half * 2, radius * 1.44, length);
		box(p, block, -half * 0.72, yMid - radius, z, half * 1.44, radius * 2, length);
	}

	public static List<Part> parts() {
		List<Part> p = new ArrayList<>();
		String hull = "iron_block";
		String trim = "polished_blackstone";
		double mid = 7.5;

		// ---- the envelope: a riveted iron cigar, 25 long, nose to the bow
		section(p, hull, -8, 16, 3.5, mid, 3.5);
		section(p, hull, 8, 2.5, 2.8, mid, 2.9);
		section(p, hull, 10.5, 1.5, 1.9, mid, 1.9);
		box(p, hull, -0.8, mid - 0.5, 12, 1.6, 1.0, 0.6);
		section(p, hull, -10.5, 2.5, 2.8, mid, 2.9);
		section(p, hull, -12.3, 1.8, 1.6, mid, 1.6);
		// copper ribs around it
		for (double z : new double[] {-6, 0, 6}) {
			section(p, "copper_block", z - 0.15, 0.3, 3.56, mid, 3.56);
		}
		// a grey band along each flank, where the name goes
		box(p, "light_gray_concrete", 3.51, mid - 0.6, -7.5, 0.04, 1.2, 15);
		box(p, "light_gray_concrete", -3.55, mid - 0.6, -7.5, 0.04, 1.2, 15);
		// nose light, port (red) and starboard (green) running lights
		light(p, "sea_lantern", -0.3, mid - 0.3, 12.6, 0.6, 0.6, 0.06);
		light(p, "red_concrete", 3.52, mid + 1.4, 3.0, 0.08, 0.4, 0.4);
		light(p, "green_terracotta", -3.6, mid + 1.4, 3.0, 0.08, 0.4, 0.4);

		// ---- tail fins
		box(p, hull, -0.1, 10.4, -12.6, 0.2, 2.2, 3.0);
		box(p, "red_concrete", -0.12, 12.6, -12.6, 0.24, 0.5, 3.0);
		box(p, hull, -0.1, 3.8, -12.4, 0.2, 2.4, 2.6);
		box(p, hull, 1.5, mid - 0.1, -12.6, 2.9, 0.2, 3.0);
		box(p, hull, -4.4, mid - 0.1, -12.6, 2.9, 0.2, 3.0);
		box(p, "red_concrete", 4.4, mid - 0.12, -12.6, 0.3, 0.24, 3.0);
		box(p, "red_concrete", -4.7, mid - 0.12, -12.6, 0.3, 0.24, 3.0);

		// ---- struts from the gondola roof up to the envelope
		for (double x : new double[] {1.45, -1.6}) {
			for (double z : new double[] {3.3, -0.2, -3.7}) {
				box(p, trim, x, 2.3, z, 0.15, 2.2, 0.15);
			}
		}

		// ---- engines on outriggers, with propellers at the back
		for (int side = -1; side <= 1; side += 2) {
			double c = side * 4.15;
			box(p, trim, side > 0 ? 1.8 : -4.0, 2.9, -1.0, 2.2, 0.2, 0.2);
			box(p, trim, side > 0 ? 1.6 : -2.0, 3.0, -1.0, 0.4, 1.3, 0.2);
			box(p, "blast_furnace", c - 0.55, 2.5, -1.7, 1.1, 1.1, 1.8);
			box(p, "copper_block", c - 0.6, 2.45, 0.05, 1.2, 1.2, 0.15);
			box(p, "copper_block", c - 0.15, 2.9, -1.95, 0.3, 0.3, 0.25);
			box(p, "dark_oak_planks", c - 0.075, 1.95, -2.0, 0.15, 2.2, 0.06);
			box(p, "dark_oak_planks", c - 1.1, 2.975, -2.0, 2.2, 0.15, 0.06);
			box(p, trim, c - 0.15, 3.6, -1.6, 0.3, 0.5, 0.3);
		}

		// ---- the gondola: an iron cabin with windows all round
		box(p, hull, -1.8, 0, -4.6, 3.6, 0.35, 9.1);
		box(p, hull, -1.3, 0.1, 4.5, 2.6, 1.0, 0.8);
		box(p, hull, -0.7, 0.3, 5.3, 1.4, 0.8, 0.5);
		box(p, "gray_concrete", 1.7, 0.35, -4.6, 0.1, 0.9, 9.1);
		box(p, "gray_concrete", -1.8, 0.35, -4.6, 0.1, 0.9, 9.1);
		box(p, "gray_concrete", -1.8, 0.35, -4.7, 3.6, 2.0, 0.1);
		box(p, "light_blue_stained_glass", 1.72, 1.25, -4.5, 0.06, 0.8, 9.0);
		box(p, "light_blue_stained_glass", -1.78, 1.25, -4.5, 0.06, 0.8, 9.0);
		box(p, "light_blue_stained_glass", -1.3, 1.1, 4.5, 2.6, 0.95, 0.8);
		box(p, hull, -1.8, 2.05, -4.7, 3.6, 0.25, 10.0);
		for (double x : new double[] {1.68, -1.8}) {
			for (double z : new double[] {4.4, 0, -4.6}) {
				box(p, trim, x, 0.35, z, 0.12, 1.7, 0.12);
			}
		}
		// inside: the wheel, the bomb hatch, benches, a barrel and a lamp
		box(p, "grindstone", -0.4, 0.4, 3.6, 0.8, 0.8, 0.8);
		box(p, trim, -0.7, -0.06, HATCH_Z - 0.7, 1.4, 0.06, 1.4);
		box(p, "dark_oak_planks", 1.0, 0.35, -4.0, 0.6, 0.4, 3.2);
		box(p, "dark_oak_planks", -1.6, 0.35, -4.0, 0.6, 0.4, 3.2);
		box(p, "barrel", -1.6, 0.35, 1.6, 0.7, 0.7, 0.7);
		light(p, "lantern", -0.25, 1.55, -4.55, 0.5, 0.5, 0.5);
		return p;
	}
}
