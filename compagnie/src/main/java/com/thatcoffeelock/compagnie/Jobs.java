package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Running jobs: the odds, sending an agent off, telegrams on the road and what comes back.
 * <p>
 * The odds start from the job's base chance for the approach, then: the agent's stat for it (4% a point above or
 * below 5), the stake (lean -10%, lavish +10%), the company's rank (2% a rank), traits, buffs, telegram answers and
 * the Enterprise skill. Then a roll: a quarter of the successes are triumphs; of the misses, some are partial, most
 * are failures and the worst are disasters.
 */
final class Jobs {
	enum Tier {
		TRIUMPH("Triumph!"), SUCCESS("Success"), PARTIAL("Partial success"), FAILURE("Failure"), DISASTER("Disaster!");

		final String title;

		Tier(String title) {
			this.title = title;
		}

		boolean good() {
			return this == TRIUMPH || this == SUCCESS;
		}
	}

	static final String[] STAKES = {"Lean", "Standard", "Lavish"};
	static final double[] STAKE_ODDS = {-0.10, 0, 0.10};
	/** Buffs: sea odds, trade odds up, trade odds down, trade payout. */
	static final String HARBOUR = "harbour";
	static final String RIVAL_BURNED = "rival_burned";
	static final String RIVAL_ANGRY = "rival_angry";
	static final String CORNER = "corner";
	/** Chance a job of 2+ days gets a telegram on the road. */
	static final double TELEGRAM_CHANCE = 0.5;

	private Jobs() {
	}

	// ---------------------------------------------------------------- odds

	static double traitBonus(Agent agent, JobType type, Approach a) {
		double bonus = 0;
		if (agent.has(Trait.LUCKY)) {
			bonus += 0.08;
		}
		if (agent.has(Trait.VETERAN)) {
			bonus += 0.05;
		}
		if (agent.has(Trait.DRUNKARD)) {
			bonus -= 0.06;
		}
		if (agent.has(Trait.COWARD) && a == Approach.FORCE) {
			bonus -= 0.10;
		}
		if (agent.has(Trait.NAVIGATOR) && type.sea) {
			bonus += 0.10;
		}
		if (agent.has(Trait.SILVER_TONGUED) && a == Approach.COIN) {
			bonus += 0.10;
		}
		if (agent.has(Trait.BUTCHER) && a == Approach.FORCE) {
			bonus += 0.10;
		}
		if (agent.has(Trait.SHADOW) && a == Approach.GUILE) {
			bonus += 0.10;
		}
		return bonus;
	}

	/** The chance of success, 5% to 95%. {@code job} adds its telegram answers, if it's under way. */
	static double odds(Company c, JobType type, Approach a, Agent agent, int stake, long today, @Nullable Job job) {
		double p = type.odds[a.ordinal()];
		p += (agent.stat(a) - 5) * 0.04;
		p += STAKE_ODDS[Math.max(0, Math.min(2, stake))];
		p += c.rank().ordinal() * 0.02;
		p += traitBonus(agent, type, a);
		if (type.sea && c.buff(HARBOUR, today)) {
			p += 0.10;
		}
		if (type.trade() && c.buff(RIVAL_BURNED, today)) {
			p += 0.10;
		}
		if (type.trade() && c.buff(RIVAL_ANGRY, today)) {
			p -= 0.10;
		}
		p += SkillsLink.bonus(c.uuid(), "enterprise/passive");
		if (job != null) {
			p += job.oddsMod;
		}
		return Math.max(0.05, Math.min(0.95, p));
	}

	static int days(JobType type, Agent agent) {
		return Math.max(1, type.days - (agent.has(Trait.NAVIGATOR) && type.sea ? 1 : 0));
	}

	static long stakeCents(JobType type, int stake) {
		return Bank.cents(type.stakes[Math.max(0, Math.min(2, stake))]);
	}

	// ---------------------------------------------------------------- sending someone

