package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The rivals' minds, such as they are. Every dawn a rival collects their income and pays their regiments (the same
 * rules as yours: no treasury, no army), carries out the one thing they planned yesterday, then plans the next. What
 * they plan comes from a weighted table: their temperament, their purse, their army against yours, and what they
 * think of you and of each other. A spy can read their plans; some leak out as rumours anyway.
 */
final class Ai {
	enum Intent {
		REST, INVEST, RECRUIT, SQUANDER, OFFER_TRADE, OFFER_PEACE_PACT, OFFER_ALLIANCE, GIFT, DEMAND_TRIBUTE, SPY, SABOTAGE, RAID, DECLARE_WAR,
		SLANDER, RIVAL_TRADE, RIVAL_WAR;

		static Intent byName(String name) {
			try {
				return valueOf(name);
			} catch (IllegalArgumentException | NullPointerException e) {
				return REST;
			}
		}
	}

	private Ai() {
	}

	static int rank(Court c) {
		return Math.max(0, CompagnieHooks.rank(c.uuid()));
	}

	// ---------------------------------------------------------------- money

	/** Income in, regiments paid. Can't pay them all? The unpaid ones desert. Returns what a trade pact paid you. */
	static long economy(Court c, Rival r, long day, Random random) {
		double factor = (r.sabotagedUntil >= day ? 0.6 : 1.0) * (1 + 0.1 * r.partners.size());
		r.treasury += Math.round(r.income * factor);
		long pact = 0;
		if (r.tradePact) {
			pact = pactIncome(c);
			r.treasury += pact;
			Bank.credit(c.uuid(), c.ownerName, pact);
		}
		// tribute
		if (r.tribute > 0 && r.tributeUntil >= day) {
			if (r.tributeToUs) {
				long pay = Math.min(r.tribute, Math.max(0, r.treasury));
				r.treasury -= pay;
				Bank.credit(c.uuid(), c.ownerName, pay);
				if (pay < r.tribute) {
					c.chronicle(r.name() + " couldn't pay the full tribute today (" + Bank.format(pay) + " of " + Bank.format(r.tribute) + ").");
				}
			} else if (Bank.charge(c.uuid(), r.tribute)) {
				r.treasury += r.tribute;
			} else {
				r.tribute = 0;
				c.honour(-5);
				r.remember("Missed a tribute payment", -20, day, 40);
				c.chronicle("You couldn't pay " + r.name() + " the tribute you owe. " + capital(r.they()) + " won't forget it.");
			}
		} else if (r.tribute > 0) {
			r.tribute = 0;
		}
		// upkeep: the same rule as yours
		long upkeep = r.regiments * Courts.UPKEEP;
		if (r.treasury >= upkeep) {
			r.treasury -= upkeep;
		} else {
			int paid = (int) (Math.max(0, r.treasury) / Courts.UPKEEP);
			int gone = r.regiments - paid;
			r.regiments = paid;
			r.treasury -= paid * Courts.UPKEEP;
			if (gone > 0) {
				c.chronicle(r.name() + " couldn't pay " + r.their() + " soldiers: " + gone + (gone == 1 ? " regiment" : " regiments") + " deserted.");
			}
		}
		r.treasury = Math.max(0, r.treasury);
		r.broke = r.treasury < Courts.UPKEEP && r.regiments == 0 && r.income < Bank.cents(40) ? r.broke + 1 : 0;
		// grudges between houses cool, friendships too
		for (String other : new ArrayList<>(r.relations.keySet())) {
			int v = r.relation(other);
			if (v != 0 && random.nextInt(2) == 0 && !r.wars.contains(other)) {
				r.relation(other, v - Integer.signum(v));
			}
		}
		return pact;
	}

	/** What a trade pact brings each side a day. */
	static long pactIncome(Court c) {
		return Bank.cents(40 + 30 * rank(c));
	}

	// ---------------------------------------------------------------- planning

	private record Choice(Intent intent, String target, int weight) {
	}

