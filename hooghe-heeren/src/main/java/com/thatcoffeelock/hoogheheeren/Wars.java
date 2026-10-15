package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.Random;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.ChatFormatting;

/**
 * War, kept simple. A war has a score from -100 (they've crushed you) to +100 (you've crushed them). Every dawn the
 * armies skirmish: both sides lose regiments, the day's winner plunders the loser's purse and the score moves. Now
 * and then the armies meet in a pitched battle at noon, which you can ride out to and fight in (see {@link Battles}).
 * Whoever runs out of regiments, or gold to pay them, ends up suing for peace: the same rules for everyone.
 */
final class Wars {
	enum Outcome {
		WON, LOST, WHITE, COLLAPSED
	}

	private Wars() {
	}

	static void declare(Court c, Rival r, boolean byUs, long day) {
		r.war = true;
		r.warScore = 0;
		r.warSince = day;
		r.lastPeaceTalk = day;
		r.tradePact = false;
		r.peacePact = false;
		r.alliance = false;
		r.tribute = 0;
		Courts.tell(c, byUs ? "⚔ You have declared war on " + r.name() + "."
			: "⚔ " + Ai.capital(r.name()) + " has declared WAR on you! Raise regiments, and watch the dispatches.", ChatFormatting.RED);
		// your allies may march with you
		for (Rival ally : c.rivals) {
			if (ally != r && ally.alliance && !ally.wars.contains(r.id)) {
				if (ally.is(Temperament.ZEALOT) || Courts.RANDOM.nextInt(10) < 6) {
					rivalWar(c, ally, r, day);
					ally.remember("Fought at your side", 10, day, 40);
				} else {
					c.chronicle(Ai.capital(ally.name()) + " sends regrets instead of regiments.");
				}
			}
			if (ally != r && byUs && ally.relation(r.id) >= 50) {
				ally.remember("Attacked " + r.House() + ", a friend", -10, day, 30);
			}
		}
	}

	/** One rival declares war on another. Your ally being attacked asks for your help. */
	static void rivalWar(Court c, Rival a, Rival b, long day) {
		if (a.wars.contains(b.id)) {
			return;
		}
		a.wars.add(b.id);
		b.wars.add(a.id);
		a.partners.remove(b.id);
		b.partners.remove(a.id);
		a.relation(b.id, Math.min(a.relation(b.id), -50));
		b.relation(a.id, Math.min(b.relation(a.id), -50));
		c.chronicle("⚔ " + a.House() + " has declared war on " + b.House() + ".");
		if (b.alliance && !a.war) {
			Dispatches.send(c, Court.Dispatch.Kind.CALL_TO_ARMS, b, 0, a.id, day);
		}
	}

	// ---------------------------------------------------------------- every dawn

	static void dawn(Court c, long day, Random random) {
		for (Rival r : new ArrayList<>(c.rivals)) {
			if (!r.war || !c.rivals.contains(r)) {
				continue;
			}
			Court.Battle b = c.battle;
			boolean battleToday = b != null && b.rival.equals(r.id) && b.day == day;
			if (!battleToday) {
				skirmish(c, r, day, random, 1.0, false);
				if (b == null && c.regiments > 0 && r.regiments > 0 && random.nextInt(10) < 3) {
					Battles.schedule(c, r, day, false);
				}
			}
			siege(c, r, day, random);
			peaceTalks(c, r, day);
		}
		rivalWars(c, day, random);
	}

