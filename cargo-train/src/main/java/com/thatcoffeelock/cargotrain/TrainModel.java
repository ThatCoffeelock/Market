package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the train looks like: stretched block displays riding an invisible root, one root per car.
 * The locomotive is a little yellow-and-blue diesel shunter with a cab at the back; the wagon is an open
 * goods wagon with crates in it that pile up as it fills.
 *
 * Local coordinates: x across (+x is the left side looking at the nose), y up with 0 on the rails,
 * z along the car with +z towards the locomotive's nose. Each car is 2 blocks long, centred on its root.
 * Blocks have no block state properties on purpose: their NBT format keeps changing between versions.
 */
final class TrainModel {
	record Part(String block, float x, float y, float z, float sx, float sy, float sz, boolean glow) {
	}

	/** Where the driver sits, in the locomotive's cab. A seated player's eyes end up about 1 block above it. */
	static final double SEAT_Y = 0.42;
	static final double SEAT_Z = -0.55;
	static final float LOCO_HITBOX_WIDTH = 1.5f;
	static final float LOCO_HITBOX_HEIGHT = 2.0f;
	static final float WAGON_HITBOX_WIDTH = 1.5f;
	static final float WAGON_HITBOX_HEIGHT = 1.2f;

	static final String LOCO_TAG = "cargotrain_loco";
	static final String WAGON_TAG = "cargotrain_wagon";
	static final String PART_TAG = "cargotrain_part";

	private TrainModel() {
	}

	private static void box(List<Part> p, String block, double x, double y, double z, double sx, double sy, double sz) {
		p.add(new Part("minecraft:" + block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, false));
	}

	private static void light(List<Part> p, String block, double x, double y, double z, double sx, double sy, double sz) {
		p.add(new Part("minecraft:" + block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, true));
	}

	/** Frame, wheels, buffer beams and buffers: the same under both cars. */
	private static void underframe(List<Part> p, double frontWheel, double backWheel) {
		box(p, "polished_blackstone", -0.6, 0.14, -1.0, 1.2, 0.22, 2.0);
		for (double z : new double[] {frontWheel, backWheel}) {
			box(p, "coal_block", 0.5, 0, z, 0.12, 0.4, 0.4);
			box(p, "coal_block", -0.62, 0, z, 0.12, 0.4, 0.4);
		}
		box(p, "red_concrete", -0.6, 0.18, 1.0, 1.2, 0.18, 0.05);
		box(p, "red_concrete", -0.6, 0.18, -1.05, 1.2, 0.18, 0.05);
		for (double x : new double[] {0.3, -0.42}) {
			box(p, "iron_block", x, 0.21, 1.05, 0.12, 0.12, 0.12);
			box(p, "iron_block", x, 0.21, -1.17, 0.12, 0.12, 0.12);
		}
	}

	static List<Part> loco() {
		List<Part> p = new ArrayList<>();
		underframe(p, 0.35, -0.75);
		// the long hood in front, with the engine in it
		box(p, "yellow_concrete", -0.42, 0.36, -0.1, 0.84, 0.78, 1.05);
		box(p, "blue_concrete", -0.43, 0.55, -0.1, 0.86, 0.12, 1.06);
		box(p, "gray_concrete", -0.3, 0.72, 0.955, 0.6, 0.32, 0.02);
		box(p, "polished_blackstone", -0.07, 1.14, 0.55, 0.14, 0.3, 0.14);
		light(p, "sea_lantern", 0.22, 0.38, 0.955, 0.12, 0.12, 0.04);
		light(p, "sea_lantern", -0.34, 0.38, 0.955, 0.12, 0.12, 0.04);
		// the cab at the back: solid below the windows, pillars and glass above, a roof and a beacon on top
		box(p, "yellow_concrete", -0.55, 0.36, -0.98, 1.1, 0.6, 0.88);
		box(p, "blue_concrete", -0.56, 0.55, -0.99, 1.12, 0.12, 0.9);
		for (double x : new double[] {0.47, -0.55}) {
			for (double z : new double[] {-0.98, -0.18}) {
				box(p, "yellow_concrete", x, 0.96, z, 0.08, 0.9, 0.08);
			}
		}
		box(p, "light_blue_stained_glass", 0.53, 1.0, -0.9, 0.02, 0.7, 0.72);
		box(p, "light_blue_stained_glass", -0.55, 1.0, -0.9, 0.02, 0.7, 0.72);
		box(p, "light_blue_stained_glass", -0.47, 1.0, -0.18, 0.94, 0.7, 0.02);
		box(p, "light_blue_stained_glass", -0.47, 1.0, -0.97, 0.94, 0.7, 0.02);
		box(p, "light_gray_concrete", -0.6, 1.86, -1.02, 1.2, 0.1, 0.96);
		light(p, "orange_stained_glass", -0.08, 1.96, -0.6, 0.16, 0.14, 0.16);
		light(p, "redstone_lamp", 0.3, 0.6, -1.0, 0.12, 0.12, 0.03);
		return p;
	}

