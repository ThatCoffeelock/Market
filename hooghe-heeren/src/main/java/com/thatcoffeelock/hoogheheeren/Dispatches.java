package com.thatcoffeelock.hoogheheeren;

import java.util.List;

import net.minecraft.ChatFormatting;

/**
 * Letters from the other houses that want an answer: offers of pacts, demands for tribute, a spy caught in your
 * house, an ally calling you to arms, an enemy suing for peace (or dictating it). Answer at the desk, at your hall,
 * or with /heeren answer. Unanswered by the time they expire, the last answer is given for you.
 */
final class Dispatches {
	private Dispatches() {
	}

	static void send(Court c, Court.Dispatch.Kind kind, Rival r, long amount, String other, long day) {
		for (Court.Dispatch d : c.dispatches) {
			if (d.kind() == kind && d.rival.equals(r.id)) {
				return; // they've asked already
			}
		}
		Court.Dispatch d = new Court.Dispatch();
		d.id = c.newId("d");
		d.kind = kind.name();
		d.rival = r.id;
		d.amount = amount;
		d.other = other;
		d.day = day;
		d.expires = day + 2;
		c.dispatches.add(d);
		Courts.tell(c, "📜 " + text(c, d) + " Answer at your Company Desk, or with /heeren dispatches.", ChatFormatting.GOLD);
	}

	static void forget(Court c, Rival r) {
		c.dispatches.removeIf(d -> d.rival.equals(r.id) || d.other.equals(r.id));
	}

	static String text(Court c, Court.Dispatch d) {
		Rival r = c.rival(d.rival);
		String who = r == null ? "A house that's gone" : Ai.capital(r.name());
		Rival other = c.rival(d.other);
		return switch (d.kind()) {
			case TRADE_OFFER -> who + " proposes a trade pact: " + Bank.format(d.amount) + " a day for each of you.";
			case PEACE_PACT_OFFER -> who + " proposes a peace pact: neither of you will attack the other.";
			case ALLIANCE_OFFER -> who + " proposes an alliance: each of you joins the other's wars.";
			case TRIBUTE_DEMAND -> who + " demands tribute: " + Bank.format(d.amount) + " a day for ten days, \"for your own protection\".";
			case SPY_CAUGHT -> "Your people caught a " + (d.other.isEmpty() ? "spy" : d.other) + " in " + (r == null ? "somebody" : r.House()) + "'s pay.";
			case CALL_TO_ARMS -> who + ", your ally, is at war with " + (other == null ? "another house" : other.name()) + ", and calls you to arms.";
			case PEACE_OFFER -> who + " sues for peace.";
			case TERMS -> who + " offers you peace, on " + (r == null ? "their" : r.their()) + " terms: " + Bank.format(d.amount) + " in reparations.";
		};
	}

	/** The answers, last one first-to-default ("Refuse", "Release", "Fight on"...). */
	static List<String> options(Court c, Court.Dispatch d) {
		Rival r = c.rival(d.rival);
		return switch (d.kind()) {
			case TRADE_OFFER, PEACE_PACT_OFFER, ALLIANCE_OFFER -> List.of("Accept", "Refuse");
			case TRIBUTE_DEMAND -> List.of("Pay " + Bank.format(d.amount) + " a day for ten days", "Refuse");
			case SPY_CAUGHT -> List.of("Hang them (Fear)", "Turn them: read " + (r == null ? "their" : r.their()) + " letters for six days",
				"Send them home (Honour)");
			case CALL_TO_ARMS -> List.of("March to war", "Decline (Honour)");
			case PEACE_OFFER -> List.of("Reparations: " + Bank.format(d.amount),
				r != null && r.warScore >= 80 ? "Their ruin: the house falls" : "Tribute: " + Bank.format(tribute(r)) + " a day for ten days", "Fight on");
			case TERMS -> List.of("Pay " + Bank.format(d.amount) + " and make peace", "Fight on");
		};
	}

	private static long tribute(Rival r) {
		return r == null ? 0 : Math.max(Bank.cents(50), r.income / 3);
	}

