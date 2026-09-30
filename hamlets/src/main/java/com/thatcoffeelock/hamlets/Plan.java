package com.thatcoffeelock.hamlets;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** What gets built. The footprint is a square of side 2 * radius + 1 around the origin. */
enum Plan implements StringRepresentable {
	COTTAGE("cottage", 8, 7, 5, -1, 9),
	CASTLE("castle", 17, 16, 8, -1, 14),
	DUNGEON("dungeon", 15, 4, 4, Builders.DUNGEON_FLOOR - 1, 7);

	static final Codec<Plan> CODEC = StringRepresentable.fromEnum(Plan::values);

	final String id;
	final int radius;
	/** How far from the centre the ground is sampled to check it's flat enough. */
	final int flatRadius;
	/** Biggest height difference allowed between the samples. */
	final int maxSlope;
	final int below;
	final int above;

	Plan(String id, int radius, int flatRadius, int maxSlope, int below, int above) {
		this.id = id;
		this.radius = radius;
		this.flatRadius = flatRadius;
		this.maxSlope = maxSlope;
		this.below = below;
		this.above = above;
	}

	BoundingBox box(BlockPos o) {
		return new BoundingBox(o.getX() - radius, o.getY() + below, o.getZ() - radius, o.getX() + radius, o.getY() + above, o.getZ() + radius);
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	static Plan byId(String id) {
		for (Plan p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return COTTAGE;
	}
}
