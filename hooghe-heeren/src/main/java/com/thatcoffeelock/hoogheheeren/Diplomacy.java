package com.thatcoffeelock.hoogheheeren;

import java.util.Random;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * What you can ask of a rival, by letter from the desk or in person (at your hall, or at their seat: in person, every
 * proposal is 15% likelier to land, and gifts count for more). Every method returns why not, or null when it went
 * through; what happened is told to the player either way. One proposal per house per day: they need time to think.
 */
final class Diplomacy {
	static final double IN_PERSON = 0.15;

	private Diplomacy() {
	}

	private static Random random() {
		return Courts.RANDOM;
	}

	static long smallGift(Court c) {
		return Bank.cents(200 * (1 + Ai.rank(c)));
	}

	static long bigGift(Court c) {
		return Bank.cents(1000 * (1 + Ai.rank(c)));
	}

	/** Marks into their treasury (the same rules: you may be paying for their next regiment). */
	static @Nullable String gift(Court c, Rival r, boolean big, boolean inPerson, long day) {
		long amount = big ? bigGift(c) : smallGift(c);
		if (!Bank.charge(c.uuid(), amount)) {
			return "You can't afford " + Bank.format(amount) + ".";
		}
		r.treasury += amount;
		double value = big ? 20 : 8;
		if (r.is(Quirk.GREEDY) || r.is(Temperament.MISER)) {
			value *= 1.5;
		}
		if (inPerson) {
			value *= r.is(Quirk.VAIN) ? 2 : 1.5;
		}
		value *= 1 + SkillsLink.bonus(c.uuid(), "statecraft/envoy");
		SkillsLink.xp(c.uuid(), "statecraft", big ? 4 : 2);
		if (day == r.lastGift) {
			value /= 2; // a second purse in a day looks desperate
		} else {
			c.honour(1);
		}
		r.lastGift = day;
		r.remember("A gift of " + Bank.format(amount) + (inPerson ? ", in person" : ""), (int) Math.round(value), day, 25);
		Courts.tell(c, Ai.capital(r.name()) + " accepts your gift of " + Bank.format(amount) + (r.is(Temperament.MISER) ? ", and counts it twice." : "."),
			ChatFormatting.GREEN);
		return null;
	}

	private static @Nullable String busy(Rival r, long day) {
		if (r.lastAsked == day) {
			return Ai.capital(r.name()) + " is still thinking over what you asked today. Try again tomorrow.";
		}
		return null;
	}

	/** Rolls a proposal: true if they say yes. */
	private static boolean roll(double p) {
		return random().nextDouble() < Math.max(0.02, Math.min(0.98, p));
	}

	static double tradeOdds(Court c, Rival r, boolean inPerson, long day) {
		int op = c.opinion(r, day);
		if (op < -10) {
			return 0;
		}
		double p = 0.3 + op / 100.0 + (c.honour - 50) / 150.0;
		p += r.is(Temperament.MERCHANT) ? 0.2 : r.is(Temperament.MISER) ? -0.1 : 0;
		return p + (inPerson ? IN_PERSON : 0) + statecraft(c);
	}

	static double peaceOdds(Court c, Rival r, boolean inPerson, long day) {
		int op = c.opinion(r, day);
		if (op < 0) {
			return 0;
		}
		return 0.2 + op / 80.0 + (c.fear - 30) / 200.0 + (inPerson ? IN_PERSON : 0) + statecraft(c);
	}

	static double allianceOdds(Court c, Rival r, boolean inPerson, long day) {
		int op = c.opinion(r, day);
		if (op < 40 || !r.peacePact) {
			return 0;
		}
		return op / 100.0 + (c.honour - 50) / 100.0 + (inPerson ? IN_PERSON : 0) + statecraft(c);
	}

	/** Will they pay tribute? Fear and the size of your army against theirs. Never if they're Proud. */
	static double tributeOdds(Court c, Rival r, boolean inPerson, long day) {
		if (r.is(Quirk.PROUD)) {
			return 0;
		}
		double ratio = (c.regiments - r.regiments) / (double) (c.regiments + r.regiments + 1);
		double p = (c.fear - 20) / 100.0 + ratio * 0.6 + (r.is(Quirk.COWARDLY) ? 0.25 : 0) - (r.is(Temperament.WARLORD) ? 0.15 : 0);
		return p + (inPerson ? IN_PERSON : 0) + statecraft(c) + SkillsLink.bonus(c.uuid(), "statecraft/iron_hand");
	}

	/** The Statecraft skill's passive: better odds on every proposal. */
	static double statecraft(Court c) {
		return SkillsLink.bonus(c.uuid(), "statecraft/passive");
	}