	/** Answers a dispatch. Returns why not, or null. {@code timedOut}: nobody answered in time. */
	static String answer(Court c, Court.Dispatch d, int choice, long day, boolean timedOut) {
		Rival r = c.rival(d.rival);
		c.dispatches.remove(d);
		Courts.changed();
		if (r == null) {
			return null;
		}
		String prefix = timedOut ? "Nobody answered " + r.name() + "'s letter in time. " : "";
		switch (d.kind()) {
			case TRADE_OFFER -> {
				if (choice == 0 && !r.war) {
					r.tradePact = true;
					Courts.tell(c, "Trade pact signed with " + r.name() + ": " + Bank.format(d.amount) + " a day for each of you.", ChatFormatting.GREEN);
				} else {
					r.remember("Turned down a trade pact", -5, day, 10);
					Courts.tell(c, prefix + "You turned down " + r.name() + "'s trade pact.", ChatFormatting.GRAY);
				}
			}
			case PEACE_PACT_OFFER -> {
				if (choice == 0 && !r.war) {
					r.peacePact = true;
					Courts.tell(c, "Peace pact signed with " + r.name() + ".", ChatFormatting.GREEN);
				} else {
					r.remember("Turned down a peace pact", -8, day, 15);
					Courts.tell(c, prefix + "You turned down " + r.name() + "'s peace pact. " + Ai.capital(r.they()) + " takes note.", ChatFormatting.GRAY);
				}
			}
			case ALLIANCE_OFFER -> {
				if (choice == 0 && !r.war) {
					r.peacePact = true;
					r.alliance = true;
					Courts.tell(c, "You are now allied with " + r.name() + ". Their wars are yours, and yours are theirs.", ChatFormatting.GREEN);
				} else {
					r.remember("Turned down an alliance", -10, day, 20);
					Courts.tell(c, prefix + "You turned down an alliance with " + r.name() + ".", ChatFormatting.GRAY);
				}
			}
			case TRIBUTE_DEMAND -> {
				if (choice == 0) {
					r.tribute = d.amount;
					r.tributeToUs = false;
					r.tributeUntil = day + 10;
					r.remember("Paid up when asked", 10, day, 30);
					c.fear(-5);
					Courts.tell(c, "You'll pay " + r.name() + " " + Bank.format(d.amount) + " a day for ten days. Peace has a price.", ChatFormatting.YELLOW);
				} else {
					r.remember("Refused to pay tribute", -15, day, 30);
					boolean war = (r.is(Temperament.WARLORD) || c.opinion(r, day) <= -30) && Courts.RANDOM.nextBoolean() && !r.peacePact;
					Courts.tell(c, prefix + "You refused " + r.name() + "'s demand." + (war ? "" : " " + Ai.capital(r.they()) + " won't forget it."),
						ChatFormatting.YELLOW);
					if (war) {
						Wars.declare(c, r, false, day);
					}
				}
			}
			case SPY_CAUGHT -> {
				r.provoked = day + 10;
				switch (choice) {
					case 0 -> {
						c.fear(5);
						r.remember("Hanged one of " + r.their() + " people", -15, day, 30);
						Courts.tell(c, "The " + (d.other.isEmpty() ? "spy" : d.other) + " was hanged at dawn. The other houses got the message.", ChatFormatting.DARK_RED);
					}
					case 1 -> {
						r.intelUntil = day + 6;
						r.remember("Turned one of " + r.their() + " people", -5, day, 20);
						Courts.tell(c, "The " + (d.other.isEmpty() ? "spy" : d.other) + " works for you now: " + r.name()
							+ "'s purse, army and plans are an open book for six days.", ChatFormatting.AQUA);
					}
					default -> {
						c.honour(3);
						r.remember("Sent " + r.their() + " spy home unharmed", 5, day, 20);
						Courts.tell(c, prefix + "The " + (d.other.isEmpty() ? "spy" : d.other) + " was sent home to " + r.name() + " with a note. Very gracious.",
							ChatFormatting.GRAY);
					}
				}
			}
			case CALL_TO_ARMS -> {
				Rival enemy = c.rival(d.other);
				if (choice == 0 && enemy != null && !enemy.war && !enemy.alliance) {
					if (enemy.peacePact) {
						c.honour(-10);
						enemy.remember("Broke your peace pact to help an ally", -30, day, 0);
					}
					r.remember("Answered the call to arms", 15, day, 40);
					Wars.declare(c, enemy, true, day);
				} else {
					c.honour(-10);
					r.remember("Left " + r.their() + " ally to fight alone", -20, day, 40);
					Courts.tell(c, prefix + "You left " + r.name() + " to fight alone. So much for alliances.", ChatFormatting.GRAY);
				}
			}
			case PEACE_OFFER -> {
				if (!r.war) {
					return null;
				}
				if (choice == 0) {
					long take = Math.min(d.amount, r.treasury);
					r.treasury -= take;
					Bank.credit(c.uuid(), c.ownerName, take);
					Courts.tell(c, Ai.capital(r.name()) + " pays " + Bank.format(take) + " in reparations.", ChatFormatting.GREEN);
					Wars.end(c, r, Wars.Outcome.WON, day);
				} else if (choice == 1 && r.warScore >= 80) {
					Wars.end(c, r, Wars.Outcome.WON, day);
					c.fear(25);
					for (Rival o : c.rivals) {
						if (o != r) {
							o.remember("Destroyed the house of " + r.house, o.is(Temperament.WARLORD) ? 5 : -10, day, 60);
						}
					}
					Ai.fall(c, r, day, Courts.RANDOM, false, "You have broken the house of " + r.house + ": its lands are sold, its banners burned.");
				} else if (choice == 1) {
					r.tribute = tribute(r);
					r.tributeToUs = true;
					r.tributeUntil = day + 10;
					Courts.tell(c, Ai.capital(r.name()) + " will pay you " + Bank.format(r.tribute) + " a day for ten days.", ChatFormatting.GREEN);
					Wars.end(c, r, Wars.Outcome.WON, day);
				} else {
					Courts.tell(c, prefix + "You sent " + r.name() + "'s envoy away. The war goes on.", ChatFormatting.RED);
				}
			}
			case TERMS -> {
				if (!r.war) {
					return null;
				}
				if (choice == 0) {
					if (!Bank.charge(c.uuid(), d.amount)) {
						c.dispatches.add(d);
						return "You can't afford " + Bank.format(d.amount) + ".";
					}
					r.treasury += d.amount;
					Wars.end(c, r, Wars.Outcome.LOST, day);
				} else {
					Courts.tell(c, prefix + "You won't pay. The war goes on.", ChatFormatting.RED);
				}
			}
		}
		Courts.changed();
		return null;
	}
}
