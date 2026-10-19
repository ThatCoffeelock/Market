package com.thatcoffeelock.overenchant;

/**
 * Lure takes 5 seconds (100 ticks) a level off the wait for a bite, and vanilla rolls the wait from 100 to 600 ticks.
 * When the wait comes out at 0 or less vanilla rolls again, so from Lure VI up (600 ticks off) nothing ever bites.
 * Past that point the wait is held at {@link #MIN_WAIT} instead: the bobber still has to be cast, but the fish come
 * almost at once. Lure V and below are left exactly as vanilla has them.
 */
public final class Lure {
	/** The longest wait vanilla rolls: take this much off and every roll comes out at 0 or less. */
	static final int NEVER_BITES = 600;
	/** The shortest wait for a bite once Lure is that high: one second. */
	static final int MIN_WAIT = 20;

	private Lure() {
	}

	/** How much the hook takes off a freshly rolled wait. */
	public static int speed(int lureSpeed, int rolled) {
		return lureSpeed >= NEVER_BITES ? Math.min(lureSpeed, rolled - MIN_WAIT) : lureSpeed;
	}
}