	/** Sends an agent off on a job. Returns why not, or null if they're on their way (and the stake is paid). */
	static @Nullable String start(Company c, JobType type, @Nullable Job offer, Approach a, Agent agent, int stake, long today, String target) {
		if (!type.allows(a)) {
			return a.advisor + " won't touch this job.";
		}
		if (agent.state() != Agent.State.READY) {
			return agent.name + " isn't available.";
		}
		if (c.active.size() >= c.slots()) {
			return "All " + c.slots() + " job slots are taken. A bigger company runs more at once.";
		}
		long cents = stakeCents(type, stake);
		if (!Bank.charge(c.uuid(), cents)) {
			return "You can't bankroll " + Bank.format(cents) + ".";
		}
		Job job = offer != null ? offer : new Job();
		if (job.id.isEmpty()) {
			job.id = c.newId("j");
		}
		job.type = type.id();
		job.approach = a.name();
		job.agent = agent.id;
		job.stake = stake;
		job.paid = cents;
		job.start = today;
		job.end = today + days(type, agent);
		job.target = target;
		job.ship = Names.ship(new Random());
		c.board.remove(job);
		c.active.add(job);
		agent.state(Agent.State.AWAY);
		c.profit -= cents;
		Companies.changed();
		return null;
	}

	// ---------------------------------------------------------------- telegrams

	/** At a dawn before the job's done: maybe a telegram comes in. True if one did. */
	static boolean maybeTelegram(Company c, Job job, long today, Random random) {
		JobType type = job.type();
		if (type == null || !job.telegram.isEmpty() || today <= job.start || today >= job.end || random.nextDouble() >= TELEGRAM_CHANCE) {
			return false;
		}
		Agent agent = c.agent(job.agent);
		Telegram t = agent != null && (agent.has(Trait.DRUNKARD) || agent.has(Trait.GREEDY)) && random.nextInt(3) == 0
			? Telegram.MUTINY : Telegram.pick(type, random);
		if (t == null) {
			return false;
		}
		job.telegram = t.name();
		job.choice = -1;
		job.telegramCost = Math.max(Bank.cents(50), Math.round(job.paid * 0.15));
		Companies.changed();
		return true;
	}

	/** Answers a job's telegram. Returns why not, or null if done. */
	static @Nullable String answer(Company c, Job job, int choice) {
		Telegram t = Telegram.byName(job.telegram);
		if (t == null || job.choice >= 0) {
			return "There's no telegram waiting for that job.";
		}
		if (choice == 0) {
			if (!Bank.charge(c.uuid(), job.telegramCost)) {
				return "You can't afford " + Bank.format(job.telegramCost) + ".";
			}
			c.profit -= job.telegramCost;
			job.oddsMod += t.paid;
		} else if (choice == 1) {
			if (t.check != null) {
				Agent agent = c.agent(job.agent);
				job.oddsMod += agent != null && agent.stat(t.check) >= 6 ? 0.06 : -0.10;
			}
		} else {
			switch (t.third) {
				case ABORT -> {
					abort(c, job);
					job.choice = choice;
					return null;
				}
				case SAFE -> job.safe = true;
				case DETOUR -> {
					job.payoutMod += 0.30;
					job.oddsMod -= 0.08;
				}
				case CONCEDE -> job.oddsMod -= 0.10;
				case FORCE_CHECK -> {
					Agent agent = c.agent(job.agent);
					job.oddsMod += agent != null && agent.force >= 6 ? 0.15 : -0.10;
				}
			}
		}
		job.choice = choice;
		Companies.changed();
		return null;
	}

