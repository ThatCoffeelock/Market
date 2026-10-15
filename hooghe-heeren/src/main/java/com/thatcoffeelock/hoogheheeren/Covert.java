package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.Random;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * Errands nobody signs their name to, run by De Compagnie's agents. Pick an errand, an advisor (Kapitein, Koopman or
 * Spion: the agent's Force, Coin or Guile) and an agent; they're away for a day or few, and come home at dawn with
 * what they found, or wounded, captured or not at all, the same as from a Compagnie job. A botched errand may be
 * traced back to you: the house remembers, your Honour suffers, and they have a reason for war.
 */
final class Covert {
	enum Type {
		SPY("Spy on them", "spyglass", 2, 150, -1, 0.55, 0.70, -5, 20,
			"Read their letters: their real purse, army, temperament and plans for six days (ten on a triumph)."),
		SABOTAGE("Sabotage their trade", "flint_and_steel", 3, 300, 0.45, -1, 0.60, -20, 40,
			"Fires in the warehouses, holes in the hulls: their income drops by 40% for a week."),
		BANDITS("Pay bandits to raid them", "iron_axe", 2, 250, 0.60, 0.50, -1, -20, 40,
			"Highwaymen on their roads: they lose a tenth of their treasury, and a regiment chasing them."),
		STEAL("Rob their treasury", "gold_ingot", 3, 300, 0.35, -1, 0.50, -30, 60,
			"In through a back window, out with their gold: 15% of their treasury comes to you (25% on a triumph)."),
		BRIBE("Bribe their captains", "emerald", 2, 400, -1, 0.60, 0.45, -20, 40,
			"A purse for every captain: a quarter of their regiments desert, and half of those join you."),
		FORGE("Forge a letter", "writable_book", 2, 250, -1, 0.45, 0.60, -25, 60,
			"A letter in their hand, full of insults, delivered to another house: the two of them fall out badly."),
		ASSASSINATE("Assassinate", "wither_rose", 4, 1500, 0.35, -1, 0.40, -60, 0,
			"Only against an enemy (at war, or hostile). The head of the house dies; the heir takes over, and the grudges.");

		final String title;
		final String icon;
		final int days;
		/** In Marks, before the size of your company. */
		final long cost;
		/** Base odds by advisor: Kapitein (Force), Koopman (Coin), Spion (Guile). Below 0: won't touch it. */
		final double[] odds;
		/** What getting caught costs you in their opinion, and for how long (0: forever). */
		final int caughtOpinion;
		final int caughtFade;
		final String text;

		Type(String title, String icon, int days, long cost, double force, double coin, double guile, int caughtOpinion, int caughtFade, String text) {
			this.title = title;
			this.icon = icon;
			this.days = days;
			this.cost = cost;
			this.odds = new double[] {force, coin, guile};
			this.caughtOpinion = caughtOpinion;
			this.caughtFade = caughtFade;
			this.text = text;
		}

		static @Nullable Type byName(String name) {
			try {
				return valueOf(name);
			} catch (IllegalArgumentException | NullPointerException e) {
				return null;
			}
		}
	}

	static final String[] APPROACHES = {"FORCE", "COIN", "GUILE"};
	static final String[] ADVISORS = {"De Kapitein", "De Koopman", "De Spion"};
	static final String[] STATS = {"Force", "Coin", "Guile"};
	static final String[] ADVISOR_ICONS = {"iron_sword", "gold_ingot", "spyglass"};

	private Covert() {
	}

	static int approach(String name) {
		for (int i = 0; i < APPROACHES.length; i++) {
			if (APPROACHES[i].equals(name)) {
				return i;
			}
		}
		return 2;
	}

	static long cost(Court c, Type t) {
		return Bank.cents(Math.round(t.cost * (1 + Ai.rank(c) * 0.5)));
	}

	/** Can this errand be run against this rival at all? Returns why not, or null. */
	static @Nullable String allowed(Court c, Rival r, Type t, long day) {
		if (t == Type.ASSASSINATE && !r.war && c.opinion(r, day) > -30) {
			return "Assassination is for enemies: be at war with them, or at least hostile.";
		}
		if (t == Type.FORGE && c.rivals.size() < 2) {
			return "There's nobody to send the letter to.";
		}
		for (Court.Op op : c.ops) {
			if (op.rival.equals(r.id) && op.type.equals(t.name())) {
				return "You already have someone on that errand.";
			}
		}
		return null;
	}

	/** The chance of success: the errand's base odds that way, the agent's edge (as on a Compagnie job), and the target. */
	static double odds(Court c, Rival r, Type t, int approach, String agent) {
		double p = t.odds[approach] + CompagnieHooks.edge(c.uuid(), agent, APPROACHES[approach]);
		if (r.is(Quirk.PARANOID)) {
			p -= 0.10;
		}
		if (r.is(Temperament.SCHEMER)) {
			p -= 0.05;
		}
		p += SkillsLink.bonus(c.uuid(), "statecraft/spymaster");
		return Math.max(0.05, Math.min(0.95, p));
	}

