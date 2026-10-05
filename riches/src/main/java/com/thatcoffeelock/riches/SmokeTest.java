package com.thatcoffeelock.riches;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Only runs with -Driches.smokeTest=true (CI). Boots a real server and checks the relic catalogue, a vault pile that
 * follows a real Market balance, a vault door that opens and swings shut, display cases and pedestals with their
 * floating items and plaques, uniqueness, the collection reward, loot-chest detection, and a save and load.
 */
final class SmokeTest {
	private static final UUID SCROOGE = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
	private static final BlockPos LEDGER = new BlockPos(0, 101, 0);
	private static final BlockPos DOOR = new BlockPos(8, 101, 0);
	private static final BlockPos CHEST = new BlockPos(-8, 101, 0);
	/** Gold columns counted in the first pile, and after it grew. */
	private static final int[] PARTS = new int[2];

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -32 -32 32 32");
			RichesMod.later(100, () -> step(server, () -> start(server, level)));
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
		RichesMod.later(5, () -> step(server, () -> {
			if (condition.getAsBoolean()) {
				RichesMod.LOG.info("[smoke] ok: {} (after {} ticks)", what, waited + 5);
				then.run();
			} else if (waited + 5 >= maxTicks) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for " + what);
			} else {
				poll(server, what, maxTicks, waited + 5, condition, then);
			}
		}));
	}

	private static void start(MinecraftServer server, ServerLevel level) {
		RichesConfig.get().doorOpenTicks = 20;
		Cmd.run(level, "fill -12 100 -12 12 100 12 minecraft:stone");
		Cmd.run(level, "fill -12 101 -12 12 108 12 minecraft:air");
		catalogue();
		prices();
		heights();

		// a vault: a ledger owned by someone with a quarter of a million Marks
		Bank.credit(SCROOGE, "Scrooge", Bank.cents(250_000) - Bank.balance(SCROOGE));
		Cmd.run(level, "setblock 0 101 0 minecraft:lodestone");
		Interactions.onPlaced(level, null, LEDGER, RichesItems.ledger());
		Places.Vault vault = Places.VAULTS.get(Places.key(level, LEDGER));
		check(vault != null, "the vault ledger is remembered");
		vault.owner = SCROOGE.toString();
		vault.ownerName = "Scrooge";
		vault.drawn = Double.NaN;
		Cmd.run(level, "setblock 2 101 2 minecraft:stone");
		waitFor(server, "the gold pile is drawn", 200, () -> vault.drawn > 0, () -> {
			check(Math.abs(vault.drawn - Vaults.height(Bank.cents(250_000))) < 0.001, "the pile is as tall as the balance says (" + vault.drawn + ")");
			int parts = pileParts(level, vault);
			check(parts >= 20, "the pile has gold columns (" + parts + ")");
			PARTS[0] = parts;
			// richer: taller pile
			Bank.credit(SCROOGE, "Scrooge", Bank.cents(750_000));
			Vaults.draw(level, vault, Vaults.balanceHeight(vault));
			check(vault.drawn > Vaults.height(Bank.cents(250_000)), "a million Marks makes a taller pile (" + vault.drawn + ")");
			PARTS[1] = pileParts(level, vault);
			check(PARTS[1] == PARTS[0], "redrawing replaces the pile instead of adding a second one");
			log("vault pile");
			door(server, level);
		});
	}

	private static void door(MinecraftServer server, ServerLevel level) {
		Cmd.run(level, "setblock 8 101 0 minecraft:iron_door[half=lower]");
		Cmd.run(level, "setblock 8 102 0 minecraft:iron_door[half=upper]");
		Interactions.onPlaced(level, null, DOOR, RichesItems.door());
		Places.Door door = Places.doorAt(level, DOOR.above());
		check(door != null && door.pos.equals(DOOR), "the vault door is found from its upper half too");
		Vaults.open(level, door);
		check(Vaults.isOpen(level, door), "the vault door opens");
		check(Vaults.isOpen(level, new Places.Door(door.dimension, DOOR.above())), "both halves open");
		waitFor(server, "the vault door swings shut by itself", 200, () -> !Vaults.isOpen(level, door), () -> {
			log("vault door");
			showcases(server, level);
		});
	}

	private static void showcases(MinecraftServer server, ServerLevel level) {
		// six cases with the whole Royal Collection, all Scrooge's
		List<Relic> royal = Relic.Collection.ROYAL.relics();
		long before = Bank.balance(SCROOGE);
		for (int i = 0; i < royal.size(); i++) {
			BlockPos pos = new BlockPos(-6 + 2 * i, 101, 6);
			boolean pedestal = i % 2 == 1;
			Cmd.run(level, "setblock " + pos.getX() + " 101 6 " + (pedestal ? "minecraft:quartz_pillar" : "minecraft:glass"));
			Interactions.onPlaced(level, null, pos, pedestal ? RichesItems.pedestal() : RichesItems.displayCase());
			Places.Showcase s = Places.SHOWCASES.get(Places.key(level, pos));
			check(s != null && s.kind == (pedestal ? Places.Kind.PEDESTAL : Places.Kind.CASE), "case " + i + " is remembered");
			s.owner = SCROOGE.toString();
			s.ownerName = "Scrooge";
			Showcases.put(level, s, RichesItems.relic(royal.get(i)), "Scrooge");
			check(s.display != null && level.getEntity(s.display) != null, "the item floats in case " + i);
			if (i < royal.size() - 1) {
				Relics.checkCollections(level, s.owner, s.ownerName);
				check(Bank.balance(SCROOGE) == before, "no reward for an incomplete collection");
			}
		}
		Places.Showcase last = Places.SHOWCASES.get(Places.key(level, new BlockPos(-6 + 2 * (royal.size() - 1), 101, 6)));
		Relics.checkCollections(level, last.owner, last.ownerName);
		long reward = Bank.cents(RichesConfig.get().collectionReward);
		check(Bank.balance(SCROOGE) == before + reward, "the full Royal Collection pays out once");
		Relics.checkCollections(level, last.owner, last.ownerName);
		check(Bank.balance(SCROOGE) == before + reward, "and only once");
		check(Showcases.plaque(last).size() == 3 && Showcases.plaque(last).get(1)[0].contains("Royal Collection"), "a relic's plaque names its collection");
		ItemStack taken = Showcases.take(level, last);
		check(RichesItems.relicOf(taken) == royal.get(royal.size() - 1) && last.item.isEmpty() && last.display == null, "taking it back out");
		Showcases.put(level, last, taken, "Scrooge");
		log("display cases, pedestals and the collection reward");

		// uniqueness
		Relic tooth = Relic.DRAGON_TOOTH;
		check(Relics.available(tooth), "the dragon's tooth is still out there");
		Relics.record(tooth, SCROOGE.toString(), "Scrooge");
		check(!Relics.available(tooth), "once found, it's gone for everyone else");
		RichesConfig.get().uniqueRelics = false;
		check(Relics.available(tooth), "unless relics aren't unique");
		RichesConfig.get().uniqueRelics = true;
		log("one of a kind");

		// a structure chest still holding its loot table is spotted
		Cmd.run(level, "setblock -8 101 0 minecraft:chest{LootTable:\"minecraft:chests/simple_dungeon\"}");
		check(Relics.lootKey(level, CHEST).contains("simple_dungeon"), "an unopened loot chest is spotted (" + Relics.lootKey(level, CHEST) + ")");
		// setblock skips a chest that's already a chest, so clear it first
		Cmd.run(level, "setblock -8 101 0 minecraft:air");
		Cmd.run(level, "setblock -8 101 0 minecraft:chest");
		check(Relics.lootKey(level, CHEST).isEmpty(), "a plain chest isn't");
		log("loot chests");
		saveAndLoad(server, level);
	}

	private static void saveAndLoad(MinecraftServer server, ServerLevel level) {
		int vaults = Places.VAULTS.size();
		int doors = Places.DOORS.size();
		int shows = Places.SHOWCASES.size();
		int found = Relics.FOUND.size();
		Store.save();
		Store.load(server);
		check(Places.VAULTS.size() == vaults && Places.DOORS.size() == doors && Places.SHOWCASES.size() == shows && Relics.FOUND.size() == found,
			"vaults, doors, cases and finds survive a save and load");
		check(!Relics.available(Relic.DRAGON_TOOTH), "the dragon's tooth is still found");
		check(Relics.REWARDED.contains(SCROOGE + ":ROYAL"), "the reward is remembered");
		Places.Showcase first = Places.SHOWCASES.get(Places.key(level, new BlockPos(-6, 101, 6)));
		check(first != null && RichesItems.relicOf(first.item) == Relic.ILLAGER_CROWN && "Scrooge".equals(first.shownBy), "the crown is still on show");
		Places.Vault vault = Places.VAULTS.get(Places.key(level, LEDGER));
		waitFor(server, "everything is drawn again after loading", 200,
			() -> vault.drawn > 0 && first.display != null && level.getEntity(first.display) != null, () -> {
				check(pileParts(level, vault) == PARTS[1], "the pile was rebuilt, not doubled (" + pileParts(level, vault) + " vs " + PARTS[1] + ")");
				RichesMod.LOG.info("RICHES SMOKE TEST PASSED");
				server.halt(false);
			});
	}

	// ---------------------------------------------------------------- pieces

	private static void catalogue() {
		check(Relic.values().length == 24, "24 relics");
		Set<String> titles = new HashSet<>();
		for (Relic.Collection c : Relic.Collection.values()) {
			check(c.relics().size() == 6, c.title + " has 6 relics");
		}
		for (Relic r : Relic.values()) {
			check(titles.add(r.title), "relic titles are unique: " + r.title);
			check(r.chance > 0 && r.chance <= 1, r.id() + " has a sensible chance");
			check(BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.withDefaultNamespace(r.model)),
				r.id() + " looks like an item that exists: " + r.model);
			ItemStack stack = RichesItems.relic(r);
			check(RichesItems.relicOf(stack) == r, r.id() + " round-trips");
			if (r.source != Relic.Source.CHEST) {
				boolean known = r.source == Relic.Source.KILL
					? BuiltInRegistries.ENTITY_TYPE.containsKey(net.minecraft.resources.Identifier.withDefaultNamespace(r.what))
					: BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.withDefaultNamespace(r.what));
				check(known, r.id() + " comes from something that exists: " + r.what);
			}
		}
		check(RichesItems.isLedger(RichesItems.ledger()) && RichesItems.isDoor(RichesItems.door()) && RichesItems.isCase(RichesItems.displayCase())
			&& RichesItems.isPedestal(RichesItems.pedestal()), "the blocks are recognised");
		check(!RichesItems.isCase(new ItemStack(Items.GLASS)), "plain glass isn't a display case");
		log("relic catalogue");
	}

	private static void prices() {
		check(RichesMod.price(RichesItems.relic(Relic.WARDEN_ECHO)) == -1L, "relics can't be sold to the Market");
		check(RichesMod.price(new ItemStack(Items.PAPER)) == null, "plain paper isn't ours");
		check(FabricLoader.getInstance().getObjectShare().get(RichesMod.PRICE_HOOKS) instanceof List<?>, "the price hook is published");
		log("market prices");
	}

	private static void heights() {
		check(Vaults.height(Bank.cents(500)) == 0, "₥500 is too poor for a pile");
		double small = Vaults.height(Bank.cents(1_000));
		double mid = Vaults.height(Bank.cents(100_000));
		double big = Vaults.height(Bank.cents(1_000_000_000L));
		check(small > 0 && mid > small && big <= RichesConfig.get().pileMaxHeight, "piles grow with the balance and stop at the ceiling ("
			+ small + ", " + mid + ", " + big + ")");
		log("pile heights");
	}

	/** Gold columns standing around the ledger (they all ride one root in the middle). */
	private static int pileParts(ServerLevel level, Places.Vault v) {
		BlockPos p = v.pos;
		return level.getEntitiesOfClass(Display.BlockDisplay.class,
			new AABB(p.getX() - 1, p.getY() - 1, p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2)).size();
	}

	private static void log(String what) {
		RichesMod.LOG.info("[smoke] ok: {}", what);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		RichesMod.LOG.error("[smoke] RICHES SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
	}
}
