package com.thatcoffeelock.fossilfool;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The floating text over tanks ("Lava Tank" / "340 / 1,000 buckets of Lava") and refineries. Only redrawn when it changes. */
final class Labels {
	static final String LABEL_TAG = "fossilfool_label";
	private static final Map<String, String> DRAWN = new HashMap<>();

	private Labels() {
	}

	static void reset() {
		DRAWN.clear();
	}

	private static String tag(String dim, BlockPos pos) {
		return "ffl_" + Integer.toHexString(dim.hashCode()) + "_" + Long.toHexString(pos.asLong());
	}

	static void remove(String dim, BlockPos pos) {
		String tag = tag(dim, pos);
		DRAWN.remove(tag);
		ServerLevel level = Machines.level(dim);
		if (level != null) {
			Cmd.run(level, "kill @e[type=minecraft:text_display,tag=" + tag + "]");
		}
	}

	private static void summon(ServerLevel level, String tag, double x, double y, double z, String text) {
		Cmd.run(level, "summon minecraft:text_display " + Cmd.pos(x, y, z) + " {Tags:[\"" + LABEL_TAG + "\",\"" + tag
			+ "\"],billboard:\"center\",background:0,shadow:1b,text:" + text + "}");
	}

	private static String line(String text, String color, boolean bold) {
		return "{text:\"" + text.replace("\"", "") + "\",color:\"" + color + "\"" + (bold ? ",bold:true" : "") + "}";
	}

	private static void draw(String dim, BlockPos pos, String first, String second) {
		ServerLevel level = Machines.level(dim);
		if (level == null || !level.isLoaded(pos)) {
			return;
		}
		String tag = tag(dim, pos);
		String both = first + second;
		if (both.equals(DRAWN.get(tag))) {
			return;
		}
		Cmd.run(level, "kill @e[type=minecraft:text_display,tag=" + tag + "]");
		summon(level, tag, pos.getX() + 0.5, pos.getY() + 1.55, pos.getZ() + 0.5, first);
		summon(level, tag, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, second);
		DRAWN.put(tag, both);
	}

	static void draw() {
		for (Tank t : Machines.TANKS.values()) {
			Fluid shown = t.set != Fluid.NONE ? t.set : t.fluid;
			String second;
			if (t.amount > 0) {
				second = Gui.n(t.amount) + " / " + Gui.n(Tank.capacity()) + " buckets of " + t.fluid.title;
			} else {
				second = "Empty. Takes " + (t.set == Fluid.NONE ? "any fluid" : t.set.title.toLowerCase(java.util.Locale.ROOT) + " only");
			}
			draw(t.dimension, t.pos, line(t.name(), shown.colorName, true), line(second, t.space() == 0 ? "red" : "gray", false));
		}
		for (Oven o : Machines.OVENS.values()) {
			draw(o.dimension, o.pos, line("Industrial Oven: " + o.state.text, o.state == Oven.State.WORKING ? "gold" : "gray", true),
				line("Diesel " + o.diesel + " · " + Gui.n(o.smelted) + " smelted", "gray", false));
		}
		for (Refinery r : Machines.REFINERIES.values()) {
			draw(r.dimension, r.pos, line("Refinery: " + r.state.text, r.state == Refinery.State.WORKING ? "gold" : "gray", true),
				line("Crude " + r.crude + " · Diesel " + r.diesel, "gray", false));
		}
	}
}
