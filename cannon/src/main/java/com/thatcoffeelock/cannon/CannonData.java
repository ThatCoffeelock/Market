package com.thatcoffeelock.cannon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What a cannon remembers: who owns it and how high the barrel points. Its heading is the root entity's yaw. */
public final class CannonData {
	public static final Codec<CannonData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("owner", "").forGetter(d -> d.owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(d -> d.ownerName),
		Codec.FLOAT.optionalFieldOf("elevation", Cannon.REST_ELEVATION).forGetter(d -> d.elevation)
	).apply(i, CannonData::new));

	public String owner;
	public String ownerName;
	/** Barrel angle in degrees above the horizon. */
	public float elevation;

	public CannonData() {
		this("", "", Cannon.REST_ELEVATION);
	}

	private CannonData(String owner, String ownerName, float elevation) {
		this.owner = owner;
		this.ownerName = ownerName;
		this.elevation = Cannon.clampElevation(elevation);
	}
}