	/**
	 * A day's fighting. Each side fields its regiments (give or take a quarter for luck), both lose some, the score
	 * moves by up to 20 (times {@code swing}), and the day's winner plunders the loser.
	 */
	static void skirmish(Court c, Rival r, long day, Random random, double swing, boolean pitched) {
		double ours = c.regiments * (0.75 + random.nextDouble() * 0.5);
		double theirs = r.regiments * (0.75 + random.nextDouble() * 0.5);
		if (ours + theirs < 0.01) {
			return; // nobody to fight: the war is waged with raids and gold
		}
		int ourLoss = (int) Math.min(c.regiments, Math.round(theirs * 0.12 * swing * (0.5 + random.nextDouble())));
		int theirLoss = (int) Math.min(r.regiments, Math.round(ours * 0.12 * swing * (0.5 + random.nextDouble())));
		c.regiments -= ourLoss;
		r.regiments -= theirLoss;
		int delta = (int) Math.round(20 * swing * (ours - theirs) / (ours + theirs));
		r.warScore = Math.max(-100, Math.min(100, r.warScore + delta));
		String what = pitched ? "The captains fought the battle without you" : "Skirmish with " + r.House();
		if (delta >= 0) {
			long take = Math.min(r.treasury * 8 / 100, Bank.cents(2000 * (1 + Ai.rank(c))) * (pitched ? 2 : 1));
			r.treasury -= take;
			Bank.credit(c.uuid(), c.ownerName, take);
			c.chronicle(what + ": you won the day (they lost " + theirLoss + ", you lost " + ourLoss + ")" + (take > 0 ? " and took "
				+ Bank.format(take) + " from their baggage." : ".") + " War score " + signed(r.warScore) + ".");
		} else {
			long take = Bank.seize(c.uuid(), Math.min(Math.max(0, Bank.balance(c.uuid())) * 3 / 100, Bank.cents(1000 + 500 * Ai.rank(c))) * (pitched ? 2 : 1));
			r.treasury += take;
			c.chronicle(what + ": they won the day (you lost " + ourLoss + ", they lost " + theirLoss + ")" + (take > 0 ? " and carried off "
				+ Bank.format(take) + "." : ".") + " War score " + signed(r.warScore) + ".");
		}
		Courts.changed();
	}

	/**
	 * At war, an army with regiments to spare may lay siege to one of your colonies (Colonycraft's network): no work,
	 * no convoys, until you beat them in the field or make peace. They'll offer battle at its walls.
	 */
	static void siege(Court c, Rival r, long day, Random random) {
		if (r.regiments < 2 || random.nextInt(10) >= 2 || !ColonyLink.besieged(c, r).isEmpty()) {
			return;
		}
		String where = ColonyLink.besiege(c, r, 6);
		if (where == null) {
			return;
		}
		Courts.tell(c, "⚔ " + Ai.capital(r.House()) + "'s army has laid siege to " + where + "! Nothing gets in or out. Beat them in the field to lift it.",
			ChatFormatting.RED);
		if (c.battle == null && c.regiments > 0) {
			Battles.schedule(c, r, day, false);
		}
	}

	/** Whoever's losing badly sues for peace; whoever's crushed has peace forced on them. */
	static void peaceTalks(Court c, Rival r, long day) {
		boolean theyreBeaten = r.warScore >= (r.is(Quirk.COWARDLY) ? 30 : 60) || (r.regiments == 0 && r.treasury < Courts.REGIMENT);
		boolean wereBeaten = r.warScore <= -60 || (c.regiments == 0 && r.regiments > 2 && Bank.balance(c.uuid()) < Courts.REGIMENT);
		if (r.warScore >= 100) {
			long take = r.treasury / 2;
			r.treasury -= take;
			Bank.credit(c.uuid(), c.ownerName, take);
			Courts.tell(c, Ai.capital(r.name()) + " surrenders, beaten to the last man. " + Bank.format(take) + " in reparations.", ChatFormatting.GREEN);
			end(c, r, Outcome.WON, day);
			return;
		}
		if (r.warScore <= -100) {
			long take = Bank.seize(c.uuid(), Math.max(0, Bank.balance(c.uuid())) * 15 / 100);
			r.treasury += take;
			Courts.tell(c, "You're beaten. " + Ai.capital(r.name()) + " dictates the peace, and helps " + (r.woman ? "herself" : "himself") + " to " + Bank.format(take) + ".",
				ChatFormatting.RED);
			end(c, r, Outcome.LOST, day);
			return;
		}
		if (day - r.lastPeaceTalk < 3) {
			return;
		}
		if (theyreBeaten) {
			r.lastPeaceTalk = day;
			Dispatches.send(c, Court.Dispatch.Kind.PEACE_OFFER, r, Math.max(Bank.cents(500), r.treasury / 2), "", day);
		} else if (wereBeaten) {
			r.lastPeaceTalk = day;
			Dispatches.send(c, Court.Dispatch.Kind.TERMS, r, Math.max(Bank.cents(500), Math.max(0, Bank.balance(c.uuid())) / 5), "", day);
		}
	}