	/** Turning back: 60% of the stake comes home with the agent. */
	static void abort(Company c, Job job) {
		long back = Math.round(job.paid * 0.6);
		Bank.credit(c.uuid(), c.ownerName, back);
		c.profit += back;
		Agent agent = c.agent(job.agent);
		if (agent != null) {
			agent.state(Agent.State.READY);
		}
		c.active.remove(job);
		if (job.type() == JobType.COLLECT_DEBT) {
			Loan loan = c.loan(job.target);
			if (loan != null) {
				loan.state(Loan.State.DEFAULTED);
			}
		}
		JobType type = job.type();
		c.news("↩ " + (type == null ? "A job" : type.title) + ": turned back. " + Bank.format(back) + " of the stake came home.");
		Companies.changed();
	}

	// ---------------------------------------------------------------- what comes back

	/** What happened, for the news and the smoke test. */
	record Result(Tier tier, long marks, List<ItemStack> items, String line) {
	}

	static Tier roll(double p, boolean safe, Random random) {
		double r = random.nextDouble();
		Tier tier;
		if (r < p * 0.25) {
			tier = Tier.TRIUMPH;
		} else if (r < p) {
			tier = Tier.SUCCESS;
		} else if (r < p + (1 - p) * 0.4) {
			tier = Tier.PARTIAL;
		} else if (r < p + (1 - p) * 0.85) {
			tier = Tier.FAILURE;
		} else {
			tier = Tier.DISASTER;
		}
		if (safe) {
			tier = tier.good() ? Tier.PARTIAL : tier == Tier.DISASTER ? Tier.FAILURE : tier;
		}
		return tier;
	}

	/** The stake comes back times this, by tier. */
	static double payoutFactor(JobType type, Tier tier) {
		if (type.mult <= 0) {
			return 0;
		}
		return switch (tier) {
			case TRIUMPH -> type.mult * 1.6;
			case SUCCESS -> type.mult;
			case PARTIAL -> 0.6;
			case FAILURE -> 0.2;
			case DISASTER -> 0;
		};
	}

