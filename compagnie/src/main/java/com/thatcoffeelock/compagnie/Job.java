package com.thatcoffeelock.compagnie;

/**
 * A job: an offer on the board (type and the day it's withdrawn), or one that's under way (who's on it, which way,
 * how much is staked, when it's back), with whatever telegram came in on the road and what was decided about it.
 */
final class Job {
	String id = "";
	String type = "";
	/** Offer: the last day it's on the board. */
	long expires;

	String approach = "";
	String agent = "";
	/** 0 lean, 1 standard, 2 lavish. */
	int stake = 1;
	/** What was paid, in cents. */
	long paid;
	long start;
	long end;
	/** COLLECT: the loan's id. RESCUE: the captured agent's id. */
	String target = "";

	/** Telegram.Kind name, or "" if none came. */
	String telegram = "";
	/** -1 while unanswered. */
	int choice = -1;
	/** What option 0 of the telegram costs, in cents. */
	long telegramCost;
	String ship = "";

	/** Added to the odds, and to the payout (0.3 = 30% more), by telegram answers. */
	double oddsMod;
	double payoutMod;
	/** "Dump the cargo": at best a partial success, but never a disaster. */
	boolean safe;

	JobType type() {
		return JobType.byId(type);
	}

	Approach approach() {
		return Approach.byName(approach);
	}

	boolean pendingTelegram() {
		return !telegram.isEmpty() && choice < 0;
	}
}
