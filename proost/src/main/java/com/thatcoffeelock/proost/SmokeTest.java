package com.thatcoffeelock.proost;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dproost.smokeTest=true (CI). Boots a real server and takes a crop all the way to the glass:
 * checks the items and their Market prices, works out what the still makes from every mash (with and without
 * sweet berries, glass bottles and enough mash), ferments wheat in a real Mash Tun and ages bottles in a real Oak
 * Cask (with topping up and a long absence), checks a cauldron only counts as a still over a lit fire, sobers a
 * drunk up, and checks the barrels survive a save and load.
 */
final class SmokeTest {
	private static final BlockPos TUN = new BlockPos(0, 101, 0);
	private static final BlockPos CASK = new BlockPos(2, 101, 0);
	private static final BlockPos STILL = new BlockPos(4, 102, 0);

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -16 -16 16 16");
			ProostMod.later(100, () -> step(server, () -> start(level)));
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
		ProostMod.later(5, () -> step(server, () -> {
			if (condition.getAsBoolean()) {
				ProostMod.LOG.info("[smoke] ok: {} (after {} ticks)", what, waited + 5);
				then.run();
			} else if (waited + 5 >= maxTicks) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for " + what);
			} else {
				poll(server, what, maxTicks, waited + 5, condition, then);
			}
		}));
	}

	private static void start(ServerLevel level) {
		Cmd.run(level, "fill -12 99 -12 12 99 12 minecraft:stone");
		Cmd.run(level, "fill -12 100 -12 12 110 12 minecraft:air");
		items();
		prices();
		still();
		drinking();
		barrels(level);
	}

	// ---------------------------------------------------------------- items

	@SuppressWarnings("unchecked")
	private static void items() {
		ItemStack mash = ProostItems.mash(ProostItems.Base.GRAIN, 4);
		check(ProostItems.isMash(mash) && ProostItems.base(mash) == ProostItems.Base.GRAIN && mash.getCount() == 4, "grain mash is recognised");
		check(!ProostItems.isMash(new ItemStack(Items.PAPER)) && !ProostItems.isSpirit(new ItemStack(Items.PAPER)), "plain paper is neither");
		ItemStack genever = ProostItems.spirit(ProostItems.Drink.GENEVER, false, 2);
		check(ProostItems.isSpirit(genever) && ProostItems.drink(genever) == ProostItems.Drink.GENEVER && !ProostItems.isAged(genever)
			&& !ProostItems.isOpen(genever), "a sealed bottle of young genever is one");
		check(genever.getMaxStackSize() == ProostItems.BOTTLE_STACK, "sealed bottles stack to " + ProostItems.BOTTLE_STACK);
		check(genever.get(DataComponents.ITEM_MODEL) != null && mash.get(DataComponents.ITEM_MODEL) != null, "they don't look like paper");
		ItemStack open = ProostItems.opened(genever);
		check(ProostItems.isOpen(open) && open.getCount() == 1 && open.getMaxStackSize() == 1 && open.getMaxDamage() == ProostItems.SWIGS,
			"an opened bottle is single and counts its swigs");
		check(ProostItems.isAged(ProostItems.spirit(ProostItems.Drink.RUM, true, 1)), "dark rum is aged");
		check(ProostItems.Base.ofCrop(new ItemStack(Items.SUGAR_CANE)) == ProostItems.Base.CANE
			&& ProostItems.Base.ofCrop(new ItemStack(Items.APPLE)) == ProostItems.Base.APPLE
			&& ProostItems.Base.ofCrop(new ItemStack(Items.WHEAT)) == ProostItems.Base.GRAIN
			&& ProostItems.Base.ofCrop(new ItemStack(Items.CARROT)) == null, "wheat, sugar cane and apples ferment; carrots don't");
		Object hook = FabricLoader.getInstance().getObjectShare().get(ProostMod.ITEM_HOOK);
		check(hook instanceof BiFunction<?, ?, ?>, "the item hook for Colonycraft is published");
		BiFunction<String, Integer, ItemStack> make = (BiFunction<String, Integer, ItemStack>) hook;
		check(ProostItems.isAged(make.apply("genever:aged", 3)) && make.apply("genever:aged", 3).getCount() == 3
			&& ProostItems.isMash(make.apply("mash:cane", 5)) && make.apply("nonsense", 1).isEmpty(), "the item hook makes real bottles and mash");
	}

	/** The Market price hook: worth more than what went in, aging pays, opened bottles sell for less. */
	private static void prices() {
		check(Prices.price(new ItemStack(Items.PAPER)) == null, "plain paper isn't ours to price");
		long mash = Prices.price(ProostItems.mash(ProostItems.Base.GRAIN, 1));
		long young = Prices.price(ProostItems.spirit(ProostItems.Drink.WHISKY, false, 1));
		long aged = Prices.price(ProostItems.spirit(ProostItems.Drink.WHISKY, true, 1));
		check(mash > 15, "mash sells for more than paper (" + mash + " cents)");
		check(young > Still.MASH_PER_BOTTLE * mash, "a bottle is worth more than the mash in it (" + young + ")");
		check(aged > 2 * young, "aging more than doubles it (" + aged + ")");
		ItemStack open = ProostItems.opened(ProostItems.spirit(ProostItems.Drink.WHISKY, false, 1));
		open.setDamageValue(2);
		long half = Prices.price(open);
		check(half > 0 && half < young / 2, "a half-drunk bottle sells for less than half (" + half + ")");
	}

	// ---------------------------------------------------------------- the still

	private static void still() {
		ItemStack grain = ProostItems.mash(ProostItems.Base.GRAIN, 20);
		ItemStack berries = new ItemStack(Items.SWEET_BERRIES, 1);
		check(!Still.plan(new ItemStack(Items.WHEAT, 20), ItemStack.EMPTY, 5, 1).ok(), "raw wheat doesn't distill");
		check(!Still.plan(ProostItems.mash(ProostItems.Base.GRAIN, Still.MASH_PER_BOTTLE - 1), ItemStack.EMPTY, 5, 1).ok(), "not enough mash");
		check(!Still.plan(grain, ItemStack.EMPTY, 0, 1).ok(), "no glass bottle, no spirit");
		Still.Plan whisky = Still.plan(grain, ItemStack.EMPTY, 5, 1);
		check(whisky.ok() && ProostItems.drink(whisky.bottles()) == ProostItems.Drink.WHISKY && whisky.bottles().getCount() == 1
			&& whisky.mashUsed() == Still.MASH_PER_BOTTLE && whisky.glassUsed() == 1, "grain mash makes whisky");
		Still.Plan genever = Still.plan(grain, berries, 5, Still.BATCH);
		check(genever.ok() && ProostItems.drink(genever.bottles()) == ProostItems.Drink.GENEVER && genever.bottles().getCount() == 1
			&& genever.berriesUsed() == 1, "with sweet berries it's genever, one berry a bottle");
		Still.Plan batch = Still.plan(grain, ItemStack.EMPTY, -1, Still.BATCH);
		check(batch.ok() && batch.bottles().getCount() == 2 && batch.glassUsed() == 0, "a batch is limited by the mash (creative needs no glass)");
		check(ProostItems.drink(Still.plan(ProostItems.mash(ProostItems.Base.CANE, 8), ItemStack.EMPTY, 1, 1).bottles()) == ProostItems.Drink.RUM
			&& ProostItems.drink(Still.plan(ProostItems.mash(ProostItems.Base.APPLE, 8), ItemStack.EMPTY, 1, 1).bottles()) == ProostItems.Drink.BRANDY,
			"cane mash makes rum, apple mash makes brandy");
		check(!ProostItems.isAged(whisky.bottles()), "the still makes young spirit");
	}

	// ---------------------------------------------------------------- drinking

	private static void drinking() {
		List<Drinking.Effect> dutch = Drinking.effects(ProostItems.spirit(ProostItems.Drink.GENEVER, false, 1));
		check(dutch.size() == 1 && dutch.get(0).id().equals("strength"), "genever is Dutch Courage");
		check(Drinking.effects(ProostItems.spirit(ProostItems.Drink.GENEVER, true, 1)).size() == 2, "oude genever does more");
		for (ProostItems.Drink drink : ProostItems.Drink.values()) {
			check(!Drinking.effects(ProostItems.spirit(drink, false, 1)).isEmpty(), drink.id() + " does something");
		}
		UUID who = UUID.randomUUID();
		check(Drinking.drunkenness(who) == 0 && Drinking.describe(0).equals("sober"), "nobody starts drunk");
		Drinking.setDrunkenness(who, Drinking.DRUNK + 1);
		check(Drinking.describe(Drinking.drunkenness(who)).equals("drunk"), "six swigs in, you're drunk");
		Drinking.soberUp(Drinking.SOBER_TICKS * 2);
		check(Math.abs(Drinking.drunkenness(who) - (Drinking.DRUNK - 1)) < 0.001, "a minute later you're two points soberer");
		Drinking.soberUp(Drinking.SOBER_TICKS * 10);
		check(Drinking.drunkenness(who) == 0, "and eventually sober");
	}

	// ---------------------------------------------------------------- barrels

	private static void barrels(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Cmd.setblock(level, TUN, "minecraft:barrel");
		check(Barrels.typeAt(level, TUN) == null, "a plain barrel is just a barrel");
		Cmd.run(level, "data merge block " + Cmd.block(TUN) + " {CustomName:{text:\"Mash Tun\",color:\"gold\",italic:false}}");
		check(Barrels.typeAt(level, TUN) == Barrels.Type.TUN, "a barrel named Mash Tun is one");
		Container tun = Barrels.at(level, TUN);
		ProostStore.Barrel tunState = ProostStore.addBarrel(level, TUN);

		tun.setItem(0, new ItemStack(Items.WHEAT, 10));
		tun.setItem(1, new ItemStack(Items.COBBLESTONE, 5));
		tun.setItem(2, ProostItems.spirit(ProostItems.Drink.RUM, false, 2));
		check(Barrels.age(Barrels.Type.TUN, tun, tunState, 100) == 0 && tunState.slots.size() == 1, "one batch starts (cobblestone and bottles ignored)");
		Barrels.age(Barrels.Type.TUN, tun, tunState, Barrels.FERMENT_TICKS / 2);
		tun.setItem(0, new ItemStack(Items.WHEAT, 20));
		Barrels.age(Barrels.Type.TUN, tun, tunState, Barrels.FERMENT_TICKS / 4);
		check(tun.getItem(0).is(Items.WHEAT), "doubling the wheat halves its progress: not done yet");
		Barrels.age(Barrels.Type.TUN, tun, tunState, Barrels.FERMENT_TICKS / 2);
		check(ProostItems.isMash(tun.getItem(0)) && tun.getItem(0).getCount() == 20 && ProostItems.base(tun.getItem(0)) == ProostItems.Base.GRAIN,
			"20 wheat fermented into 20 grain mash");
		check(tun.getItem(1).is(Items.COBBLESTONE) && ProostItems.isSpirit(tun.getItem(2)) && !ProostItems.isAged(tun.getItem(2)),
			"a tun doesn't touch anything else (bottles don't age in it)");

		Cmd.setblock(level, CASK, "minecraft:barrel");
		Cmd.run(level, "data merge block " + Cmd.block(CASK) + " {CustomName:{text:\"Oak Cask\",color:\"gold\",italic:false}}");
		check(Barrels.typeAt(level, CASK) == Barrels.Type.CASK, "a barrel named Oak Cask is a cask");
		Container cask = Barrels.at(level, CASK);
		ProostStore.Barrel caskState = ProostStore.addBarrel(level, CASK);
		cask.setItem(0, ProostItems.spirit(ProostItems.Drink.GENEVER, false, 6));
		cask.setItem(1, ProostItems.opened(ProostItems.spirit(ProostItems.Drink.WHISKY, false, 1)));
		cask.setItem(2, new ItemStack(Items.SUGAR_CANE, 8));
		Barrels.age(Barrels.Type.CASK, cask, caskState, 1);
		Barrels.age(Barrels.Type.CASK, cask, caskState, Barrels.AGE_TICKS * 3);
		check(ProostItems.isAged(cask.getItem(0)) && cask.getItem(0).getCount() == 6 && ProostItems.drink(cask.getItem(0)) == ProostItems.Drink.GENEVER,
			"after a long absence: 6 bottles of Oude Genever");
		check(!ProostItems.isAged(cask.getItem(1)) && cask.getItem(2).is(Items.SUGAR_CANE), "opened bottles don't age, and a cask doesn't ferment");
		cask.setItem(3, ProostItems.spirit(ProostItems.Drink.BRANDY, false, 1));
		Barrels.update(level, CASK, Barrels.Type.CASK, cask, caskState);
		check(caskState.slots.containsKey("3"), "the cask picks up new bottles when it's updated");

		Cmd.setblock(level, STILL.below(), "minecraft:campfire[lit=false]");
		Cmd.setblock(level, STILL, "minecraft:cauldron");
		check(!Still.isStill(level, STILL), "a cauldron on an unlit campfire isn't a still");
		Cmd.setblock(level, STILL.below(), "minecraft:campfire[lit=true]");
		check(Still.isStill(level, STILL), "on a lit campfire it is");
		Cmd.setblock(level, STILL.below(), "minecraft:magma_block");
		check(Still.isStill(level, STILL), "magma works too");

		ProostStore.save();
		ProostStore.load(server);
		ProostStore.Barrel loaded = ProostStore.barrel(level, CASK);
		check(loaded != null && loaded.slots.containsKey("3") && loaded.slots.get("3").kind.equals("young:brandy"), "the cask survives a save and load");
		Cmd.setblock(level, TUN, "minecraft:air");
		Barrels.scan(server);
		check(ProostStore.barrel(level, TUN) == null, "a broken tun is forgotten");

		waitFor(server, "the server to keep ticking with Proost loaded", 40, () -> true, () -> {
			ProostMod.LOG.info("PROOST SMOKE TEST PASSED");
			server.halt(false);
		});
	}

	private static void fail(MinecraftServer server, Throwable t) {
		ProostMod.LOG.error("PROOST SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		ProostMod.LOG.info("[smoke] ok: {}", what);
	}
}