	/** Brings a job home: rolls it, pays out, fills the strongbox, settles the agent's fate, writes the news. */
	static Result resolve(Company c, Job job, long today, Random random, @Nullable ServerLevel level) {
		JobType type = job.type();
		Approach a = job.approach();
		Agent agent = c.agent(job.agent);
		c.active.remove(job);
		if (type == null || a == null || agent == null) {
			Companies.changed();
			return new Result(Tier.FAILURE, 0, List.of(), "A job came back with nobody to report on it.");
		}
		double p = odds(c, type, a, agent, job.stake, today, job);
		Tier tier = roll(p, job.safe, random);
		double bonus = SkillsLink.bonus(c.uuid(), "enterprise/chartered");

		// Marks
		double payout = job.paid * payoutFactor(type, tier) * (1 + job.payoutMod) * (1 + bonus);
		if (type.trade() && c.buff(CORNER, today)) {
			payout *= 1.2;
		}
		long marks = Math.round(payout);
		long cut = agent.has(Trait.GREEDY) ? Math.round(marks * 0.15) : 0;
		marks -= cut;
		Bank.credit(c.uuid(), c.ownerName, marks);
		c.profit += marks;

		// spoils
		List<ItemStack> items = new ArrayList<>();
		if (tier.good() || tier == Tier.PARTIAL) {
			double more = (agent.has(Trait.BUTCHER) ? 1.1 : 1.0) * (1 + bonus) * (tier == Tier.PARTIAL ? 0.5 : 1.0);
			for (JobType.Spoil s : type.spoils) {
				if ((s.triumphOnly() && tier != Tier.TRIUMPH) || random.nextDouble() >= s.chance()) {
					continue;
				}
				int n = (int) Math.round((s.min() + random.nextInt(s.max() - s.min() + 1)) * more);
				ItemStack stack = Strongbox.make(s.item(), Math.max(1, n));
				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
			// Fuck Illagers: proof of the dead; Riches: an expedition may find a relic
			if (type == JobType.ILLAGER_CAMP) {
				ItemStack fingers = Links.fingers(tier == Tier.PARTIAL ? 1 + random.nextInt(2) : 2 + random.nextInt(5));
				if (!fingers.isEmpty()) {
					items.add(fingers);
				}
			}
			if (type == JobType.RELIC_EXPEDITION && (tier == Tier.TRIUMPH || (tier == Tier.SUCCESS && random.nextDouble() < 0.35))) {
				ItemStack relic = Links.relic(c);
				if (!relic.isEmpty()) {
					items.add(relic);
					c.news("★ A relic! The expedition brought home " + relic.getHoverName().getString() + ".");
				}
			}
		}
		StringBuilder extra = new StringBuilder();
		long cargoMarks = cargo(c, job, type, tier, bonus, level, items, extra);
		marks += cargoMarks;
		special(c, job, type, tier, today, random, level, items, extra);
		for (ItemStack stack : items) {
			Strongbox.add(c, stack);
		}

		// the agent
		String fate = fate(c, agent, a, tier, today, random);

		// standing
		long gain = marks - job.paid;
		long rep = switch (tier) {
			case TRIUMPH -> 60;
			case SUCCESS -> 40;
			case PARTIAL -> 10;
			default -> 0;
		};
		c.rep += rep + Math.max(0, gain / Bank.cents(20));
		if (tier.good()) {
			c.jobsDone++;
		}
		SkillsLink.xp(c.uuid(), "enterprise", switch (tier) {
			case TRIUMPH -> 15;
			case SUCCESS -> 10;
			case PARTIAL -> 4;
			default -> 1;
		});

		StringBuilder line = new StringBuilder();
		line.append(tier.good() ? "✔ " : tier == Tier.PARTIAL ? "≈ " : "✘ ").append(type.title).append(" (").append(agent.name)
			.append(", ").append(a.advisor).append("): ").append(tier.title);
		if (marks > 0) {
			line.append(" ").append(Bank.format(marks)).append(" back on a ").append(Bank.format(job.paid)).append(" stake.");
		}
		if (cut > 0) {
			line.append(" (").append(agent.name).append(" kept ").append(Bank.format(cut)).append(".)");
		}
		if (!items.isEmpty()) {
			line.append(" Spoils in the strongbox: ").append(Strongbox.describe(items)).append(".");
		}
		line.append(extra);
		if (!fate.isEmpty()) {
			line.append(" ").append(fate);
		}
		c.news(line.toString());
		Companies.changed();
		return new Result(tier, marks, items, line.toString());
	}

	/** Luxury Export pays for the cargo; a chartered ship earns its fee and comes home (unless it sank). Returns Marks paid. */
	private static long cargo(Company c, Job job, JobType type, Tier tier, double bonus, @Nullable ServerLevel level, List<ItemStack> items,
		StringBuilder extra) {
		long paid = 0;
		if (type.special == JobType.Special.EXPORT) {
			double factor = switch (tier) {
				case TRIUMPH -> 3.2;
				case SUCCESS -> 2.5;
				case PARTIAL -> 1.0;
				case FAILURE -> 0.3;
				case DISASTER -> 0;
			};
			paid = Math.round(job.cargo * factor * (1 + bonus));
			extra.append(" The cargo (worth ").append(Bank.format(job.cargo)).append(" in Amsterdam) ").append(paid > 0 ? "fetched " + Bank.format(paid) + " abroad." : "is lost.");
		} else if (type.special == JobType.Special.CHARTER) {
			long fee = Bank.cents(switch (tier) {
				case TRIUMPH -> 3000;
				case SUCCESS -> 2000;
				case PARTIAL -> 800;
				case FAILURE -> 200;
				case DISASTER -> 0;
			});
			paid = Math.round(fee * (1 + bonus));
			ItemStack ship = level == null ? ItemStack.EMPTY : Cargo.decode(level, job.cargoItem);
			if (tier == Tier.DISASTER) {
				extra.append(" The ship went down with all hands. It's gone.");
			} else {
				extra.append(" The charter paid ").append(Bank.format(paid)).append(", and the ship's back in the strongbox.");
				if (!ship.isEmpty()) {
					items.add(ship);
				}
			}
			job.cargoItem = null;
		}
		if (paid > 0) {
			Bank.credit(c.uuid(), c.ownerName, paid);
			c.profit += paid;
		}
		return paid;
	}

	/** Jobs that do something besides paying out. */
	private static void special(Company c, Job job, JobType type, Tier tier, long today, Random random, @Nullable ServerLevel level,
		List<ItemStack> items, StringBuilder extra) {
		boolean good = tier.good();
		switch (type.special) {
			case CHART -> {
				if (good || tier == Tier.PARTIAL) {
					ItemStack chart = level == null ? ItemStack.EMPTY : Charts.chart(level, type, c, random);
					if (chart.isEmpty()) {
						items.add(Strongbox.make("gold_ingot", tier == Tier.TRIUMPH ? 8 : 4));
						extra.append(" They found nothing worth charting, and brought gold instead.");
					} else {
						items.add(chart);
						extra.append(" ").append(Charts.describe(chart));
					}
				}
			}
			case CORNER -> {
				if (good || tier == Tier.PARTIAL) {
					c.buffs.put(CORNER, today + (tier == Tier.PARTIAL ? 1 : 3));
					extra.append(" Trade jobs pay 20% more for a while.");
				}
			}
			case RIVAL -> {
				if (good) {
					c.buffs.put(RIVAL_BURNED, today + 4);
					extra.append(" The English are reeling: trade odds +10% for four days.");
				} else if (tier == Tier.DISASTER) {
					c.buffs.put(RIVAL_ANGRY, today + 3);
					extra.append(" The English know it was us: trade odds -10% for three days.");
				}
			}
			case HARBOUR -> {
				if (good || tier == Tier.PARTIAL) {
					c.buffs.put(HARBOUR, today + (good ? 5 : 2));
					extra.append(" The harbourmaster is ours: sea jobs +10% odds.");
				}
			}
			case REP -> {
				long rep = switch (tier) {
					case TRIUMPH -> 1600;
					case SUCCESS -> 800;
					case PARTIAL -> 300;
					default -> 0;
				};
				if (rep > 0) {
					c.rep += rep;
					extra.append(" The company's name carries further: +").append(rep).append(" reputation.");
				}
			}
			case RECRUIT -> {
				if (good || tier == Tier.PARTIAL) {
					int n = tier == Tier.TRIUMPH ? 2 : 1;
					for (int i = 0; i < n; i++) {
						Agent fresh = Hiring.roll(c, random);
						fresh.hireCost = 0;
						c.candidates.add(fresh);
					}
					extra.append(" ").append(n).append(" pressganged sailor").append(n == 1 ? " is" : "s are").append(" at the hiring board, free.");
				}
			}
			case OIL -> {
				if (good || tier == Tier.PARTIAL) {
					java.util.Map<?, ?> pocket = level == null ? null : Links.pocket(level, home(c));
					if (pocket != null && pocket.get("x") instanceof Integer x && pocket.get("y") instanceof Integer y && pocket.get("z") instanceof Integer z) {
						boolean gusher = Boolean.TRUE.equals(pocket.get("gusher"));
						ItemStack survey = Charts.item(gusher ? "fossilfool:gusher" : "fossilfool:oil_pocket", new BlockPos(x, y, z));
						items.add(survey);
						extra.append(" ").append(Charts.describe(survey)).append(" Drill down to Y ").append(y).append(".");
					} else {
						extra.append(" The rod didn't so much as twitch for miles around.");
					}
				}
			}
			case SACKS -> {
				int n = tier == Tier.TRIUMPH ? 3 : tier == Tier.SUCCESS ? 2 : tier == Tier.PARTIAL ? 1 : 0;
				int brought = 0;
				for (int i = 0; i < n; i++) {
					ItemStack sack = Links.villagerSack(Names.agent(random).replaceAll(" '.*' ", " "));
					if (!sack.isEmpty()) {
						items.add(sack);
						brought++;
					}
				}
				if (brought > 0) {
					extra.append(" ").append(brought).append(" settler").append(brought == 1 ? "" : "s").append(" came home in sacks, ready for a colony.");
				}
			}
			case EXPORT, CHARTER -> {
			}
			case COLLECT -> extra.append(Ledger.collected(c, job, tier));
			case RESCUE -> extra.append(rescued(c, job, tier, today));
			case NONE -> {
			}
		}
	}

	private static String rescued(Company c, Job job, Tier tier, long today) {
		Agent prisoner = c.agent(job.target);
		if (prisoner == null || prisoner.state() != Agent.State.CAPTURED) {
			return "";
		}
		switch (tier) {
			case TRIUMPH, SUCCESS -> {
				prisoner.state(Agent.State.READY);
				return " " + prisoner.name + " is free and back on the books.";
			}
			case PARTIAL -> {
				prisoner.state(Agent.State.WOUNDED);
				prisoner.until = today + 2;
				return " " + prisoner.name + " got out, barely, and needs two days to recover.";
			}
			case DISASTER -> {
				c.agents.remove(prisoner);
				c.agentsLost++;
				return " The guards were waiting. " + prisoner.name + " was hanged at dawn.";
			}
			default -> {
				return " " + prisoner.name + " is still in a cell.";
			}
		}
	}

	/** What becomes of the agent: experience, or wounds, a cell, or a grave. */
	static String fate(Company c, Agent agent, Approach a, Tier tier, long today, Random random) {
		agent.state(Agent.State.READY);
		agent.jobs++;
		switch (tier) {
			case TRIUMPH -> agent.xp += 3;
			case SUCCESS -> agent.xp += 2;
			case PARTIAL -> agent.xp += 1;
			case FAILURE -> {
				if (random.nextDouble() < 0.25) {
					agent.state(Agent.State.WOUNDED);
					agent.until = today + 1 + random.nextInt(2);
					return agent.name + " came back wounded.";
				}
			}
			case DISASTER -> {
				double[] odds = switch (a) {
					case FORCE -> new double[] {0.4, 0.3, 0.3};
					case COIN -> new double[] {0.1, 0.2, 0.3};
					case GUILE -> new double[] {0.2, 0.5, 0.3};
				};
				double r = random.nextDouble();
				if (r < odds[0]) {
					if (agent.has(Trait.COWARD)) {
						agent.state(Agent.State.WOUNDED);
						agent.until = today + 3;
						return agent.name + " ran for it, and came back badly wounded.";
					}
					c.agents.remove(agent);
					c.agentsLost++;
					return agent.name + " didn't come back. ⚰";
				}
				if (r < odds[0] + odds[1]) {
					agent.state(Agent.State.CAPTURED);
					return agent.name + " was captured. Pay the ransom (" + Bank.format(Hiring.ransom(agent)) + ") or send a rescue.";
				}
				if (r < odds[0] + odds[1] + odds[2]) {
					agent.state(Agent.State.WOUNDED);
					agent.until = today + 3;
					return agent.name + " came back badly wounded.";
				}
			}
		}
		if (agent.level < 5 && agent.xp >= agent.level * 5) {
			agent.xp -= agent.level * 5;
			agent.level++;
			agent.improveBest();
			agent.wage = Math.round(agent.wage * 1.15);
			return agent.name + " is now level " + agent.level + " (and wants a raise).";
		}
		return "";
	}

	/** The position of the company's desk the owner last used, for searching nearby. */
	static BlockPos home(Company c) {
		return new BlockPos(c.homeX, 64, c.homeZ);
	}
}
