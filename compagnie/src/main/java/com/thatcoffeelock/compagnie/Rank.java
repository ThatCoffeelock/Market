package com.thatcoffeelock.compagnie;

/** How big the company has grown. Reputation comes from jobs well done, profit and loans repaid. */
enum Rank {
	CHARTER("Charter", 0, 2, 3, 5, 2000),
	TRADING_HOUSE("Trading House", 1500, 3, 5, 6, 5000),
	COMPAGNIE("Compagnie", 6000, 4, 7, 7, 10000),
	VOC("Verenigde Oostindische Compagnie", 20000, 6, 10, 8, 25000);

	final String title;
	/** Reputation needed. */
	final long rep;
	/** Jobs running at once, agents on the books, offers on the board. */
	final int slots;
	final int agents;
	final int board;
	/** The biggest loan a borrower will ask for, in Marks. */
	final long maxLoan;

	Rank(String title, long rep, int slots, int agents, int board, long maxLoan) {
		this.title = title;
		this.rep = rep;
		this.slots = slots;
		this.agents = agents;
		this.board = board;
		this.maxLoan = maxLoan;
	}

	static Rank of(long rep) {
		Rank best = CHARTER;
		for (Rank r : values()) {
			if (rep >= r.rep) {
				best = r;
			}
		}
		return best;
	}

	/** The next rank up, or null at the top. */
	Rank next() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}
}
