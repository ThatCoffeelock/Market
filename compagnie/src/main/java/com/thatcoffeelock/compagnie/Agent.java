package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;

/** Someone on the Compagnie's books: three stats (1-10), a trait or two, a wage, and how they're doing. */
final class Agent {
	enum State {
		READY, AWAY, WOUNDED, CAPTURED
	}

	String id = "";
	String name = "";
	int force;
	int coin;
	int guile;
	List<String> traits = new ArrayList<>();
	String state = State.READY.name();
	/** The day a wounded agent is back on their feet. */
	long until;
	int xp;
	int level = 1;
	int jobs;
	/** Per day, and to hire, in cents. */
	long wage;
	long hireCost;

	State state() {
		try {
			return State.valueOf(state);
		} catch (IllegalArgumentException | NullPointerException e) {
			return State.READY;
		}
	}

	void state(State s) {
		state = s.name();
	}

	boolean has(Trait t) {
		return traits.contains(t.name());
	}

	int stat(Approach a) {
		return switch (a) {
			case FORCE -> force;
			case COIN -> coin;
			case GUILE -> guile;
		};
	}

	/** Raises the stat this agent is best at (ties: force, then coin, then guile). */
	void improveBest() {
		if (force >= coin && force >= guile) {
			force = Math.min(10, force + 1);
		} else if (coin >= guile) {
			coin = Math.min(10, coin + 1);
		} else {
			guile = Math.min(10, guile + 1);
		}
	}

	String traitText() {
		List<String> names = new ArrayList<>();
		for (String t : traits) {
			Trait trait = Trait.byName(t);
			if (trait != null) {
				names.add(trait.title);
			}
		}
		return names.isEmpty() ? "no traits" : String.join(", ", names);
	}
}
