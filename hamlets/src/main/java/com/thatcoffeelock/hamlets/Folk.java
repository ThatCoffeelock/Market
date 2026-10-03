package com.thatcoffeelock.hamlets;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Who lives there. Monsters also mean the place has gone to ruin. */
enum Folk implements StringRepresentable {
	VILLAGERS("villagers"),
	MONSTERS("monsters");

	static final Codec<Folk> CODEC = StringRepresentable.fromEnum(Folk::values);

	final String id;

	Folk(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	static Folk byId(String id) {
		return MONSTERS.id.equals(id) ? MONSTERS : VILLAGERS;
	}
}