	static long tributeAmount(Rival r) {
		return Math.max(Bank.cents(20), r.income * 3 / 10);
	}

	static @Nullable String proposeTrade(Court c, Rival r, boolean inPerson, long day) {
		if (r.war) {
			return "You're at war with " + r.name() + ".";
		}
		String busy = busy(r, day);
		if (busy != null) {
			return busy;
		}
		r.lastAsked = day;
		if (roll(tradeOdds(c, r, inPerson, day))) {
			SkillsLink.xp(c.uuid(), "statecraft", inPerson ? 15 : 10);
			r.tradePact = true;
			Courts.tell(c, Ai.capital(r.name()) + " signs a trade pact: " + Bank.format(Ai.pactIncome(c)) + " a day for each of you.", ChatFormatting.GREEN);
		} else {
			Courts.tell(c, Ai.capital(r.name()) + " declines your trade pact. \"Perhaps another time.\"", ChatFormatting.YELLOW);
		}
		return null;
	}

	static @Nullable String proposePeacePact(Court c, Rival r, boolean inPerson, long day) {
		if (r.war) {
			return "You're at war: offer peace first.";
		}
		String busy = busy(r, day);
		if (busy != null) {
			return busy;
		}
		r.lastAsked = day;
		if (roll(peaceOdds(c, r, inPerson, day))) {
			SkillsLink.xp(c.uuid(), "statecraft", inPerson ? 15 : 10);
			r.peacePact = true;
			Courts.tell(c, Ai.capital(r.name()) + " signs a peace pact with you.", ChatFormatting.GREEN);
		} else {
			Courts.tell(c, Ai.capital(r.name()) + " won't tie " + (r.woman ? "her" : "his") + " hands with a peace pact. Not yet.", ChatFormatting.YELLOW);
		}
		return null;
	}

	static @Nullable String proposeAlliance(Court c, Rival r, boolean inPerson, long day) {
		if (!r.peacePact) {
			return "An alliance needs a peace pact first.";
		}
		String busy = busy(r, day);
		if (busy != null) {
			return busy;
		}
		r.lastAsked = day;
		if (roll(allianceOdds(c, r, inPerson, day))) {
			SkillsLink.xp(c.uuid(), "statecraft", inPerson ? 25 : 20);
			r.alliance = true;
			Courts.tell(c, Ai.capital(r.name()) + " is now your ally. Your wars are " + r.their() + " wars, and the other way round.", ChatFormatting.GREEN);
		} else {
			Courts.tell(c, Ai.capital(r.name()) + " doesn't trust you that far. Yet.", ChatFormatting.YELLOW);
		}
		return null;
	}

	static @Nullable String demandTribute(Court c, Rival r, boolean inPerson, long day) {
		if (r.war) {
			return "You're at war: win it first.";
		}
		if (r.tributeUntil >= day && r.tribute > 0) {
			return "Tribute is already flowing between you.";
		}
		String busy = busy(r, day);
		if (busy != null) {
			return busy;
		}
		r.lastAsked = day;
		if (roll(tributeOdds(c, r, inPerson, day))) {
			SkillsLink.xp(c.uuid(), "statecraft", 12);
			r.tribute = tributeAmount(r);
			r.tributeToUs = true;
			r.tributeUntil = day + 10;
			r.remember("Forced to pay you tribute", -25, day, 40);
			c.fear(5);
			Courts.tell(c, Ai.capital(r.name()) + " will pay you " + Bank.format(r.tribute) + " a day for ten days. Through gritted teeth.", ChatFormatting.GREEN);
			return null;
		}
		r.remember("Made threats", -10, day, 20);
		boolean war = r.is(Temperament.WARLORD) && !r.peacePact && !r.alliance && random().nextInt(10) < 4;
		Courts.tell(c, Ai.capital(r.name()) + " laughs at your demand." + (war ? " Then " + r.they() + " reaches for " + r.their() + " sword." : ""),
			ChatFormatting.RED);
		if (war) {
			Wars.declare(c, r, false, day);
		}
		return null;
	}

	static @Nullable String cancelTrade(Court c, Rival r, long day) {
		if (!r.tradePact) {
			return "There's no trade pact to tear up.";
		}
		r.tradePact = false;
		c.honour(-5);
		r.remember("Tore up your trade pact", -20, day, r.is(Temperament.ZEALOT) ? 0 : 40);
		Courts.tell(c, "You tore up the trade pact with " + r.name() + ".", ChatFormatting.YELLOW);
		return null;
	}

