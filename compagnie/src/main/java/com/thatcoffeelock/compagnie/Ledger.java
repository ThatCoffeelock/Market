package com.thatcoffeelock.compagnie;

import java.util.Random;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Ledger: moneylending. Borrowers turn up at the desk asking for Marks (or gold ingots), for a few days, at an
 * interest that matches how likely they are to pay it back. On the due day they pay, or they default: then send a
 * collector (a job), sell the debt to Leonard the Usurer for 30%, or write it off.
 */
final class Ledger {
	static final int MAX_OFFERS = 4;
	static final double USURER = 0.3;
	private static final String[] REASONS = {"a new oven", "a tulip bulb (a really good one)", "his daughter's dowry",
		"\"investments\" (gambling debts)", "a share in a herring buss", "a printing press", "repairs to his windmill",
		"a cask of genever for the guild feast", "a portrait by a promising young painter", "bail for his brother",
		"a bigger loom", "passage to Batavia", "a shop on the Herengracht", "a cow (a very good cow)"};

	private Ledger() {
	}

	/** A new borrower: amount, risk and interest to match, a few days. */
	static Loan roll(Company c, long today, Random random) {
		Loan l = new Loan();
		l.id = c.newId("l");
		l.borrower = Names.borrower(random);
		l.reason = REASONS[random.nextInt(REASONS.length)];
		Loan.Risk risk = Loan.Risk.values()[random.nextInt(Loan.Risk.values().length)];
		l.risk = risk.name();
		l.gold = random.nextDouble() < 0.25;
		int rank = c.rank().ordinal();
		if (l.gold) {
			l.amount = 8 + random.nextInt(8 + 16 * rank);
		} else {
			long marks = 300 + (long) (random.nextDouble() * (c.rank().maxLoan - 300));
			l.amount = Bank.cents(Math.round(marks / 50.0) * 50);
		}
		double base = switch (risk) {
			case RELIABLE -> 0.06 + random.nextDouble() * 0.06;
			case SHAKY -> 0.12 + random.nextDouble() * 0.13;
			case NOTORIOUS -> 0.25 + random.nextDouble() * 0.20;
		};
		l.interest = Math.round(base * 100) / 100.0;
		l.days = 2 + random.nextInt(5);
		l.day = today + 2;
		return l;
	}

	static int gold(Player player) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void takeGold(Player player, long count) {
		Inventory inv = player.getInventory();
		long left = count;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				int take = (int) Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
	}

	/** Lends to a borrower on offer. Gold loans take the ingots from the player's inventory. Returns why not, or null. */
	static @Nullable String lend(Company c, Loan loan, @Nullable Player player, long today) {
		if (loan.state() != Loan.State.OFFER) {
			return "That's not on offer any more.";
		}
		if (loan.gold) {
			if (player == null || gold(player) < loan.amount) {
				return loan.borrower + " wants " + loan.amount + " gold ingots, in your hand. Bring them.";
			}
			takeGold(player, loan.amount);
		} else if (!Bank.charge(c.uuid(), loan.amount)) {
			return "You can't lend " + Bank.format(loan.amount) + ".";
		} else {
			c.profit -= loan.amount;
		}
		loan.state(Loan.State.ACTIVE);
		loan.day = today + loan.days;
		c.news("Lent " + loan.amountText() + " to " + loan.borrower + " for " + loan.reason + ". " + loan.owedText() + " due in " + loan.days + " days.");
		Companies.changed();
		return null;
	}

	/** Pays a loan back to the company: Marks to the balance, ingots to the strongbox. */
	private static void repay(Company c, Loan loan, long amount) {
		if (loan.gold) {
			Strongbox.add(c, Strongbox.make("gold_ingot", (int) amount));
		} else {
			Bank.credit(c.uuid(), c.ownerName, amount);
			c.profit += amount;
		}
	}

