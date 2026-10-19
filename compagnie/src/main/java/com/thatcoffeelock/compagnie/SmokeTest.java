package com.thatcoffeelock.compagnie;

import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dcompagnie.smokeTest=true (CI). Boots a real server and runs a company without a player: places a
 * desk, founds a company, hires an agent, works out odds, sends them off, answers a telegram, brings jobs home, finds
 * a chart, fills the strongbox to overflowing, lends money (repaid, defaulted, collected, sold to the usurer), loses
 * an agent and ransoms another, climbs the ranks, misses a payday, and saves and loads it all.
 */
final class SmokeTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000c0c0a");
	private static final BlockPos DESK = new BlockPos(0, 101, 0);

	/** A Random whose rolls always come out the same, for the outcomes we want to see. */
	private static final class Fixed extends Random {
		private final double value;

		Fixed(double value) {
			this.value = value;
		}

		@Override
		public double nextDouble() {
			return value;
		}

		@Override
		public int nextInt(int bound) {
			return 0;
		}
	}

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		CompagnieMod.later(60, () -> {
			try {
				test(server.overworld());
				CompagnieMod.LOG.info("COMPAGNIE SMOKE TEST PASSED");
			} catch (Throwable t) {
				CompagnieMod.LOG.error("COMPAGNIE SMOKE TEST FAILED", t);
			}
			server.halt(false);
		});
	}

	private static void test(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Companies.tick(server);
		long today = Companies.today();

		// the desk
		ItemStack deskItem = Desks.item();
		check(Desks.isItem(deskItem) && !Desks.isItem(new ItemStack(Items.CARTOGRAPHY_TABLE)), "a Company Desk is one, a plain cartography table isn't");
		Cmd.run(level, "setblock " + DESK.getX() + " " + DESK.getY() + " " + DESK.getZ() + " minecraft:cartography_table");
		Desks.add(level, DESK, false);
		check(Desks.at(level, DESK) != null, "the desk is remembered");

		// a company
		Company c = Companies.of(OWNER, "SmokeTest");
		check(c.rank() == Rank.CHARTER && c.board.size() == Rank.CHARTER.board, "a new company is a Charter with a full board (" + c.board.size() + ")");
		check(c.candidates.size() == Hiring.CANDIDATES && !c.loans.isEmpty(), "candidates at the hiring board, a borrower on the Ledger");
		for (Job offer : c.board) {
			JobType t = offer.type();
			check(t != null && t.onBoard() && t.minRank == 0, "the board only offers Charter-level jobs (" + offer.type + ")");
		}
		Bank.credit(OWNER, "SmokeTest", Bank.cents(100_000));

		// hiring
		Agent agent = c.candidates.get(0);
		long before = Bank.balance(OWNER);
		check(Hiring.hire(c, agent) == null && c.agents.contains(agent) && Bank.balance(OWNER) == before - agent.hireCost,
			"hiring " + agent.name + " for " + Bank.format(agent.hireCost));
		agent.traits.clear();
		check(agent.force >= 1 && agent.force <= 10 && agent.wage > 0, "agents have stats from 1 to 10 and a wage");

		// odds
		JobType escort = JobType.ESCORT_CONVOY;
		double lean = Jobs.odds(c, escort, Approach.FORCE, agent, 0, today, null);
		double lavish = Jobs.odds(c, escort, Approach.FORCE, agent, 2, today, null);
		check(lavish > lean && lavish - lean <= 0.2001, "a lavish stake has better odds than a lean one (" + lean + " / " + lavish + ")");
		check(Jobs.start(c, JobType.UNDERWRITE, null, Approach.FORCE, agent, 1, today, "") != null, "the Kapitein won't underwrite a voyage");

		// sending someone off
		Job offer = new Job();
		offer.id = c.newId("j");
		offer.type = escort.id();
		c.board.add(offer);
		before = Bank.balance(OWNER);
		check(Jobs.start(c, escort, offer, Approach.FORCE, agent, 1, today, "") == null, "sending " + agent.name + " to escort a convoy");
		check(c.active.contains(offer) && !c.board.contains(offer) && agent.state() == Agent.State.AWAY
			&& Bank.balance(OWNER) == before - Jobs.stakeCents(escort, 1), "the stake is paid and the agent is away");
		check(Jobs.start(c, escort, null, Approach.FORCE, agent, 1, today, "") != null, "an agent who's away can't be sent twice");

		// a telegram
		check(Jobs.maybeTelegram(c, offer, today + 1, new Fixed(0.0)) && offer.pendingTelegram(), "a telegram comes in on the road");
		boolean reads = true;
		for (Telegram t : Telegram.values()) {
			for (int a = 0; a < 3; a++) {
				reads &= !t.answer(a, offer).contains("%s") && !t.text(offer).contains("%s");
			}
		}
		check(reads, "every telegram and its answers read without a hitch");
		before = Bank.balance(OWNER);
		double oddsBefore = Jobs.odds(c, escort, Approach.FORCE, agent, 1, today, offer);
		check(Jobs.answer(c, offer, 0) == null && !offer.pendingTelegram() && Bank.balance(OWNER) == before - offer.telegramCost
			&& Jobs.odds(c, escort, Approach.FORCE, agent, 1, today, offer) > oddsBefore, "paying up in a telegram buys better odds");
		check(Jobs.answer(c, offer, 1) != null, "a telegram is answered once");

		// the dice
		int good = 0;
		int disasters = 0;
		Random random = new Random(42);
		for (int i = 0; i < 10_000; i++) {
			Jobs.Tier tier = Jobs.roll(0.7, false, random);
			good += tier.good() ? 1 : 0;
			disasters += tier == Jobs.Tier.DISASTER ? 1 : 0;
		}
		check(good > 6700 && good < 7300 && disasters > 300 && disasters < 600, "70% odds: about 70% succeed, about 4.5% disasters (" + good + ", " + disasters + ")");
		for (int i = 0; i < 1000; i++) {
			Jobs.Tier tier = Jobs.roll(0.9, true, random);
			check(!tier.good() && tier != Jobs.Tier.DISASTER, "dumping the cargo means at best a partial, never a disaster", false);
		}

		// coming home: a triumph
		before = Bank.balance(OWNER);
		Jobs.Result result = Jobs.resolve(c, offer, offer.end, new Fixed(0.0), level);
		check(result.tier() == Jobs.Tier.TRIUMPH && c.active.isEmpty() && agent.state() == Agent.State.READY, "the convoy got through: " + result.line());
		check(result.marks() > Jobs.stakeCents(escort, 1) && Bank.balance(OWNER) - before == result.marks(), "a triumph pays more than the stake");
		check(c.jobsDone == 1 && c.rep > 0 && agent.jobs == 1, "it counts for the company and the agent");

		// a chart
		ItemStack chart = Charts.item("minecraft:shipwreck", new BlockPos(120, 63, -340));
		check(Charts.describe(chart).contains("Shipwreck at X 120, Z -340"), "a Company Chart says where to go (" + Charts.describe(chart) + ")");
		check(Charts.find(level, "minecraft:no_such_thing", BlockPos.ZERO) == null, "charting something that doesn't exist finds nothing");
		BlockPos village = Charts.find(level, "minecraft:village_plains", BlockPos.ZERO);
		CompagnieMod.LOG.info("[smoke] the nearest plains village is at {}", village);

		// the strongbox
		c.strongbox.clear();
		Strongbox.add(c, Strongbox.make("cocoa_beans", 100));
		check(c.strongbox.size() == 2 && c.strongbox.get(0).getCount() == 64 && c.strongbox.get(1).getCount() == 36, "100 cocoa beans make two stacks");
		Strongbox.add(c, Strongbox.make("cocoa_beans", 20));
		check(c.strongbox.size() == 2 && c.strongbox.get(1).getCount() == 56, "and top up what's there");
		check(Strongbox.make("no_such_item", 5).isEmpty(), "spoils that don't exist in this game are skipped");
		before = Bank.balance(OWNER);
		for (int i = 0; i < Strongbox.MAX + 5; i++) {
			Strongbox.add(c, new ItemStack(Items.DIAMOND_SWORD));
		}
		check(c.strongbox.size() == Strongbox.MAX && Bank.balance(OWNER) > before, "a full strongbox sells the overflow");
		c.strongbox.clear();

		// the Ledger
		Loan loan = Ledger.roll(c, today, new Random(3));
		loan.gold = false;
		loan.amount = Bank.cents(1000);
		loan.interest = 0.2;
		c.loans.add(loan);
		before = Bank.balance(OWNER);
		check(Ledger.lend(c, loan, null, today) == null && loan.state() == Loan.State.ACTIVE && Bank.balance(OWNER) == before - Bank.cents(1000),
			"lending " + loan.borrower + " ₥1,000");
		loan.day = today;
		before = Bank.balance(OWNER);
		Ledger.dawn(c, today, new Fixed(0.99));
		check(!c.loans.contains(loan) && Bank.balance(OWNER) - before == Bank.cents(1200), "they paid it back with 20% interest");

		Loan bad = Ledger.roll(c, today, new Random(4));
		bad.gold = false;
		bad.amount = Bank.cents(500);
		bad.interest = 0.3;
		c.loans.add(bad);
		check(Ledger.lend(c, bad, null, today) == null, "lending to a bad risk");
		bad.day = today;
		Ledger.dawn(c, today, new Fixed(0.0));
		check(bad.state() == Loan.State.DEFAULTED, bad.borrower + " defaulted");
		check(Jobs.start(c, JobType.COLLECT_DEBT, null, Approach.FORCE, agent, 1, today, bad.id) == null, "sending a collector");
		bad.state(Loan.State.COLLECTING);
		Job collect = c.active.get(0);
		before = Bank.balance(OWNER);
		Jobs.resolve(c, collect, collect.end, new Fixed(0.0), level);
		check(!c.loans.contains(bad) && Bank.balance(OWNER) - before == Math.round(bad.owed() * 1.2), "the collector brought it all back, and then some");

		Loan worse = Ledger.roll(c, today, new Random(5));
		worse.gold = false;
		worse.amount = Bank.cents(1000);
		worse.interest = 0;
		worse.state(Loan.State.DEFAULTED);
		c.loans.add(worse);
		before = Bank.balance(OWNER);
		check(Ledger.sell(c, worse) == null && Bank.balance(OWNER) - before == Bank.cents(300) && !c.loans.contains(worse),
			"Leonard the Usurer buys a bad debt for 30%");
		Loan gold = Ledger.roll(c, today, new Random(6));
		gold.gold = true;
		gold.amount = 8;
		c.loans.add(gold);
		check(Ledger.lend(c, gold, null, today) != null, "a gold loan needs the ingots in hand");
		c.loans.remove(gold);

		// fates
		Agent doomed = Hiring.roll(c, new Random(7));
		doomed.traits.clear();
		c.agents.add(doomed);
		String fate = Jobs.fate(c, doomed, Approach.FORCE, Jobs.Tier.DISASTER, today, new Fixed(0.0));
		check(!c.agents.contains(doomed) && c.agentsLost == 1, "a disaster can cost an agent their life: " + fate);
		Agent caught = Hiring.roll(c, new Random(8));
		caught.traits.clear();
		c.agents.add(caught);
		Jobs.fate(c, caught, Approach.FORCE, Jobs.Tier.DISASTER, today, new Fixed(0.5));
		check(caught.state() == Agent.State.CAPTURED, "or their freedom");
		check(Hiring.payRansom(c, caught) == null && caught.state() == Agent.State.READY, "the ransom gets them out");
		Agent coward = Hiring.roll(c, new Random(9));
		coward.traits.clear();
		coward.traits.add(Trait.COWARD.name());
		c.agents.add(coward);
		Jobs.fate(c, coward, Approach.FORCE, Jobs.Tier.DISASTER, today, new Fixed(0.0));
		check(c.agents.contains(coward) && coward.state() == Agent.State.WOUNDED, "a coward runs for it and lives");

		// specials
		Job forge = new Job();
		forge.id = c.newId("j");
		forge.type = JobType.FORGE_A_CHARTER.id();
		c.board.add(forge);
		long rep = c.rep;
		check(Jobs.start(c, JobType.FORGE_A_CHARTER, forge, Approach.GUILE, caught, 1, today, "") == null, "forging a charter");
		Jobs.resolve(c, forge, forge.end, new Fixed(0.0), level);
		check(c.rep >= rep + 1600, "a forged charter is worth a lot of reputation (" + rep + " -> " + c.rep + ")");

		// ranks
		check(Rank.of(0) == Rank.CHARTER && Rank.of(Rank.VOC.rep) == Rank.VOC && Rank.CHARTER.next() == Rank.TRADING_HOUSE && Rank.VOC.next() == null,
			"the ranks, from Charter to VOC");
		c.rep = Rank.VOC.rep;
		c.board.clear();
		Companies.fillBoard(c, today, new Random(10));
		check(c.board.size() == Rank.VOC.board, "a VOC has a bigger board (" + c.board.size() + ")");

		// payday without money
		Bank.charge(OWNER, Bank.balance(OWNER));
		int staff = c.agents.size();
		Hiring.payday(c, today);
		check(c.agents.size() == staff - 1, "no wages: somebody walks out");
		Hiring.payday(c, today + 5);
		check(coward.state() == Agent.State.WOUNDED || !c.agents.contains(coward) || coward.state() == Agent.State.READY, "the wounded heal in time");

		// cargo: luxury goods and ships
		ItemStack bottle = new ItemStack(Items.PAPER);
		net.minecraft.nbt.CompoundTag spirit = new net.minecraft.nbt.CompoundTag();
		spirit.putString("proost", "spirit");
		bottle.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(spirit));
		ItemStack airship = new ItemStack(Items.PAPER);
		net.minecraft.nbt.CompoundTag ship = new net.minecraft.nbt.CompoundTag();
		ship.putString("blimey", "airship");
		airship.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(ship));
		check(Cargo.isLuxury(bottle) && !Cargo.isLuxury(new ItemStack(Items.PAPER)) && Cargo.isShip(airship) && !Cargo.isShip(bottle),
			"a sealed bottle is luxury cargo, an airship is a ship, plain paper is neither");
		check(!JobType.LUXURY_EXPORT.available() && !JobType.OIL_SURVEY.available(), "jobs that need Proost or Fossil Fool stay off the board without them");
		for (Job j : c.board) {
			check(j.type() != null && j.type().available(), "nothing on the board needs a missing mod", false);
		}
		Agent trader = Hiring.roll(c, new Random(11));
		trader.traits.clear();
		c.agents.add(trader);
		Bank.credit(OWNER, "SmokeTest", Bank.cents(10_000));
		Job export = new Job();
		export.id = c.newId("j");
		export.type = JobType.LUXURY_EXPORT.id();
		export.cargo = Bank.cents(1000);
		c.board.add(export);
		check(Jobs.start(c, JobType.LUXURY_EXPORT, export, Approach.COIN, trader, 1, today, "") == null, "shipping ₥1,000 of luxury goods abroad");
		before = Bank.balance(OWNER);
		Jobs.resolve(c, export, export.end, new Fixed(0.0), level);
		check(Bank.balance(OWNER) - before == Bank.cents(3200), "a triumph sells them for 320% (" + Bank.format(Bank.balance(OWNER) - before) + ")");
		Job charter = new Job();
		charter.id = c.newId("j");
		charter.type = JobType.CHARTER_SHIP.id();
		charter.cargoItem = Cargo.encode(level, airship);
		c.board.add(charter);
		check(charter.cargoItem != null && Jobs.start(c, JobType.CHARTER_SHIP, charter, Approach.COIN, trader, 1, today, "") == null, "chartering out an airship");
		c.strongbox.clear();
		before = Bank.balance(OWNER);
		Jobs.resolve(c, charter, charter.end, new Fixed(0.0), level);
		check(Bank.balance(OWNER) - before == Bank.cents(3000) && c.strongbox.size() == 1 && Cargo.isShip(c.strongbox.get(0)),
			"the charter pays and the airship comes home to the strongbox");
		c.strongbox.clear();

		// a Company House (from Colonycraft)
		int slots = c.slots();
		int cap = c.maxAgents();
		long wages = c.dailyWages();
		Object house = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get(CompagnieApi.HOUSE);
		check(house instanceof java.util.function.BiConsumer<?, ?>, "the Company House hook is published");
		@SuppressWarnings("unchecked")
		java.util.function.BiConsumer<UUID, Integer> setHouse = (java.util.function.BiConsumer<UUID, Integer>) house;
		setHouse.accept(OWNER, 2);
		check(c.house == 2 && c.slots() == slots + 1 && c.maxAgents() == cap + 2 && (wages == 0 || c.dailyWages() < wages),
			"a tier 2 Company House: a job slot, two more agents, cheaper wages");
		setHouse.accept(OWNER, 0);
		check(c.house == 0 && c.slots() == slots, "and without it, back to normal");

		// saving and loading
		c.strongbox.add(new ItemStack(Items.EMERALD, 7));
		int agents = c.agents.size();
		Companies.save();
		Companies.load(server);
		Company loaded = Companies.get(OWNER);
		check(loaded != null && loaded.agents.size() == agents && loaded.rep == c.rep, "the company survives a save and load");
		check(loaded.strongbox.size() == 1 && loaded.strongbox.get(0).is(Items.EMERALD) && loaded.strongbox.get(0).getCount() == 7,
			"and so does its strongbox");
		check(Desks.at(level, DESK) != null, "and the desk");
		Companies.dawn(Companies.lastDay + 1);
		check(true, "a dawn runs");
		Desks.remove(level, DESK);
		Companies.forgetAll();
		Companies.save();
	}

	private static void check(boolean ok, String what) {
		check(ok, what, true);
	}

	private static void check(boolean ok, String what, boolean log) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		if (log) {
			CompagnieMod.LOG.info("[smoke] ok: {}", what);
		}
	}
}
