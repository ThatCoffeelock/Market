package com.thatcoffeelock.flintlock;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * The three guns and everything that makes them different. Speeds are in blocks per tick, damage in half-hearts,
 * knockback in blocks per tick of velocity added to whatever gets hit.
 */
enum Gun {
	/** Quick to reload, light to carry, fine for a duel. */
	PISTOL("pistol", "Flintlock Pistol", ChatFormatting.YELLOW, Ammo.CARTRIDGE,
		40, 1, 9f, 1.5, 4.0, 0.99, 0.015, 12, 0, 0.3, 0.12, 0.0, 1.6f,
		"Small, loud, and rude."),
	/** Slow to reload, hits like a horse, flies straight. */
	MUSKET("musket", "Musket", ChatFormatting.GOLD, Ammo.CARTRIDGE,
		80, 1, 18f, 0.4, 6.0, 0.99, 0.01, 20, 0, 0.5, 0.15, 0.0, 1.0f,
		"Point the long end at the problem."),
	/** Fires a fistful of junk. Hurts up close, sends things flying, useless past a dozen blocks. */
	BLUNDERBUSS("blunderbuss", "Blunderbuss", ChatFormatting.RED, Ammo.SCATTERSHOT,
		60, 8, 2.5f, 8.0, 2.5, 0.85, 0.02, 8, 12, 0.25, 0.08, 0.7, 0.7f,
		"Aiming is optional. Bracing is not.");

	final String id;
	final String title;
	final ChatFormatting color;
	final Ammo ammo;
	/** How long reloading takes, in ticks. */
	final int reloadTicks;
	/** Balls per shot. */
	final int pellets;
	/** Damage per ball, before falloff. */
	final float damage;
	/** Spread of each ball around where you look, in degrees (one standard deviation). */
	final double spreadDegrees;
	final double speed;
	/** Velocity is multiplied by this every tick. */
	final double drag;
	final double gravity;
	/** Ticks before a ball that hasn't hit anything is dropped. */
	final int maxAge;
	/** Damage and knockback fade to {@link #MIN_FALLOFF} over this many blocks. 0 means no falloff. */
	final double falloffBlocks;
	/** Horizontal knockback per ball that hits. */
	final double knockback;
	/** Upward knockback per ball that hits. */
	final double lift;
	/** How hard the gun kicks the shooter backwards. */
	final double recoil;
	/** Pitch of the bang. Lower is bigger. */
	final float pitch;
	final String blurb;

	static final double MIN_FALLOFF = 0.3;
	/** Knockback from one shot never adds up to more than this, however many pellets hit. */
	static final double MAX_KNOCKBACK = 2.4;
	static final double MAX_LIFT = 0.5;

	Gun(String id, String title, ChatFormatting color, Ammo ammo, int reloadTicks, int pellets, float damage, double spreadDegrees,
		double speed, double drag, double gravity, int maxAge, double falloffBlocks, double knockback, double lift, double recoil,
		float pitch, String blurb) {
		this.id = id;
		this.title = title;
		this.color = color;
		this.ammo = ammo;
		this.reloadTicks = reloadTicks;
		this.pellets = pellets;
		this.damage = damage;
		this.spreadDegrees = spreadDegrees;
		this.speed = speed;
		this.drag = drag;
		this.gravity = gravity;
		this.maxAge = maxAge;
		this.falloffBlocks = falloffBlocks;
		this.knockback = knockback;
		this.lift = lift;
		this.recoil = recoil;
		this.pitch = pitch;
		this.blurb = blurb;
	}

	/** How much of its damage and knockback a ball keeps after flying this far. */
	double falloff(double travelled) {
		if (falloffBlocks <= 0) {
			return 1.0;
		}
		return Math.max(MIN_FALLOFF, 1.0 - travelled / falloffBlocks * (1.0 - MIN_FALLOFF));
	}

	static @Nullable Gun byId(String id) {
		for (Gun gun : values()) {
			if (gun.id.equals(id)) {
				return gun;
			}
		}
		return null;
	}

	/** What each gun is loaded with. */
	enum Ammo {
		CARTRIDGE("cartridge", "Paper Cartridge", ChatFormatting.WHITE, "white_candle",
			"Powder and a lead ball, wrapped in paper.", "For the Flintlock Pistol and the Musket."),
		SCATTERSHOT("scattershot", "Scattershot", ChatFormatting.GRAY, "bundle",
			"A pouch of powder, gravel and bad intentions.", "For the Blunderbuss.");

		final String id;
		final String title;
		final ChatFormatting color;
		/** Vanilla item model it borrows, so vanilla clients can show it. */
		final String model;
		final String blurb;
		final String usedBy;

		Ammo(String id, String title, ChatFormatting color, String model, String blurb, String usedBy) {
			this.id = id;
			this.title = title;
			this.color = color;
			this.model = model;
			this.blurb = blurb;
			this.usedBy = usedBy;
		}

		static @Nullable Ammo byId(String id) {
			for (Ammo ammo : values()) {
				if (ammo.id.equals(id)) {
					return ammo;
				}
			}
			return null;
		}
	}
}