	/** Decides what this rival will do at the next dawn. */
	static void plan(Court c, Rival r, long day, Random random) {
		int op = c.opinion(r, day);
		Temperament t = r.temperament();
		int ours = c.regiments;
		int theirs = r.regiments;
		boolean warlord = t == Temperament.WARLORD;
		boolean merchant = t == Temperament.MERCHANT;
		boolean schemer = t == Temperament.SCHEMER;
		boolean fighting = r.war || !r.wars.isEmpty();
		List<Choice> choices = new ArrayList<>();
		add(choices, Intent.REST, "", 10);
		if (r.treasury > Courts.REGIMENT * 4) {
			add(choices, Intent.INVEST, "", merchant ? 30 : t == Temperament.MISER ? 25 : 10);
		}
		if (r.treasury > Courts.REGIMENT * 2 && r.regiments < 40) {
			int w = warlord ? 30 : t == Temperament.SPENDTHRIFT ? 15 : t == Temperament.MISER ? 3 : 8;
			w += fighting ? 30 : 0;
			w += ours > theirs ? 10 : 0;
			add(choices, Intent.RECRUIT, "", w);
		}
		if (t == Temperament.SPENDTHRIFT && r.treasury > Bank.cents(2000)) {
			add(choices, Intent.SQUANDER, "", 15);
		}
		if (!r.war) {
			if (!r.tradePact && op >= 10) {
				add(choices, Intent.OFFER_TRADE, "", merchant ? 30 : 10);
			}
			if (!r.peacePact && op >= 30) {
				add(choices, Intent.OFFER_PEACE_PACT, "", merchant || t == Temperament.ZEALOT ? 15 : 7);
			}
			if (r.peacePact && !r.alliance && op >= 60) {
				add(choices, Intent.OFFER_ALLIANCE, "", 10);
			}
			if (op >= 40 && r.treasury > Bank.cents(3000)) {
				add(choices, Intent.GIFT, "", t == Temperament.SPENDTHRIFT ? 20 : t == Temperament.MISER ? 1 : 5);
			}
			if (op < 0 && theirs > ours * 1.3 + 1 && c.fear < 50 && r.tributeUntil < day && !r.peacePact) {
				add(choices, Intent.DEMAND_TRIBUTE, "", warlord ? 25 : 7);
			}
			boolean breaksPact = r.peacePact && (t == Temperament.ZEALOT || op > -75);
			if (!breaksPact && !r.alliance) {
				if (op <= -50 && theirs >= ours) {
					add(choices, Intent.DECLARE_WAR, "", warlord ? 30 : 12);
				} else if (warlord && op <= -30 && theirs >= ours * 0.8) {
					add(choices, Intent.DECLARE_WAR, "", 12);
				}
			}
		}
		if (op < 10 && !r.alliance) {
			add(choices, Intent.SPY, "", schemer ? 25 : 5);
		}
		if (op < -10 && !r.alliance && !r.peacePact) {
			add(choices, Intent.SABOTAGE, "", schemer ? 20 : 5);
		}
		if ((op < -30 && !r.peacePact) || r.war) {
			add(choices, Intent.RAID, "", (warlord ? 25 : 8) + (r.war ? 25 : 0));
		}
		if (schemer && c.rivals.size() > 2 && op < 20) {
			Rival ear = random(c, r, random);
			if (ear != null) {
				add(choices, Intent.SLANDER, ear.id, 15);
			}
		}
		// the other houses
		Rival friend = null;
		Rival enemy = null;
		for (Rival o : c.rivals) {
			if (o == r) {
				continue;
			}
			int rel = r.relation(o.id);
			if (rel >= 25 && !r.partners.contains(o.id) && (friend == null || rel > r.relation(friend.id))) {
				friend = o;
			}
			if (rel <= -50 && !r.wars.contains(o.id) && (enemy == null || rel < r.relation(enemy.id))) {
				enemy = o;
			}
		}
		if (friend != null) {
			add(choices, Intent.RIVAL_TRADE, friend.id, merchant ? 15 : 5);
		}
		if (enemy != null && r.regiments >= enemy.regiments) {
			add(choices, Intent.RIVAL_WAR, enemy.id, warlord ? 15 : 4);
		}

		int total = 0;
		for (Choice ch : choices) {
			total += ch.weight();
		}
		int roll = random.nextInt(Math.max(1, total));
		Choice pick = choices.get(0);
		for (Choice ch : choices) {
			roll -= ch.weight();
			if (roll < 0) {
				pick = ch;
				break;
			}
		}
		r.intent = pick.intent().name();
		r.intentTarget = pick.target();
		// the walls have ears
		if (random.nextInt(10) < 4) {
			String rumour = switch (pick.intent()) {
				case DECLARE_WAR -> "Rumour has it " + r.name() + " is buying muskets and counting your regiments.";
				case RAID -> "Rumour has it " + r.House() + "'s riders have been seen near your trade roads.";
				case DEMAND_TRIBUTE -> "Rumour has it " + r.name() + " thinks you ought to be paying " + r.their() + " protection.";
				case SABOTAGE -> "Rumour has it somebody's been asking questions about your warehouses.";
				case RIVAL_WAR -> {
					Rival e = c.rival(pick.target());
					yield e == null ? null : "Rumour has it " + r.House() + " and " + e.House() + " are about to come to blows.";
				}
				default -> null;
			};
			if (rumour != null) {
				c.chronicle(rumour);
			}
		}
	}

