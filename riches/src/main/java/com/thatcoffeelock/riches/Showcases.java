package com.thatcoffeelock.riches;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * What's on show in Display Cases and on Pedestals: the item floats and slowly turns (inside the glass, or on top of
 * the pillar), with a brass plaque above it. The plaque names the item and who put it there, and for a relic, which
 * collection it belongs to. Everything is redrawn from scratch when it changes, so nothing can get out of step.
 */
final class Showcases {
	static final String SHOW_TAG = "riches_show";

	private Showcases() {
	}

	static String tag(Places.Showcase s) {
		return "riches_show_" + Integer.toHexString(s.dimension.hashCode()) + "_" + Long.toHexString(s.pos.asLong());
	}

	/** Strips characters that would break the plaque's text. */
	static String clean(String text) {
		String cleaned = text.replaceAll("[\"\\\\§\\n\\r]", "").trim();
		return cleaned.substring(0, Math.min(40, cleaned.length()));
	}

	/** The item as SNBT for a summon command, components and all. Falls back to just the item id. */
	static String itemSnbt(ServerLevel level, ItemStack stack) {
		ItemStack one = stack.copyWithCount(1);
		return ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), one).result()
			.map(Tag::toString)
			.orElse("{id:\"" + BuiltInRegistries.ITEM.getKey(one.getItem()) + "\",count:1}");
	}

	/** Puts an item on show. */
	static void put(ServerLevel level, Places.Showcase s, ItemStack item, String by) {
		s.item = item.copyWithCount(1);
		s.shownBy = by;
		s.shownOn = LocalDate.now().toString();
		s.drawn = false;
		Store.changed();
		draw(level, s);
	}

	/** Takes the item off show and returns it. */
	static ItemStack take(ServerLevel level, Places.Showcase s) {
		ItemStack out = s.item;
		s.item = ItemStack.EMPTY;
		s.shownBy = "";
		s.shownOn = "";
		Store.changed();
		draw(level, s);
		return out;
	}

	static void clear(ServerLevel level, Places.Showcase s) {
		Cmd.run(level, "kill @e[tag=" + tag(s) + "]");
		s.display = null;
	}

	/** The plaque's lines, top to bottom: {text, colour, bold}. */
	static List<String[]> plaque(Places.Showcase s) {
		List<String[]> lines = new ArrayList<>();
		if (s.item.isEmpty()) {
			return lines;
		}
		Relic relic = RichesItems.relicOf(s.item);
		lines.add(new String[] {clean(s.item.getHoverName().getString()), relic != null ? relic.collection.color.name().toLowerCase(java.util.Locale.ROOT) : "gold", "b"});
		if (relic != null) {
			lines.add(new String[] {clean("Relic · " + relic.collection.title), "gray", ""});
		}
		if (!s.shownBy.isEmpty()) {
			lines.add(new String[] {clean("Shown by " + s.shownBy + " · " + s.shownOn), "dark_gray", ""});
		}
		return lines;
	}

	static void draw(ServerLevel level, Places.Showcase s) {
		clear(level, s);
		s.drawn = true;
		if (s.item.isEmpty()) {
			return;
		}
		String tag = tag(s);
		boolean pedestal = s.kind == Places.Kind.PEDESTAL;
		double x = s.pos.getX() + 0.5;
		double z = s.pos.getZ() + 0.5;
		double itemY = s.pos.getY() + (pedestal ? 1.35 : 0.5);
		double scale = pedestal ? 0.6 : 0.5;
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:item_display " + Cmd.pos(x, itemY, z) + " {" + Cmd.uuidNbt(id) + ",Tags:[\"" + SHOW_TAG + "\",\""
			+ tag + "\"],item_display:\"fixed\",item:" + itemSnbt(level, s.item) + ",transformation:" + spin(s.angle, scale) + "}");
		s.display = level.getEntity(id) == null ? null : id;
		List<String[]> lines = plaque(s);
		double top = s.pos.getY() + (pedestal ? 2.0 : 1.3) + 0.25 * (lines.size() - 1);
		for (int i = 0; i < lines.size(); i++) {
			String[] l = lines.get(i);
			Cmd.run(level, "summon minecraft:text_display " + Cmd.pos(x, top - 0.25 * i, z) + " {Tags:[\"" + SHOW_TAG + "\",\"" + tag
				+ "\"],billboard:\"center\",background:0,shadow:1b,text:{text:\"" + l[0] + "\",color:\"" + l[1] + "\""
				+ (l[2].isEmpty() ? "" : ",bold:true") + "}}");
		}
	}

	private static String spin(float degrees, double scale) {
		double half = Math.toRadians(degrees) / 2;
		return "{left_rotation:[0f," + Cmd.f(Math.sin(half)) + "f,0f," + Cmd.f(Math.cos(half)) + "f],right_rotation:[0f,0f,0f,1f],"
			+ "translation:[0f,0f,0f],scale:[" + Cmd.f(scale) + "f," + Cmd.f(scale) + "f," + Cmd.f(scale) + "f]}";
	}

	/** Draws what isn't drawn yet, and turns every item on show a quarter turn a second (the client smooths it out). */
	static void tick(int ticks) {
		for (Places.Showcase s : Places.SHOWCASES.values()) {
			ServerLevel level = Places.level(s.dimension);
			if (level == null || !level.isLoaded(s.pos)) {
				continue;
			}
			if (!s.drawn) {
				draw(level, s);
				continue;
			}
			if (ticks % 20 == 0 && s.display != null) {
				s.angle = (s.angle + 90) % 360;
				double scale = s.kind == Places.Kind.PEDESTAL ? 0.6 : 0.5;
				Cmd.run(level, "data merge entity " + s.display + " {transformation:" + spin(s.angle, scale)
					+ ",start_interpolation:0,interpolation_duration:20}");
			}
		}
	}
}