	/** At dawn: due loans are paid or defaulted, stale offers withdrawn, new borrowers turn up. */
	static void dawn(Company c, long today, Random random) {
		for (Loan loan : new java.util.ArrayList<>(c.loans)) {
			if (loan.state() == Loan.State.OFFER && loan.day < today) {
				c.loans.remove(loan);
			} else if (loan.state() == Loan.State.ACTIVE && loan.day <= today) {
				boolean pays = random.nextDouble() >= loan.risk().defaultChance;
				if (!pays && SkillsLink.roll(c.uuid(), "enterprise/iron_ledger")) {
					pays = true;
				}
				if (pays) {
					repay(c, loan, loan.owed());
					c.loans.remove(loan);
					c.rep += 15;
					SkillsLink.xp(c.uuid(), "enterprise", 5);
					c.news("✔ " + loan.borrower + " paid back " + loan.owedText() + ".");
				} else {
					loan.state(Loan.State.DEFAULTED);
					c.news("✘ " + loan.borrower + " can't pay the " + loan.owedText() + " they owe. Send a collector, sell the debt, or write it off.");
				}
			}
		}
		int offers = 0;
		for (Loan loan : c.loans) {
			if (loan.state() == Loan.State.OFFER) {
				offers++;
			}
		}
		if (offers < MAX_OFFERS && random.nextDouble() < 0.6) {
			c.loans.add(roll(c, today, random));
		}
		if (offers + 1 < MAX_OFFERS && c.rank().ordinal() >= 1 && random.nextDouble() < 0.4) {
			c.loans.add(roll(c, today, random));
		}
		Companies.changed();
	}

	/** Sells a defaulted debt to Leonard the Usurer for 30% (in Marks, even for gold). */
	static @Nullable String sell(Company c, Loan loan) {
		if (loan.state() != Loan.State.DEFAULTED) {
			return "Leonard only buys bad debts.";
		}
		long value = loan.gold ? Math.round(loan.owed() * USURER * Math.max(0, Bank.sellValue(new ItemStack(Items.GOLD_INGOT))))
			: Math.round(loan.owed() * USURER);
		Bank.credit(c.uuid(), c.ownerName, value);
		c.profit += value;
		c.loans.remove(loan);
		c.news("Sold " + loan.borrower + "'s debt to Leonard the Usurer for " + Bank.format(value) + ". He'll collect. Somehow.");
		Companies.changed();
		return null;
	}

	static void writeOff(Company c, Loan loan) {
		c.loans.remove(loan);
		c.news("Wrote off " + loan.borrower + "'s debt. Very Christian of you.");
		Companies.changed();
	}

	static String collectTarget(Loan loan) {
		return loan.id;
	}

	/** What a debt collector brought back, by how it went. */
	static String collected(Company c, Job job, Jobs.Tier tier) {
		Loan loan = c.loan(job.target);
		if (loan == null) {
			return "";
		}
		switch (tier) {
			case TRIUMPH -> {
				repay(c, loan, Math.round(loan.owed() * 1.2));
				c.loans.remove(loan);
				SkillsLink.xp(c.uuid(), "enterprise", 8);
				return " " + loan.borrower + " paid in full, plus a little extra for the trouble.";
			}
			case SUCCESS -> {
				repay(c, loan, loan.owed());
				c.loans.remove(loan);
				SkillsLink.xp(c.uuid(), "enterprise", 8);
				return " " + loan.borrower + " found the money after all: " + loan.owedText() + ".";
			}
			case PARTIAL -> {
				repay(c, loan, loan.owed() / 2);
				c.loans.remove(loan);
				return " Half of it, and a promise. The promise is worthless.";
			}
			case DISASTER -> {
				c.loans.remove(loan);
				return " " + loan.borrower + " has fled the city. The debt's gone with them.";
			}
			default -> {
				loan.state(Loan.State.DEFAULTED);
				return " " + loan.borrower + " slammed the door. Try again?";
			}
		}
	}
}
