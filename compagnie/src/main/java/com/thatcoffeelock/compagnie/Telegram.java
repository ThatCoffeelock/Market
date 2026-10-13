package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * News from the road, mid-job, with three ways to answer. The first always costs money and buys better odds; the
 * second is the default if nobody answers by the time the job's done; the third is the gamble (or the way out).
 */
enum Telegram {
	STORM(true, false, "A storm off the Cape has battered the %s.",
		"Pay %s for repairs (+10%)", "Press on", "Turn back (60% of the stake back)", 0.10, null, Third.ABORT),
	PIRATES(true, false, "Dunkirk privateers are shadowing the %s.",
		"Pay %s tribute (+12%)", "Fight them off (Force decides)", "Dump the cargo (safe, but at best a partial)", 0.12, Approach.FORCE, Third.SAFE),
	CUSTOMS(false, false, "A customs officer is asking questions about the papers on the %s.",
		"Grease his palm: %s (+10%)", "Bluff it out (Guile decides)", "Drop the job (60% of the stake back)", 0.10, Approach.GUILE, Third.ABORT),
	SECOND_BUYER(false, true, "A second buyer offers more for the cargo of the %s, if it takes a detour.",
		"Bribe the pilot for a fast detour: %s (+8%)", "Stay the course", "Take the detour (+30% payout, -8% odds)", 0.08, null, Third.DETOUR),
	MUTINY(false, false, "The crew of the %s want more pay, or their captain's head.",
		"Pay them a bonus: %s (+6%)", "Flog the ringleader (Force decides)", "Let them have their way (-10%)", 0.06, Approach.FORCE, Third.CONCEDE),
	FEVER(false, false, "Fever has broken out on the %s.",
		"Buy quinine: %s (+8%)", "Push on", "Turn back (60% of the stake back)", 0.08, null, Third.ABORT),
	INFORMANT(false, false, "An informant offers the enemy's plans to the crew of the %s.",
		"Buy them: %s (+15%)", "Ignore him", "Persuade him the hard way (Force decides)", 0.15, null, Third.FORCE_CHECK);

	/** What the third answer does. */
	enum Third {
		ABORT, SAFE, DETOUR, CONCEDE, FORCE_CHECK
	}

	/** Only for sea jobs; only for trade jobs. */
	final boolean sea;
	final boolean trade;
	final String text;
	final String[] answers;
	/** What paying (the first answer) adds to the odds. */
	final double paid;
	/** The second answer tests this stat of the agent (6 or more: +6%, less: -10%), or is neutral if null. */
	final Approach check;
	final Third third;

	Telegram(boolean sea, boolean trade, String text, String pay, String second, String third, double paid, Approach check, Third thirdEffect) {
		this.sea = sea;
		this.trade = trade;
		this.text = text;
		this.answers = new String[] {pay, second, third};
		this.paid = paid;
		this.check = check;
		this.third = thirdEffect;
	}

	/** The telegrams that fit a job. */
	static List<Telegram> fitting(JobType type) {
		List<Telegram> out = new ArrayList<>();
		for (Telegram t : values()) {
			if ((t.sea && !type.sea) || (t.trade && !type.trade())) {
				continue;
			}
			out.add(t);
		}
		return out;
	}

	static Telegram pick(JobType type, Random random) {
		List<Telegram> fits = fitting(type);
		return fits.isEmpty() ? null : fits.get(random.nextInt(fits.size()));
	}

	static Telegram byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	String text(Job job) {
		return String.format(text, job.ship);
	}

	String answer(int i, Job job) {
		return i == 0 ? String.format(answers[0], Bank.format(job.telegramCost)) : answers[i];
	}
}
