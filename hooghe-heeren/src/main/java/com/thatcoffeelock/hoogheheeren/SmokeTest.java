package com.thatcoffeelock.hoogheheeren;

import java.util.Random;
import java.util.UUID;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Only runs with -Dhoogheheeren.smokeTest=true (CI). Boots a real server and runs a court without a player: founds a
 * company and a table of rivals, gives gifts, signs and tears up pacts, makes a house pay its regiments (and watches
 * them desert when it can't), sends agents on errands (one triumphs, one is caught), goes to war and wins it, lets
 * the other houses fight each other, buries a lord and replaces a bankrupt house, builds a seat in the world, puts a
 * lord in it, turns out its garrison and cuts one down, invites a guest and bars the doors, runs ten dawns, and
 * saves and loads it all.
 */
final class SmokeTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000beee");

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

		@Override
		public boolean nextBoolean() {
			return false;
		}
	}

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		HeerenMod.later(60, () -> {
			try {
				test(server.overworld());
			} catch (Throwable t) {
				HeerenMod.LOG.error("HOOGHE HEEREN SMOKE TEST FAILED", t);
				server.halt(false);
				return;
			}
			// the seat's chunks were only just force-loaded: give their entities a few seconds to become reachable
			HeerenMod.later(100, () -> {
				try {
					inTheWorld(server.overworld());
					HeerenMod.LOG.info("HOOGHE HEEREN SMOKE TEST PASSED");
				} catch (Throwable t) {
					HeerenMod.LOG.error("HOOGHE HEEREN SMOKE TEST FAILED", t);
				}
				server.halt(false);
			});
		});
	}

	private static void test(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long today = Courts.today();
		Bank.credit(OWNER, "SmokeTest", Bank.cents(100000));

		// rivals are optional: a company alone has none, whatever dawns pass
		CompagnieHooks.found(OWNER, "SmokeTest");
		Courts.dawn(OWNER, today, new Random(1));
		check(Courts.get(OWNER) == null, "a company that hasn't taken a seat has no rivals");

		// a court
		Court c = Courts.of(OWNER, "SmokeTest");
		check(CompagnieHooks.exists(OWNER), "taking a seat at court keeps the company");
		check(c.rivals.size() == 3, "a Charter sits with three houses (" + c.rivals.size() + ")");
		for (Rival r : c.rivals) {
			check(!r.house.isEmpty() && !r.first.isEmpty() && !r.seatName.isEmpty(), "every house has a name: " + r.name() + " of " + r.seatName);
			check(r.treasury > 0 && r.income > 0 && r.regiments > 0, "and a treasury, an income and regiments", false);
			double d = Math.sqrt((double) r.seatX * r.seatX + (double) r.seatZ * r.seatZ);
			check(d >= 690 && d <= 1410, "and a seat out in the world (" + Math.round(d) + " blocks away)", false);
		}
		check(!c.rivals.get(0).colour.equals(c.rivals.get(1).colour) && !c.rivals.get(1).colour.equals(c.rivals.get(2).colour),
			"no two houses at the table wear the same colours");
		check(Court.Tier.of(-80) == Court.Tier.FEUD && Court.Tier.of(0) == Court.Tier.NEUTRAL && Court.Tier.of(75) == Court.Tier.SWORN,
			"opinion tiers run from Blood Feud to Sworn Ally");
		check(c.reputation().equals("A Newcomer at Court"), "a new player is a Newcomer at Court");

		Rival a = c.rivals.get(0);
		Rival b = c.rivals.get(1);
		Rival d3 = c.rivals.get(2);

		// gifts
		int before = c.opinion(a, today);
		long purse = a.treasury;
		check(Diplomacy.gift(c, a, true, false, today) == null, "a big gift is sent");
		check(a.treasury == purse + Diplomacy.bigGift(c), "and lands in their treasury");
		check(c.opinion(a, today) > before, "and warms them to you (" + before + " -> " + c.opinion(a, today) + ")");
		Rival.Memory m = a.memories.get(a.memories.size() - 1);
		check(m.now(today) > 0 && m.now(today + 100) == 0, "and the memory fades in time");

		// a trade pact, by dispatch
		Dispatches.send(c, Court.Dispatch.Kind.TRADE_OFFER, a, Ai.pactIncome(c), "", today);
		check(c.dispatches.size() == 1 && Dispatches.options(c, c.dispatches.get(0)).size() == 2, "a trade offer arrives, with two answers");
		check(Dispatches.answer(c, c.dispatches.get(0), 0, today, false) == null && a.tradePact && c.dispatches.isEmpty(), "and is accepted");
		long bal = Bank.balance(OWNER);
		Ai.economy(c, a, today + 1, new Fixed(0.5));
		check(Bank.balance(OWNER) == bal + Ai.pactIncome(c), "the pact pays at dawn");
		check(Diplomacy.cancelTrade(c, a, today) == null && !a.tradePact && c.honour == 46, "tearing it up costs Honour (" + c.honour + ")");

		// the same rules: no treasury, no army
		b.treasury = 0;
		b.income = 0;
		b.regiments = 5;
		b.tribute = 0;
		Ai.economy(c, b, today + 1, new Fixed(0.5));
		check(b.regiments == 0, "a house that can't pay its regiments loses them");
		check(Diplomacy.raise(c, 4) == null && c.regiments == 4, "you raise four regiments");
		long all = Bank.balance(OWNER);
		Bank.seize(OWNER, all);
		Bank.credit(OWNER, "SmokeTest", Courts.UPKEEP * 2);
		Courts.payRegiments(c);
		check(c.regiments == 2, "and when you can only pay two, two desert (" + c.regiments + ")");
		Bank.credit(OWNER, "SmokeTest", all);

		// errands, by Compagnie agents
		String spy = CompagnieHooks.addAgent(OWNER, "Smoke Spy", 5, 5, 9);
		check(Covert.start(c, d3, Covert.Type.SPY, 2, spy, "", today) == null && c.ops.size() == 1, "a spy is sent to " + d3.House());
		check(CompagnieHooks.agents(OWNER).stream().anyMatch(x -> x.id().equals(spy) && x.state().equals("AWAY") && x.heldBy().equals(HeerenMod.ID)),
			"the agent is away on the Heeren's business");
		check(Covert.start(c, d3, Covert.Type.SPY, 2, spy, "", today) != null, "and can't be sent twice");
		Covert.resolve(c, c.ops.get(0), today + 2, new Fixed(0.0));
		check(d3.known(today + 2) && c.ops.isEmpty(), "a triumph: their letters are an open book");
		check(CompagnieHooks.agents(OWNER).stream().anyMatch(x -> x.id().equals(spy) && x.state().equals("READY")), "and the agent is home");
		int honour = c.honour;
		check(Covert.start(c, d3, Covert.Type.STEAL, 2, spy, "", today) == null, "a burglar is sent");
		Covert.resolve(c, c.ops.get(0), today + 3, new Fixed(0.999));
		check(c.honour < honour && d3.memories.stream().anyMatch(x -> x.text.startsWith("Caught your agent")), "a disaster: it's traced back to you");

		// war
		Rival w = a;
		w.regiments = 2;
		c.regiments = 6;
		check(Diplomacy.declareWar(c, w, today) == null && w.war, "war is declared on " + w.House());
		check(Diplomacy.declareWar(c, w, today) != null, "and can't be declared twice");
		int score = w.warScore;
		Wars.skirmish(c, w, today + 1, new Fixed(0.5), 1.0, false);
		check(w.warScore > score, "the bigger army wins the skirmish (score " + Wars.signed(w.warScore) + ")");
		w.warScore = 70;
		w.lastPeaceTalk = today - 10;
		Wars.peaceTalks(c, w, today + 1);
		check(c.dispatches.stream().anyMatch(x -> x.kind() == Court.Dispatch.Kind.PEACE_OFFER), "a beaten house sues for peace");
		Court.Dispatch peace = c.dispatches.stream().filter(x -> x.kind() == Court.Dispatch.Kind.PEACE_OFFER).findFirst().orElseThrow();
		check(Dispatches.answer(c, peace, 0, today + 1, false) == null && !w.war && c.warsWon == 1, "reparations accepted: the war is won");

		// the other houses
		Wars.rivalWar(c, b, d3, today);
		check(b.wars.contains(d3.id) && d3.wars.contains(b.id), b.House() + " and " + d3.House() + " go to war without you");

		// rise and fall
		String house = d3.house;
		String firstName = d3.first;
		Ai.fall(c, d3, today, new Random(7), true, "Smoke test:");
		check(d3.house.equals(house) && d3.generation == 2, "an heir takes the seat (" + firstName + " -> " + d3.first + ")");
		String oldId = b.id;
		int seatX = b.seatX;
		Ai.fall(c, b, today, new Random(7), false, "Smoke test:");
		Rival fresh = c.rivals.get(1);
		check(!fresh.id.equals(oldId) && fresh.seatX == seatX && c.rival(oldId) == null, "a bankrupt house is replaced by a newcomer, in the same seat");
		check(c.rivals.stream().noneMatch(x -> x.wars.contains(oldId) || x.relations.containsKey(oldId)), "and the old house is forgotten");

		// ten dawns of whatever they get up to
		for (int i = 1; i <= 10; i++) {
			Courts.dawn(c, today + 10 + i, new Random(i));
		}
		check(c.rivals.size() == 3, "ten dawns pass, and the table is still full");

		// a seat in the world
		Rival s = c.rivals.get(2);
		s.seatX = 600;
		s.seatZ = 600;
		s.built = false;
		Cmd.run(level, "forceload add 570 570 630 630");
		check(Builder.loaded(level, 600, 600), "the seat's chunks are loaded");
		Builder.build(level, s);
		BlockPos chest = Builder.at(s, Builder.CHEST);
		check(s.built && level.getBlockState(chest).is(Blocks.CHEST), s.seatName + " is built, with its treasury chest");
		check(level.getBlockState(Builder.at(s, new BlockPos(-Builder.R, 2, 0))).is(Blocks.STONE_BRICKS), "and walls");
		check(s.dressed.equals(s.colour), "in the house's colours");
	}

	/** Lords, soldiers and guests, once the seat's chunks are fully loaded; then saving and loading. */
	private static void inTheWorld(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long today = Courts.today();
		Court c = Courts.get(OWNER);
		check(c != null, "the court is still there");
		Rival s = c.rivals.get(2);
		int honour;
		BlockPos lordAt = Builder.at(s, Builder.LORD);
		LivingEntity lord = Figures.ensure(level, Seats.lordMark(c, s), lordAt.getX() + 0.5, lordAt.getY(), lordAt.getZ() + 0.5, 0, Seats.lordLook(c, s));
		check(lord != null && Seats.lordMark(c, s).equals(Figures.mark(lord)), "the lord is at home in the great hall");
		Figures.remove(Seats.lordMark(c, s));

		// its garrison, at war
		s.regiments = 2;
		if (!s.war) {
			Wars.declare(c, s, true, today + 21);
		}
		Battles.garrison(c, s, level, level.getGameTime());
		int garrison = Battles.garrisonLeft(c, s);
		check(garrison == 4, "the garrison turns out (" + garrison + ")");
		Soldiers.Soldier one = Soldiers.in(Battles.seatKey(c, s), Soldiers.THEIRS).get(0);
		check(one.body != null && one.brain.isInvisible(), "a soldier is an invisible brain with a mannequin body");
		check(Soldiers.foe(one.body) && Soldiers.foe(one.brain)
			&& net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get(HeerenApi.FOE) instanceof java.util.function.Predicate<?>,
			"Sellswords see the rival's men as foes");
		one.brain.hurtServer(level, level.damageSources().generic(), 1000f);
		check(Battles.garrisonLeft(c, s) == garrison - 1, "and one of them falls");
		Battles.endGarrison(c, s);
		check(Battles.garrisonLeft(c, s) == 0, "and the rest march home");

		// the hall
		check(Hall.isItem(Hall.item()) && !Hall.isItem(new ItemStack(Items.BELL)), "an Audience Bell is one, a plain bell isn't");
		c.hall = true;
		c.hallDim = Cmd.dimId(level);
		c.hallX = 600;
		c.hallY = s.seatY + 1;
		c.hallZ = 605;
		Rival guest = c.rivals.get(0);
		guest.war = false;
		guest.memories.clear();
		guest.memories.add(new Rival.Memory("Smoke test", 40, today + 21, 0));
		check(Hall.invite(c, guest, today + 21) == null && c.guest.equals(guest.id), guest.name() + " accepts an invitation");
		check(Hall.invite(c, c.rivals.get(1), today + 21) != null, "and nobody else fits in the hall meanwhile");
		honour = c.honour;
		Hall.barDoors(c, guest, today + 21);
		check(c.guestHeld && guest.war && c.honour < honour && c.rivals.get(1).memories.stream().anyMatch(x -> x.fade == 0),
			"the doors are barred: war, dishonour, and nobody forgets");
		Hall.ransom(c, guest, today + 21);
		check(c.guest.isEmpty() && !c.guestHeld, "the prisoner is ransomed home");

		// saving and loading
		int memories = c.rivals.get(1).memories.size();
		Courts.save();
		Courts.load(server);
		Court loaded = Courts.get(OWNER);
		check(loaded != null && loaded.rivals.size() == 3 && loaded.rivals.get(1).memories.size() == memories, "the court survives a save and load");
		check(loaded.rivals.get(2).built && loaded.honour == c.honour, "with its seats and its standing");

		// leaving the table: not at war, then gone
		Court back = Courts.get(OWNER);
		Rival enemy = back.rivals.get(0);
		if (!enemy.war) {
			Wars.declare(back, enemy, true, today + 22);
		}
		check(Courts.leave(back) != null && Courts.get(OWNER) != null, "nobody walks away from a war");
		for (Rival r : back.rivals) {
			if (r.war) {
				Wars.end(back, r, Wars.Outcome.WHITE, today + 22);
			}
		}
		check(Courts.leave(back) == null && Courts.get(OWNER) == null, "at peace, you can leave the table");
		Courts.dawn(OWNER, today + 23, new Random(2));
		check(Courts.get(OWNER) == null, "and the houses don't come back on their own");

		Cmd.run(level, "forceload remove 570 570 630 630");
		Courts.forgetAll();
		Courts.save();
	}

	private static void check(boolean ok, String what) {
		check(ok, what, true);
	}

	private static void check(boolean ok, String what, boolean log) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		if (log) {
			HeerenMod.LOG.info("[smoke] ok: {}", what);
		}
	}
}