	static List<Part> wagon() {
		List<Part> p = new ArrayList<>();
		underframe(p, 0.3, -0.7);
		box(p, "spruce_planks", -0.56, 0.36, -0.96, 1.12, 0.08, 1.92);
		box(p, "dark_oak_planks", 0.5, 0.44, -0.96, 0.06, 0.5, 1.92);
		box(p, "dark_oak_planks", -0.56, 0.44, -0.96, 0.06, 0.5, 1.92);
		box(p, "dark_oak_planks", -0.5, 0.44, 0.9, 1.0, 0.5, 0.06);
		box(p, "dark_oak_planks", -0.5, 0.44, -0.96, 1.0, 0.5, 0.06);
		box(p, "iron_block", 0.49, 0.94, -0.97, 0.08, 0.05, 1.94);
		box(p, "iron_block", -0.57, 0.94, -0.97, 0.08, 0.05, 1.94);
		box(p, "blue_concrete", 0.555, 0.6, -0.5, 0.01, 0.16, 1.0);
		box(p, "blue_concrete", -0.565, 0.6, -0.5, 0.01, 0.16, 1.0);
		return p;
	}

	/** The cargo you can see, shown one by one as the wagon fills. Always the last passengers of the wagon. */
	static final List<Part> CRATES = List.of(
		new Part("minecraft:barrel", 0.03f, 0.44f, -0.86f, 0.42f, 0.42f, 0.42f, false),
		new Part("minecraft:hay_block", -0.47f, 0.44f, -0.86f, 0.45f, 0.45f, 0.45f, false),
		new Part("minecraft:oak_planks", -0.46f, 0.44f, 0.05f, 0.5f, 0.5f, 0.5f, false),
		new Part("minecraft:barrel", 0.06f, 0.44f, 0.1f, 0.42f, 0.42f, 0.42f, false));

	static String transformation(Part part, boolean visible) {
		float k = visible ? 1 : 0;
		return "{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[" + Cmd.f(part.x()) + "f," + Cmd.f(part.y()) + "f,"
			+ Cmd.f(part.z()) + "f],scale:[" + Cmd.f(part.sx() * k) + "f," + Cmd.f(part.sy() * k) + "f," + Cmd.f(part.sz() * k) + "f]}";
	}

	private static void display(StringBuilder cmd, Part part, String rot, boolean visible) {
		cmd.append(",{id:\"minecraft:block_display\",block_state:\"").append(part.block()).append("\",Tags:[\"").append(PART_TAG).append("\"],")
			.append(rot).append(",teleport_duration:2,transformation:").append(transformation(part, visible));
		if (part.glow()) {
			cmd.append(",brightness:{sky:15,block:15}");
		}
		cmd.append('}');
	}

	private static StringBuilder root(String tag, UUID id, double x, double y, double z, String rot, float hitboxWidth, float hitboxHeight) {
		return new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(x, y, z)).append(" {").append(Cmd.uuidNbt(id))
			.append(",Tags:[\"").append(tag).append("\"],").append(rot).append(",teleport_duration:2,Passengers:[")
			.append("{id:\"minecraft:interaction\",width:").append(Cmd.f(hitboxWidth)).append("f,height:").append(Cmd.f(hitboxHeight))
			.append("f,response:1b,Tags:[\"").append(PART_TAG).append("\"]}");
	}

	/** The locomotive: an invisible root carrying a click hitbox (always the first passenger) and the model. */
	static String locoCommand(UUID id, double x, double y, double z, float yaw) {
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		StringBuilder cmd = root(LOCO_TAG, id, x, y, z, rot, LOCO_HITBOX_WIDTH, LOCO_HITBOX_HEIGHT);
		for (Part part : loco()) {
			display(cmd, part, rot, true);
		}
		return cmd.append("]}").toString();
	}

	/** The wagon, built the same way, with as many crates showing as the cargo deserves. */
	static String wagonCommand(UUID id, double x, double y, double z, float yaw, int crates) {
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		StringBuilder cmd = root(WAGON_TAG, id, x, y, z, rot, WAGON_HITBOX_WIDTH, WAGON_HITBOX_HEIGHT);
		for (Part part : wagon()) {
			display(cmd, part, rot, true);
		}
		for (int i = 0; i < CRATES.size(); i++) {
			display(cmd, CRATES.get(i), rot, i < crates);
		}
		return cmd.append("]}").toString();
	}
}