	private static void add(List<Choice> choices, Intent intent, String target, int weight) {
		if (weight > 0) {
			choices.add(new Choice(intent, target, weight));
		}
	}

	private static @Nullable Rival random(Court c, Rival not, Random random) {
		List<Rival> others = new ArrayList<>(c.rivals);
		others.remove(not);
		return others.isEmpty() ? null : others.get(random.nextInt(others.size()));
	}

	/** What a plan looks like to someone who's read their letters. */
	static String intentText(Court c, Rival r) {
		Rival target = c.rival(r.intentTarget);
		String other = target == null ? "another house" : target.name();
		return switch (Intent.byName(r.intent)) {
			case REST -> "Nothing in particular. Counting money, probably.";
			case INVEST -> "Buying shares and warehouses: more income later.";
			case RECRUIT -> "Raising regiments.";
			case SQUANDER -> "Throwing a feast for half of Amsterdam.";
			case OFFER_TRADE -> "Sending you an offer of a trade pact.";
			case OFFER_PEACE_PACT -> "Sending you an offer of a peace pact.";
			case OFFER_ALLIANCE -> "Sending you an offer of an alliance.";
			case GIFT -> "Sending you a gift.";
			case DEMAND_TRIBUTE -> "Demanding you pay " + r.their() + " tribute.";
			case SPY -> "Sending a spy into your house.";
			case SABOTAGE -> "Sabotaging your trade.";
			case RAID -> "Raiding your convoys.";
			case DECLARE_WAR -> "Declaring WAR on you.";
			case SLANDER -> "Whispering lies about you to " + other + ".";
			case RIVAL_TRADE -> "A trade pact with " + other + ".";
			case RIVAL_WAR -> "War on " + other + ".";
		};
	}

	// ---------------------------------------------------------------- acting

	/** Your best spy-catcher: the sharpest Guile among the agents who are home. 0 if nobody's in. */
	static double vigilance(Court c) {
		int best = 0;
		for (CompagnieHooks.AgentInfo a : CompagnieHooks.agents(c.uuid())) {
			if (a.state().equals("READY")) {
				best = Math.max(best, a.guile());
			}
		}
		return best <= 0 ? 0 : 0.05 + best * 0.035;
	}

