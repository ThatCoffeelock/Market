package com.thatcoffeelock.compagnie;

/**
 * A borrower on the Ledger. On offer first; once the company lends, active until the due day, when they pay back
 * with interest or default. Some borrowers want gold ingots instead of Marks, and pay back in ingots.
 */
final class Loan {
	enum State {
		OFFER, ACTIVE, DEFAULTED, COLLECTING
	}

	enum Risk {
		RELIABLE("Reliable", 0.05), SHAKY("Shaky", 0.2), NOTORIOUS("Notorious", 0.4);

		final String title;
		final double defaultChance;

		Risk(String title, double defaultChance) {
			this.title = title;
			this.defaultChance = defaultChance;
		}
	}

	String id = "";
	String borrower = "";
	String reason = "";
	/** In cents, or gold ingots for a gold loan. */
	long amount;
	boolean gold;
	/** 0.15 = 15% interest over the whole loan. */
	double interest;
	int days;
	String risk = Risk.SHAKY.name();
	/** Offer: the day it's withdrawn. Active: the day it's due. */
	long day;
	String state = State.OFFER.name();

	State state() {
		try {
			return State.valueOf(state);
		} catch (IllegalArgumentException | NullPointerException e) {
			return State.OFFER;
		}
	}

	void state(State s) {
		state = s.name();
	}

	Risk risk() {
		try {
			return Risk.valueOf(risk);
		} catch (IllegalArgumentException | NullPointerException e) {
			return Risk.SHAKY;
		}
	}

	/** What comes back: the amount plus interest (cents or ingots). */
	long owed() {
		return Math.round(amount * (1 + interest));
	}

	String amountText() {
		return gold ? amount + " gold ingots" : Bank.format(amount);
	}

	String owedText() {
		return gold ? owed() + " gold ingots" : Bank.format(owed());
	}
}
