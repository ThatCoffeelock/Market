package com.thatcoffeelock.fossilfool;

import net.minecraft.ChatFormatting;

/** What an Oil Tank holds. A tank holds one kind at a time; NONE while it's empty. */
enum Fluid {
	NONE("Empty", ChatFormatting.GRAY),
	CRUDE("Crude Oil", ChatFormatting.DARK_GRAY),
	DIESEL("Diesel", ChatFormatting.GOLD);

	final String title;
	final ChatFormatting color;

	Fluid(String title, ChatFormatting color) {
		this.title = title;
		this.color = color;
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