	static @Nullable String cancelPeacePact(Court c, Rival r, long day) {
		if (!r.peacePact) {
			return "There's no peace pact to break.";
		}
		if (r.alliance) {
			return "End the alliance first.";
		}
		r.peacePact = false;
		c.honour(-10);
		r.remember("Broke your peace pact", -30, day, r.is(Temperament.ZEALOT) ? 0 : 60);
		Courts.tell(c, "You broke the peace pact with " + r.name() + ". The other houses noticed.", ChatFormatting.YELLOW);
		return null;
	}

	static @Nullable String cancelAlliance(Court c, Rival r, long day) {
		if (!r.alliance) {
			return "You're not allies.";
		}
		r.alliance = false;
		c.honour(-15);
		r.remember("Abandoned your alliance", -35, day, r.is(Temperament.ZEALOT) ? 0 : 60);
		Courts.tell(c, "You ended the alliance with " + r.name() + ".", ChatFormatting.YELLOW);
		return null;
	}

	static @Nullable String declareWar(Court c, Rival r, long day) {
		if (r.war) {
			return "You're already at war with " + r.name() + ".";
		}
		if (r.alliance) {
			return "You can't declare war on your ally. End the alliance first (if you must).";
		}
		if (r.peacePact) {
			c.honour(-20);
			r.remember("Broke your peace pact and attacked you", -40, day, 0);
			for (Rival o : c.rivals) {
				if (o != r) {
					o.remember("Broke a pact with " + r.House(), -15, day, 60);
				}
			}
		} else if (r.provoked >= day) {
			c.chronicle("Nobody blames you: " + r.name() + " had it coming.");
		} else {
			c.honour(-5);
			r.remember("Attacked you without a reason", -20, day, 60);
		}
		r.remember("Declared war on you", -20, day, 60);
		Wars.declare(c, r, true, day);
		return null;
	}

	/** What they'll take to stop a war you're losing: nothing when you're winning. */
	static long peacePrice(Court c, Rival r) {
		return r.warScore >= 0 ? 0 : Math.max(Bank.cents(300), Bank.cents(40L * -r.warScore * (1 + Ai.rank(c))));
	}

	/** A white peace (likelier the better you're doing), or with {@code pay}, a bought one. */
	static @Nullable String offerPeace(Court c, Rival r, boolean pay, long day) {
		if (!r.war) {
			return "You're not at war with " + r.name() + ".";
		}
		if (pay) {
			long price = peacePrice(c, r);
			if (price <= 0) {
				return "You're winning: offer a white peace instead.";
			}
			if (!Bank.charge(c.uuid(), price)) {
				return "You can't afford " + Bank.format(price) + ".";
			}
			r.treasury += price;
			c.fear(-5);
			Courts.tell(c, "You paid " + r.name() + " " + Bank.format(price) + " to end the war.", ChatFormatting.YELLOW);
			Wars.end(c, r, Wars.Outcome.WHITE, day);
			return null;
		}
		if (r.lastPeaceTalk == day) {
			return "Peace has been talked about today already. Try tomorrow.";
		}
		r.lastPeaceTalk = day;
		double p = 0.35 + r.warScore / 100.0 + (r.is(Temperament.MERCHANT) || r.is(Temperament.MISER) || r.is(Quirk.COWARDLY) ? 0.15 : 0)
			- (r.is(Temperament.WARLORD) ? 0.15 : 0);
		if (r.warScore < -20) {
			p = 0;
		}
		if (roll(p)) {
			Wars.end(c, r, Wars.Outcome.WHITE, day);
		} else {
			Courts.tell(c, Ai.capital(r.name()) + " won't hear of peace" + (r.warScore < 0 ? " for nothing. " + Ai.capital(r.they()) + "'d take "
				+ Bank.format(peacePrice(c, r)) + "." : ". Not yet."), ChatFormatting.RED);
		}
		return null;
	}

	/** Raises regiments: {@link Courts#REGIMENT} each, and {@link Courts#UPKEEP} a day to keep. */
	static @Nullable String raise(Court c, int n) {
		long cost = Courts.REGIMENT * n;
		if (!Bank.charge(c.uuid(), cost)) {
			return "You can't afford " + Bank.format(cost) + " for " + n + (n == 1 ? " regiment." : " regiments.");
		}
		c.regiments += n;
		Courts.changed();
		return null;
	}

	static @Nullable String disband(Court c, int n) {
		if (c.regiments <= 0) {
			return "You have no regiments to send home.";
		}
		c.regiments = Math.max(0, c.regiments - n);
		Courts.changed();
		return null;
	}

	/** The odds as the menus show them. */
	static String pct(double p) {
		return Math.round(Math.max(0, Math.min(1, p)) * 100) + "%";
	}
}