	/** Sends an agent off. Returns why not, or null if they're on their way (and the errand is paid for). */
	static @Nullable String start(Court c, Rival r, Type t, int approach, String agent, String other, long day) {
		String why = allowed(c, r, t, day);
		if (why != null) {
			return why;
		}
		if (t.odds[approach] < 0) {
			return ADVISORS[approach] + " won't touch this errand.";
		}
		if (t == Type.FORGE && (c.rival(other) == null || other.equals(r.id))) {
			return "Pick the house that gets the letter.";
		}
		String name = "";
		boolean ready = false;
		for (CompagnieHooks.AgentInfo a : CompagnieHooks.agents(c.uuid())) {
			if (a.id().equals(agent)) {
				name = a.name();
				ready = a.state().equals("READY");
			}
		}
		if (!ready) {
			return "That agent isn't available.";
		}
		long cost = cost(c, t);
		if (!Bank.charge(c.uuid(), cost)) {
			return "You can't pay for it: " + Bank.format(cost) + ".";
		}
		if (!CompagnieHooks.dispatch(c.uuid(), agent, HeerenMod.ID)) {
			Bank.credit(c.uuid(), c.ownerName, cost);
			return "That agent isn't available.";
		}
		Court.Op op = new Court.Op();
		op.id = c.newId("o");
		op.type = t.name();
		op.rival = r.id;
		op.other = other;
		op.agent = agent;
		op.agentName = name;
		op.approach = APPROACHES[approach];
		op.paid = cost;
		op.start = day;
		op.end = day + t.days;
		c.ops.add(op);
		Courts.changed();
		return null;
	}

	/** At dawn: errands that are done come home. */
	static void dawn(Court c, long day, Random random) {
		for (Court.Op op : new ArrayList<>(c.ops)) {
			if (op.end <= day) {
				resolve(c, op, day, random);
			}
		}
	}

	enum Tier {
		TRIUMPH, SUCCESS, PARTIAL, FAILURE, DISASTER;

		boolean good() {
			return this == TRIUMPH || this == SUCCESS;
		}
	}

	/** The same roll as a Compagnie job: a quarter of the successes are triumphs; the worst misses are disasters. */
	static Tier roll(double p, Random random) {
		double r = random.nextDouble();
		if (r < p * 0.25) {
			return Tier.TRIUMPH;
		}
		if (r < p) {
			return Tier.SUCCESS;
		}
		if (r < p + (1 - p) * 0.4) {
			return Tier.PARTIAL;
		}
		return r < p + (1 - p) * 0.85 ? Tier.FAILURE : Tier.DISASTER;
	}