	/** Carries out what the rival planned yesterday, if it still makes sense. */
	static void act(Court c, Rival r, long day, Random random) {
		Intent intent = Intent.byName(r.intent);
		int op = c.opinion(r, day);
		int rank = rank(c);
		switch (intent) {
			case REST -> {
			}
			case INVEST -> {
				long spend = r.treasury / 4;
				r.treasury -= spend;
				r.income += spend / 40;
			}
			case RECRUIT -> {
				int n = (int) Math.min(4, (r.treasury / 2) / Courts.REGIMENT);
				if (n > 0) {
					r.treasury -= n * Courts.REGIMENT;
					r.regiments += n;
					if (r.war) {
						c.chronicle(r.name() + " raised " + n + " more " + (n == 1 ? "regiment" : "regiments") + " for the war.");
					}
				}
			}
			case SQUANDER -> {
				r.treasury -= r.treasury * 15 / 100;
				for (Rival o : c.rivals) {
					if (o != r) {
						o.relation(r.id, o.relation(r.id) + 3);
					}
				}
				c.chronicle(r.name() + " threw a feast for half of Amsterdam. The other houses are charmed; " + r.their() + " banker isn't.");
			}
			case OFFER_TRADE -> {
				if (!r.war && !r.tradePact) {
					Dispatches.send(c, Court.Dispatch.Kind.TRADE_OFFER, r, pactIncome(c), "", day);
				}
			}
			case OFFER_PEACE_PACT -> {
				if (!r.war && !r.peacePact) {
					Dispatches.send(c, Court.Dispatch.Kind.PEACE_PACT_OFFER, r, 0, "", day);
				}
			}
			case OFFER_ALLIANCE -> {
				if (!r.war && r.peacePact && !r.alliance) {
					Dispatches.send(c, Court.Dispatch.Kind.ALLIANCE_OFFER, r, 0, "", day);
				}
			}
			case GIFT -> {
				if (!r.war) {
					long gift = Math.min(r.treasury / 20, Bank.cents(300 * (1 + rank)));
					if (gift > 0) {
						r.treasury -= gift;
						Bank.credit(c.uuid(), c.ownerName, gift);
						c.chronicle(r.name() + " sent you a gift of " + Bank.format(gift) + ", with " + r.their() + " compliments.");
					}
				}
			}
			case DEMAND_TRIBUTE -> {
				if (!r.war && r.tributeUntil < day) {
					long bal = Math.max(0, Bank.balance(c.uuid()));
					long amount = Math.max(Bank.cents(50), Math.min(bal / 50, Bank.cents(1000)));
					Dispatches.send(c, Court.Dispatch.Kind.TRIBUTE_DEMAND, r, amount, "", day);
				}
			}
			case SPY -> {
				if (random.nextDouble() < 0.55 - vigilance(c)) {
					// they read your letters: their next raid knows where your convoys are
					r.spied = day + 3;
				} else {
					Dispatches.send(c, Court.Dispatch.Kind.SPY_CAUGHT, r, 0, "spy", day);
				}
			}
			case SABOTAGE -> {
				if (random.nextDouble() < 0.5 - vigilance(c)) {
					HeerenMod.sabotaged(c, day + 3);
					c.chronicle("Somebody has been sabotaging your trade: your Compagnie's trade jobs are 10% harder for three days."
						+ (random.nextBoolean() ? " The trail leads to " + r.name() + "." : ""));
					r.provoked = day + 10;
				} else {
					Dispatches.send(c, Court.Dispatch.Kind.SPY_CAUGHT, r, 0, "saboteur", day);
				}
			}
			case RAID -> {
				if (r.peacePact && !r.war) {
					break;
				}
				double held = (c.regiments + 0.5) / (c.regiments + r.regiments * 0.7 + 1.0) * (r.spied >= day ? 0.6 : 1.0);
				if (random.nextDouble() < held) {
					c.chronicle("Your regiments beat off a raid by " + r.House() + "'s riders on your convoys.");
					if (r.regiments > 0 && random.nextBoolean()) {
						r.regiments--;
					}
				} else {
					long take = Bank.seize(c.uuid(), Math.min(Math.max(0, Bank.balance(c.uuid())) * 3 / 100, Bank.cents(800 + 400 * rank)));
					r.treasury += take;
					c.chronicle(r.House() + "'s riders raided your convoys and carried off " + Bank.format(take) + ".");
					r.treasury += ColonyLink.raidConvoy(c, r);
				}
				if (!r.war) {
					r.provoked = day + 10;
				}
			}
			case DECLARE_WAR -> {
				if (!r.war && !r.alliance && op <= -25) {
					if (r.peacePact) {
						for (Rival o : c.rivals) {
							if (o != r) {
								o.relation(r.id, o.relation(r.id) - 20);
							}
						}
						c.chronicle(r.name() + " tore up the peace pact. The other houses are appalled.");
					}
					Wars.declare(c, r, false, day);
				}
			}
			case SLANDER -> {
				Rival ear = c.rival(r.intentTarget);
				if (ear != null) {
					ear.remember("Heard ugly stories about you", -10, day, 20);
					if (random.nextInt(3) == 0) {
						c.chronicle("Ugly stories about you are going round. " + ear.name() + " seems to believe them.");
					}
				}
			}
			case RIVAL_TRADE -> {
				Rival o = c.rival(r.intentTarget);
				if (o != null && !r.partners.contains(o.id) && !r.wars.contains(o.id) && o.relation(r.id) >= 0) {
					r.partners.add(o.id);
					o.partners.add(r.id);
					r.relation(o.id, r.relation(o.id) + 10);
					o.relation(r.id, o.relation(r.id) + 10);
					c.chronicle(r.House() + " and " + o.House() + " signed a trade pact. Both are getting richer.");
				}
			}
			case RIVAL_WAR -> {
				Rival o = c.rival(r.intentTarget);
				if (o != null && !r.wars.contains(o.id)) {
					Wars.rivalWar(c, r, o, day);
				}
			}
		}
		r.intent = Intent.REST.name();
	}

