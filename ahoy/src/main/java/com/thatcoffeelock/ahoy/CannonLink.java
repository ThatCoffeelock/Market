package com.thatcoffeelock.ahoy;

/** The only way into {@link CannonDeck}, so the Cannon mod's classes are only touched when it's installed. */
final class CannonLink {
	private CannonLink() {
	}

	static GunDeck deck(Ship ship) {
		return new CannonDeck(ship);
	}
}