	static void resolve(Court c, Court.Op op, long day, Random random) {
		c.ops.remove(op);
		Type t = op.type();
		Rival r = c.rival(op.rival);
		if (t == null || r == null) {
			String fate = CompagnieHooks.recall(c.uuid(), op.agent, op.approach, "PARTIAL");
			c.chronicle(op.agentName + " came home: the house they were sent against is gone. " + fate);
			return;
		}
		double p = odds(c, r, t, approach(op.approach), op.agent);
		Tier tier = roll(p, random);
		SkillsLink.xp(c.uuid(), "statecraft", tier == Tier.TRIUMPH ? 12 : tier.good() ? 8 : tier == Tier.PARTIAL ? 3 : 0);
		StringBuilder line = new StringBuilder();
		line.append(tier.good() ? "✔ " : tier == Tier.PARTIAL ? "≈ " : "✘ ").append(t.title).append(" (").append(r.House()).append(", ")
			.append(op.agentName).append("): ");
		switch (t) {
			case SPY -> {
				if (tier.good() || tier == Tier.PARTIAL) {
					r.intelUntil = Math.max(r.intelUntil, day + (tier == Tier.TRIUMPH ? 10 : tier == Tier.PARTIAL ? 3 : 6));
					line.append("their letters are an open book. ").append(Ai.capital(r.they())).append(" plans: ").append(Ai.intentText(c, r));
				} else {
					line.append("nothing worth reading.");
				}
			}
			case SABOTAGE -> {
				if (tier.good()) {
					r.sabotagedUntil = day + (tier == Tier.TRIUMPH ? 10 : 7);
					line.append("their warehouses burned. Their income is down 40% for a while.");
				} else if (tier == Tier.PARTIAL) {
					r.sabotagedUntil = day + 3;
					line.append("a small fire. Their income is down for three days.");
				} else {
					line.append("the fuse went out.");
				}
			}
			case BANDITS -> {
				if (tier.good() || tier == Tier.PARTIAL) {
					long loss = r.treasury * (tier == Tier.TRIUMPH ? 15 : tier == Tier.PARTIAL ? 5 : 10) / 100;
					r.treasury -= loss;
					if (tier != Tier.PARTIAL && r.regiments > 0) {
						r.regiments--;
					}
					line.append("their roads ran red. They lost ").append(Bank.format(loss)).append(tier == Tier.PARTIAL ? "." : " and a regiment.");
				} else {
					line.append("the bandits took the money and went home.");
				}
			}
			case STEAL -> {
				if (tier.good() || tier == Tier.PARTIAL) {
					long take = r.treasury * (tier == Tier.TRIUMPH ? 25 : tier == Tier.SUCCESS ? 15 : 5) / 100;
					r.treasury -= take;
					Bank.credit(c.uuid(), c.ownerName, take);
					line.append(Bank.format(take)).append(" out of their strongroom and into yours.");
				} else {
					line.append("the strongroom was better guarded than it looked.");
				}
			}
			case BRIBE -> {
				if (tier.good() || tier == Tier.PARTIAL) {
					int gone = (int) Math.ceil(r.regiments * (tier == Tier.TRIUMPH ? 0.35 : tier == Tier.PARTIAL ? 0.1 : 0.25));
					int joined = tier == Tier.PARTIAL ? 0 : gone / 2;
					r.regiments -= gone;
					c.regiments += joined;
					line.append(gone).append(gone == 1 ? " regiment" : " regiments").append(" deserted them")
						.append(joined > 0 ? ", and " + joined + " marched over to you." : ".");
				} else {
					line.append("the captains pocketed the money and stayed put.");
				}
			}
			case FORGE -> {
				Rival other = c.rival(op.other);
				if (other != null && (tier.good() || tier == Tier.PARTIAL)) {
					int hit = tier == Tier.TRIUMPH ? 50 : tier == Tier.SUCCESS ? 35 : 15;
					other.relation(r.id, other.relation(r.id) - hit);
					r.relation(other.id, r.relation(other.id) - hit / 2);
					other.partners.remove(r.id);
					r.partners.remove(other.id);
					line.append(other.House()).append(" read every word, and believed it. They're at each other's throats.");
				} else {
					line.append("the letter was spotted for a forgery.");
				}
			}
			case ASSASSINATE -> {
				if (tier.good()) {
					line.append("it's done.");
					c.fear(10);
					c.chronicle(line.toString());
					finish(c, op, tier, r, day, random, "");
					Ai.fall(c, r, day, random, true, Ai.capital(r.name()) + " was found dead this morning.");
					return;
				} else if (tier == Tier.PARTIAL) {
					line.append("wounded, not dead. ").append(Ai.capital(r.they())).append(" won't sleep well for a while.");
					r.remember("Somebody tried to kill " + (r.woman ? "her" : "him"), -10, day, 30);
				} else {
					line.append("the target was ready for them.");
				}
			}
		}
		finish(c, op, tier, r, day, random, line.toString());
	}

	/** The agent comes home (or doesn't), and a botched errand may be traced back to you. */
	private static void finish(Court c, Court.Op op, Tier tier, Rival r, long day, Random random, String line) {
		Type t = op.type();
		boolean caught = tier == Tier.DISASTER || (tier == Tier.FAILURE && random.nextInt(100) < 35);
		String fate = CompagnieHooks.recall(c.uuid(), op.agent, op.approach, tier.name());
		StringBuilder out = new StringBuilder(line);
		if (caught && t != null) {
			r.remember("Caught your agent: " + t.title.toLowerCase(), t.caughtOpinion, day, r.is(Temperament.ZEALOT) && t.caughtFade > 0 ? t.caughtFade * 2 : t.caughtFade);
			c.honour(t == Type.ASSASSINATE ? -15 : -5);
			out.append(" Worse: it was traced back to you. ").append(Ai.capital(r.name())).append(" knows.");
			if (!r.war && !r.alliance && c.opinion(r, day) <= -40 && (r.regiments >= c.regiments || r.is(Temperament.WARLORD))) {
				out.append(" ").append(Ai.capital(r.they())).append(" calls it an act of war.");
				if (!line.isEmpty()) {
					c.chronicle(out.toString());
				}
				if (!fate.isEmpty()) {
					c.chronicle(fate);
				}
				Wars.declare(c, r, false, day);
				return;
			}
		}
		if (!fate.isEmpty()) {
			out.append(" ").append(fate);
		}
		if (out.length() > 0) {
			Courts.tell(c, out.toString(), tier.good() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
		}
	}

	/** A rival's gone: their errands end early (the agents come home with nothing). */
	static void forget(Court c, Rival r) {
		for (Court.Op op : new ArrayList<>(c.ops)) {
			if (op.rival.equals(r.id) || op.other.equals(r.id)) {
				c.ops.remove(op);
				String fate = CompagnieHooks.recall(c.uuid(), op.agent, op.approach, "PARTIAL");
				c.chronicle(op.agentName + " came home early: the house they were after is gone." + (fate.isEmpty() ? "" : " " + fate));
			}
		}
	}
}
