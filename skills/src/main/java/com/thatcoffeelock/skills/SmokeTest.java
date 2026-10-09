package com.thatcoffeelock.skills;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dskills.smokeTest=true (CI). Boots a real server and checks: the XP curve, every perk and passive
 * text, every icon exists in this Minecraft version, placed-block tracking, that the damage mixin runs, that a real
 * brewing stand brews through the brewing mixin, the Market hook, and that profiles survive a save and load.
 */
final class SmokeTest {
	private static final BlockPos ORE = new BlockPos(0, 101, 0);
	private static final BlockPos BRICKS = new BlockPos(2, 101, 0);
	private static final BlockPos STAND = new BlockPos(4, 101, 0);

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -16 -16 16 16");
			SkillsMod.later(100, () -> step(server, () -> start(server, level)));
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void waitFor(MinecraftServer server, String what, int maxTicks, BooleanSupplier condition, Step then) {
		poll(server, what, maxTicks, 0, condition, then);
	}

	private static void poll(MinecraftServer server, String what, int maxTicks, int waited, BooleanSupplier condition, Step then) {
		SkillsMod.later(5, () -> step(server, () -> {
			if (condition.getAsBoolean()) {
				SkillsMod.LOG.info("[smoke] ok: {} (after {} ticks)", what, waited + 5);
				then.run();
			} else if (waited + 5 >= maxTicks) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for " + what);
			} else {
				poll(server, what, maxTicks, waited + 5, condition, then);
			}
		}));
	}

	private static void start(MinecraftServer server, ServerLevel level) {
		curve();
		texts();
		icons();
		placed(level);
		hook();
		store(server);

		Cmd.run(level, "fill -8 100 -8 8 106 8 minecraft:air");
		Cmd.run(level, "fill -8 99 -8 8 99 8 minecraft:stone");
		int before = Fighting.calls;
		Cmd.run(level, "summon minecraft:zombie 0 100 4 {NoAI:1b,PersistenceRequired:1b,Tags:[\"skills_smoke\"]}");
		Cmd.run(level, "damage @e[type=minecraft:zombie,tag=skills_smoke,limit=1] 2");
		check(Fighting.calls > before, "the damage mixin ran (" + (Fighting.calls - before) + " calls)");
		Cmd.run(level, "kill @e[tag=skills_smoke]");
		log("damage mixin");

		Cmd.setblock(level, STAND, "minecraft:brewing_stand");
		String at = Cmd.block(STAND);
		Cmd.run(level, "item replace block " + at + " container.0 with minecraft:potion[minecraft:potion_contents={potion:\"minecraft:water\"}]");
		Cmd.run(level, "item replace block " + at + " container.3 with minecraft:nether_wart");
		Cmd.run(level, "item replace block " + at + " container.4 with minecraft:blaze_powder");
		check(level.getBlockEntity(STAND) instanceof Container c && !c.getItem(3).isEmpty(), "brewing stand filled");
		int brewsBefore = Brewing.brews;
		waitFor(server, "the brewing stand brewed through the mixin", 900, () -> Brewing.brews > brewsBefore, () -> {
			Container stand = (Container) level.getBlockEntity(STAND);
			check(stand.getItem(0).is(Items.POTION), "the bottle is still a potion after brewing");
			SkillsMod.LOG.info("SKILLS SMOKE TEST PASSED");
			server.halt(false);
		});
	}

	private static void curve() {
		check(Skill.levelFor(0) == 0, "0 XP is level 0");
		check(Skill.levelFor(99.9) == 0 && Skill.levelFor(100) == 1, "level 1 at 100 XP");
		for (int n = 0; n <= Skill.MAX_LEVEL; n++) {
			check(Skill.levelFor(Skill.totalFor(n)) == n, "total XP for level " + n + " gives level " + n);
		}
		check(Skill.levelFor(1e12) == Skill.MAX_LEVEL, "levels stop at 100");
		double total = Skill.totalFor(Skill.MAX_LEVEL);
		check(total > 150_000 && total < 200_000, "level 100 takes 150k-200k XP (got " + (long) total + ")");
		check(Skill.progress(Skill.totalFor(10) + Skill.xpForLevel(11) / 2) > 0.49, "progress halfway through a level");
		log("XP curve: level 10 = " + (long) Skill.totalFor(10) + ", level 50 = " + (long) Skill.totalFor(50) + ", level 100 = " + (long) total);
	}

	private static void texts() {
		for (Skill skill : Skill.values()) {
			for (int level : new int[] {0, 1, 37, 100}) {
				check(!skill.passiveText(level).isEmpty(), skill.id() + " passive text");
			}
			check(Perk.of(skill).size() == 3, skill.id() + " has 3 perks");
		}
		for (Perk perk : Perk.values()) {
			for (int rank = 0; rank <= Perk.MAX_RANK; rank++) {
				String text = perk.describe(rank);
				check(!text.isBlank() && !text.contains("%s"), perk.id() + " rank " + rank + " text: " + text);
			}
		}
		log("texts, e.g. " + Perk.EXECUTIONER.describe(5) + " / " + Perk.VEIN_MINER.describe(3) + " / " + Skill.MINING.passiveText(100));
	}

	private static void icons() {
		for (Skill skill : Skill.values()) {
			check(Skills.item(skill.icon) != Items.PAPER, "icon " + skill.icon + " exists");
		}
		for (Perk perk : Perk.values()) {
			check(Skills.item(perk.icon) != Items.PAPER, "icon " + perk.icon + " exists");
		}
		for (String pane : new String[] {"lime_stained_glass_pane", "yellow_stained_glass_pane", "red_stained_glass_pane",
			"gray_stained_glass_pane", "black_stained_glass_pane", "grindstone", "nether_star", "book", "arrow", "potion"}) {
			check(Skills.item(pane) != Items.PAPER, "menu item " + pane + " exists");
		}
		log("icons");
	}

	private static void placed(ServerLevel level) {
		Cmd.setblock(level, ORE, "minecraft:iron_ore");
		Cmd.setblock(level, BRICKS, "minecraft:stone_bricks");
		check(Gathering.tracked(level.getBlockState(ORE)), "iron ore is tracked");
		check(!Gathering.tracked(level.getBlockState(BRICKS)), "stone bricks aren't tracked");
		SkillsMod.placed(level, ORE);
		SkillsMod.placed(level, BRICKS);
		check(Placed.isPlaced(level, ORE), "placed ore remembered");
		check(!Placed.isPlaced(level, BRICKS), "bricks not remembered");
		check(Placed.remove(level, ORE), "placed ore forgotten on break");
		check(!Placed.remove(level, ORE), "and only once");
		Cmd.setblock(level, ORE, "minecraft:air");
		Cmd.setblock(level, BRICKS, "minecraft:air");
		log("placed-block tracking");
	}

	@SuppressWarnings("unchecked")
	private static void hook() {
		Object sell = FabricLoader.getInstance().getObjectShare().get(Trading.MARKET_SELL);
		Object buy = FabricLoader.getInstance().getObjectShare().get(Trading.MARKET_BUY);
		check(sell instanceof java.util.function.BiFunction && buy instanceof java.util.function.BiFunction, "Market hooks published");
		log("market hooks");
		Object bonus = FabricLoader.getInstance().getObjectShare().get(Wildcatting.BONUS);
		Object xp = FabricLoader.getInstance().getObjectShare().get(Wildcatting.XP);
		check(bonus instanceof java.util.function.BiFunction && xp instanceof java.util.function.BiConsumer, "Fossil Fool hooks published");
		UUID oilman = UUID.fromString("00000000-0000-0000-0000-0000000011ee");
		SkillsStore.Profile profile = SkillsStore.create(oilman, "Oilman");
		profile.xp.put(Skill.WILDCATTING.id(), Skill.totalFor(40));
		profile.perks.put(Perk.ROUGHNECK.id(), 2);
		profile.perks.put(Perk.DOWSER.id(), 1);
		var bonusFn = (java.util.function.BiFunction<UUID, String, Double>) bonus;
		check(Math.abs(bonusFn.apply(oilman, "fuel") - 0.10) < 1e-9, "Wildcatting 40 burns fuel 10% better");
		check(Math.abs(bonusFn.apply(oilman, "speed") - 0.12) < 1e-9 && bonusFn.apply(oilman, "dowse") == 8.0, "Roughneck 2, Dowser 1");
		check(bonusFn.apply(UUID.randomUUID(), "fuel") == 0.0, "strangers get no bonus");
		((java.util.function.BiConsumer<UUID, Double>) xp).accept(oilman, 500.0);
		check(profile.xp(Skill.WILDCATTING) > Skill.totalFor(40), "offline owners still earn Wildcatting XP");
		log("fossil fool hooks");

		// the general skills API every other mod uses
		Object apiBonus = FabricLoader.getInstance().getObjectShare().get(SkillsApi.BONUS);
		Object apiXp = FabricLoader.getInstance().getObjectShare().get(SkillsApi.XP);
		check(apiBonus instanceof java.util.function.BiFunction && apiXp instanceof java.util.function.BiConsumer, "skills API published");
		var api = (java.util.function.BiFunction<UUID, String, Double>) apiBonus;
		profile.xp.put(Skill.ARTILLERY.id(), Skill.totalFor(50));
		profile.perks.put(Perk.POWDER_MONKEY.id(), 3);
		check(Math.abs(api.apply(oilman, "artillery/passive") - 0.15) < 1e-9, "Artillery 50 reloads 15% faster");
		check(Math.abs(api.apply(oilman, "artillery/powder_monkey") - 0.18) < 1e-9, "Powder Monkey 3 = 18%");
		check(api.apply(oilman, "artillery/haggler") == 0.0 && api.apply(oilman, "nonsense") == 0.0, "wrong keys give nothing");
		((java.util.function.BiConsumer<UUID, java.util.Map.Entry<String, Double>>) apiXp).accept(oilman, java.util.Map.entry("governance", 300.0));
		check(profile.level(Skill.GOVERNANCE) >= 2, "offline Governance XP is banked");
		check(Skill.values().length == 20 && Perk.values().length == 60, "20 skills, 60 perks");
		profile.xp.put(Skill.PILOTEERING.id(), Skill.totalFor(100));
		profile.perks.put(Perk.ACE.id(), 5);
		check(Math.abs(api.apply(oilman, "piloteering/passive") - 0.30) < 1e-9, "Piloteering 100 burns 30% less diesel");
		check(Math.abs(api.apply(oilman, "piloteering/ace") - 0.20) < 1e-9, "Ace 5 = +20% airship speed");
		for (Skill skill : Skill.values()) {
			check(Perk.of(skill).size() == 3, skill.id() + " has 3 perks");
		}
		check(Fighting.ILLAGERS.contains("pillager") && Fighting.ILLAGERS.contains("evoker") && !Fighting.ILLAGERS.contains("zombie"),
			"illagers are illagers, zombies aren't");
		net.minecraft.world.SimpleContainer box = new net.minecraft.world.SimpleContainer(3);
		box.setItem(1, new net.minecraft.world.item.ItemStack(Items.BREAD, 5));
		check(Treasure.grow(box) && box.getItem(1).getCount() == 6, "Treasure Hunting's bonus item grows a stack");
		log("skills API and the five new skills");
	}

	private static void store(MinecraftServer server) {
		UUID id = UUID.fromString("00000000-0000-0000-0000-00000000beef");
		SkillsStore.Profile profile = SkillsStore.create(id, "Smokey");
		profile.xp.put(Skill.MINING.id(), Skill.totalFor(42) + 5);
		profile.perks.put(Perk.PROSPECTOR.id(), 3);
		check(profile.pointsEarned(Skill.MINING) == 4 && profile.pointsFree(Skill.MINING) == 1, "perk points: 4 earned, 1 free at level 42");
		SkillsStore.save();
		SkillsStore.load(server);
		SkillsStore.Profile loaded = SkillsStore.of(id);
		check(loaded != null && loaded.level(Skill.MINING) == 42 && loaded.rank(Perk.PROSPECTOR) == 3 && "Smokey".equals(loaded.name),
			"profile survives a save and load");
		log("save and load");
	}

	private static void log(String what) {
		SkillsMod.LOG.info("[smoke] ok: {}", what);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		SkillsMod.LOG.error("[smoke] SKILLS SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
	}
}
