package com.thatcoffeelock.fossilfool;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * What a Tank holds. A tank holds one kind at a time; NONE while it's empty. Crude and diesel come in our own
 * buckets; water and lava in vanilla ones.
 */
enum Fluid {
	NONE("Empty", "Tank", ChatFormatting.GRAY, "gray"),
	CRUDE("Crude Oil", "Oil Tank", ChatFormatting.DARK_GRAY, "dark_gray"),
	DIESEL("Diesel", "Diesel Tank", ChatFormatting.GOLD, "gold"),
	WATER("Water", "Water Tank", ChatFormatting.BLUE, "blue"),
	LAVA("Lava", "Lava Tank", ChatFormatting.RED, "red");

	final String title;
	/** What a tank set to this fluid is called. */
	final String tankName;
	final ChatFormatting color;
	/** The same colour, by name, for text displays. */
	final String colorName;

	Fluid(String title, String tankName, ChatFormatting color, String colorName) {
		this.title = title;
		this.tankName = tankName;
		this.color = color;
		this.colorName = colorName;
	}

	/** The fuel a bucket of this is, or null if it doesn't burn. */
	@Nullable Fuel fuel() {
		return switch (this) {
			case CRUDE -> Fuel.CRUDE;
			case DIESEL -> Fuel.DIESEL;
			case LAVA -> Fuel.LAVA;
			case NONE, WATER -> null;
		};
	}

	/** The next setting when a tank's fluid is cycled: any, crude, diesel, water, lava, any... */
	Fluid next() {
		Fluid[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	static Fluid byName(String name) {
		for (Fluid fluid : values()) {
			if (fluid.name().equalsIgnoreCase(name)) {
				return fluid;
			}
		}
		return NONE;
	}
}