	// ---------------------------------------------------------------- rise and fall

	/** Old lords die, broke houses fall, and now and then an upstart buys out the weakest seat at the table. */
	static void succession(Court c, long day, Random random) {
		for (Rival r : new ArrayList<>(c.rivals)) {
			double death = r.is(Quirk.OLD) ? 0.02 : 0.002;
			if (random.nextDouble() < death) {
				fall(c, r, day, random, true, capital(r.name()) + " has died in " + r.their() + " bed.");
			} else if (r.broke >= 3) {
				fall(c, r, day, random, false, "The house of " + r.house + " is bankrupt. The creditors sold the seat.");
			}
		}
		if (random.nextInt(100) == 0 && !c.rivals.isEmpty()) {
			Rival weakest = null;
			for (Rival r : c.rivals) {
				long worth = r.treasury + r.regiments * Courts.REGIMENT + r.income * 20;
				if (!r.war && c.guest.isEmpty() && (weakest == null || worth < weakest.treasury + weakest.regiments * Courts.REGIMENT + weakest.income * 20)) {
					weakest = r;
				}
			}
			if (weakest != null) {
				fall(c, weakest, day, random, false, "A richer family bought " + weakest.seatName + " out from under " + weakest.name() + ".");
			}
		}
	}

	/**
	 * A house falls. With {@code heir}, the next of kin takes the seat: same house, a new face, maybe a new temperament,
	 * and half of every grudge (the war too, if there is one). Without, a newcomer moves into the seat with a clean
	 * slate.
	 */
	static void fall(Court c, Rival r, long day, Random random, boolean heir, String why) {
		c.housesFallen++;
		Hall.forget(c, r);
		if (heir) {
			String old = r.first;
			Names.lord(r, random, true);
			r.generation++;
			if (random.nextBoolean()) {
				r.temperament = Temperament.values()[random.nextInt(Temperament.values().length)].name();
			}
			r.quirk = Quirk.values()[random.nextInt(Quirk.values().length)].name();
			if (r.is(Quirk.OLD)) {
				r.quirk = Quirk.PROUD.name();
			}
			for (Rival.Memory m : r.memories) {
				m.value /= 2;
			}
			r.met = false;
			r.intelUntil = -1;
			r.intent = Intent.REST.name();
			Courts.tell(c, why + " " + capital(r.name()) + " takes " + old + "'s seat, and half "
				+ r.their() + " grudges.", net.minecraft.ChatFormatting.LIGHT_PURPLE);
			return;
		}
		// a newcomer: everything about the old house goes
		if (r.war) {
			Wars.end(c, r, Wars.Outcome.COLLAPSED, day);
		}
		Covert.forget(c, r);
		Dispatches.forget(c, r);
		Rival fresh = Courts.newcomer(c, day, random);
		fresh.dim = r.dim;
		fresh.seatX = r.seatX;
		fresh.seatY = r.seatY;
		fresh.seatZ = r.seatZ;
		fresh.built = r.built;
		fresh.dressed = r.dressed;
		fresh.seatName = r.seatName;
		int at = c.rivals.indexOf(r);
		c.rivals.set(at, fresh);
		for (Rival o : c.rivals) {
			o.relations.remove(r.id);
			o.wars.remove(r.id);
			o.partners.remove(r.id);
		}
		Seats.forget(c, r);
		Courts.tell(c, why + " " + capital(fresh.name()) + " moves into " + fresh.seatName + ", and hangs " + fresh.their() + " own banners.",
			net.minecraft.ChatFormatting.LIGHT_PURPLE);
	}

	static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
