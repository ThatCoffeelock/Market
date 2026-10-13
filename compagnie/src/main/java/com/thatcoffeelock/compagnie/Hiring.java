package com.thatcoffeelock.compagnie;

import java.util.Random;

import org.jetbrains.annotations.Nullable;

/**
 * The hiring board and the payroll. Three candidates turn up every two days; better ones as the company grows.
 * Wages are paid at every dawn. Can't pay? The best-paid agent who's in walks out.
 */
final class Hiring {
	static final int CANDIDATES = 3;

	private Hiring() {
	}

	/** A new face: three stats with one they're good at, a trait or two, a price and a wage. */
	static Agent roll(Company c, Random random) {
		Agent a = new Agent();
		a.id = c.newId("a");
		a.name = Names.agent(random);
		int rank = c.rank().ordinal();
		int[] stats = new int[3];
		for (int i = 0; i < 3; i++) {
			stats[i] = 1 + random.nextInt(5) + rank;
		}
		stats[random.nextInt(3)] += 2;
		a.force = Math.max(1, Math.min(10, stats[0]));
		a.coin = Math.max(1, Math.min(10, stats[1]));
		a.guile = Math.max(1, Math.min(10, stats[2]));
		a.traits.add(Trait.roll(random).name());
		if (random.nextDouble() < 0.35) {
			Trait second = Trait.roll(random);
			if (!a.traits.contains(second.name())) {
				a.traits.add(second.name());
			}
		}
		int sum = a.force + a.coin + a.guile;
		double factor = 1.0;
		if (a.has(Trait.VETERAN)) {
			factor *= 1.2;
		}
		if (a.has(Trait.DRUNKARD)) {
			factor *= 0.6;
		}
		double broker = 1.0 - SkillsLink.bonus(c.uuid(), "enterprise/broker");
		a.hireCost = Bank.cents(Math.round((150 + 40 * sum) * factor * broker));
		a.wage = Bank.cents(Math.round((4 + 0.8 * sum) * factor * broker));
		return a;
	}

	/** New candidates when the board's empty or two days old. */
	static void refresh(Company c, long today, Random random) {
		if (!c.candidates.isEmpty() && c.candidatesDay >= 0 && today - c.candidatesDay < 2) {
			return;
		}
		// pressganged sailors stay until hired
		c.candidates.removeIf(a -> a.hireCost > 0);
		while (c.candidates.size() < CANDIDATES) {
			c.candidates.add(roll(c, random));
		}
		c.candidatesDay = today;
		Companies.changed();
	}

	/** Hires a candidate. Returns why not, or null if they're on the books. */
	static @Nullable String hire(Company c, Agent candidate) {
		if (!c.candidates.contains(candidate)) {
			return "They've moved on.";
		}
		if (c.agents.size() >= c.maxAgents()) {
			return "The books are full: your company keeps " + c.maxAgents() + " agents.";
		}
		if (!Bank.charge(c.uuid(), candidate.hireCost)) {
			return "You can't afford " + Bank.format(candidate.hireCost) + " to sign them.";
		}
		c.candidates.remove(candidate);
		c.agents.add(candidate);
		c.profit -= candidate.hireCost;
		c.news("Hired " + candidate.name + " (" + candidate.traitText() + ") for " + Bank.format(candidate.wage) + " a day.");
		Companies.changed();
		return null;
	}

	/** Lets an agent go. Not while they're away or in a cell. */
	static @Nullable String dismiss(Company c, Agent agent) {
		Agent.State s = agent.state();
		if (s == Agent.State.AWAY || s == Agent.State.CAPTURED) {
			return agent.name + " can't be dismissed right now.";
		}
		c.agents.remove(agent);
		c.news("Let " + agent.name + " go.");
		Companies.changed();
		return null;
	}

	static long ransom(Agent agent) {
		return Math.max(Bank.cents(300), Math.round(agent.hireCost * 1.5));
	}

	/** Pays a captured agent's ransom. Returns why not, or null if they're free. */
	static @Nullable String payRansom(Company c, Agent agent) {
		if (agent.state() != Agent.State.CAPTURED) {
			return agent.name + " isn't in a cell.";
		}
		long cost = ransom(agent);
		if (!Bank.charge(c.uuid(), cost)) {
			return "You can't afford the " + Bank.format(cost) + " ransom.";
		}
		c.profit -= cost;
		agent.state(Agent.State.READY);
		c.news("Paid " + Bank.format(cost) + " to get " + agent.name + " out of a cell.");
		Companies.changed();
		return null;
	}

	/** At dawn: wages, and the wounded get back on their feet. */
	static void payday(Company c, long today) {
		for (Agent a : c.agents) {
			if (a.state() == Agent.State.WOUNDED && a.until <= today) {
				a.state(Agent.State.READY);
				c.news(a.name + " is back on their feet.");
			}
		}
		long wages = c.dailyWages();
		if (wages <= 0) {
			return;
		}
		if (Bank.charge(c.uuid(), wages)) {
			c.profit -= wages;
			return;
		}
		Agent leaving = null;
		for (Agent a : c.agents) {
			if (a.state() == Agent.State.READY || a.state() == Agent.State.WOUNDED) {
				if (leaving == null || a.wage > leaving.wage) {
					leaving = a;
				}
			}
		}
		if (leaving != null) {
			c.agents.remove(leaving);
			c.news("No wages today (" + Bank.format(wages) + " due). " + leaving.name + " walked out in disgust.");
		} else {
			c.news("No wages today (" + Bank.format(wages) + " due). Everyone's grumbling.");
		}
		Companies.changed();
	}
}