	static void end(Court c, Rival r, Outcome outcome, long day) {
		r.war = false;
		r.warScore = 0;
		r.lastPeaceTalk = day;
		if (c.battle != null && c.battle.rival.equals(r.id)) {
			Battles.cancel(c);
		}
		ColonyLink.lift(c, r);
		switch (outcome) {
			case WON -> {
				c.warsWon++;
				c.fear(15);
				SkillsLink.xp(c.uuid(), "statecraft", 40);
				r.remember("Lost a war to you", -25, day, 60);
				CompagnieHooks.reputation(c.uuid(), 500);
				Courts.tell(c, "☮ Peace with " + r.name() + ". You won the war.", ChatFormatting.GREEN);
			}
			case LOST -> {
				c.warsLost++;
				c.fear(-10);
				r.remember("Beat you in a war", 10, day, 30);
				Courts.tell(c, "☮ Peace with " + r.name() + ". You lost the war.", ChatFormatting.RED);
			}
			case WHITE -> {
				r.remember("Made peace with you", 5, day, 20);
				Courts.tell(c, "☮ Peace with " + r.name() + ". Nobody won, everybody paid.", ChatFormatting.YELLOW);
			}
			case COLLAPSED -> {
				c.warsWon++;
				c.fear(10);
				Courts.tell(c, "☮ The war with the house of " + r.house + " is over: there's nobody left to fight.", ChatFormatting.GREEN);
			}
		}
		Courts.changed();
	}

	/** Wars between the other houses: abstract, bloody, and good news for whoever's watching. */
	private static void rivalWars(Court c, long day, Random random) {
		for (Rival a : new ArrayList<>(c.rivals)) {
			for (String id : new ArrayList<>(a.wars)) {
				Rival b = c.rival(id);
				if (b == null) {
					a.wars.remove(id);
					continue;
				}
				if (a.id.compareTo(b.id) > 0) {
					continue; // once per pair
				}
				double sa = a.regiments * (0.75 + random.nextDouble() * 0.5);
				double sb = b.regiments * (0.75 + random.nextDouble() * 0.5);
				a.regiments -= (int) Math.min(a.regiments, Math.round(sb * 0.12 * (0.5 + random.nextDouble())));
				b.regiments -= (int) Math.min(b.regiments, Math.round(sa * 0.12 * (0.5 + random.nextDouble())));
				Rival winner = null;
				Rival loser = null;
				if (a.regiments == 0 && b.regiments > 0) {
					winner = b;
					loser = a;
				} else if (b.regiments == 0 && a.regiments > 0) {
					winner = a;
					loser = b;
				}
				if (winner != null) {
					long take = loser.treasury * 30 / 100;
					loser.treasury -= take;
					winner.treasury += take;
					a.wars.remove(b.id);
					b.wars.remove(a.id);
					c.chronicle("☮ " + winner.House() + " has beaten " + loser.House() + " and taken " + Bank.format(take) + " in reparations.");
				} else if ((a.regiments + b.regiments == 0) || random.nextInt(100) < 6) {
					a.wars.remove(b.id);
					b.wars.remove(a.id);
					c.chronicle("☮ " + a.House() + " and " + b.House() + " have made peace. Neither can afford the war.");
				}
			}
		}
	}

	static String signed(int v) {
		return (v > 0 ? "+" : "") + v;
	}
}
