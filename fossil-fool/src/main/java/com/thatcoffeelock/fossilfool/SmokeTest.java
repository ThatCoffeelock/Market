package com.thatcoffeelock.fossilfool;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Only runs with -Dfossilfool.smokeTest=true (CI). Boots a real server and goes through the whole oil business:
 * items and prices, the fuel ladder, pockets (deterministic, breaking in by hand, dowsing), a Drill Rig drilling a
 * real 5×5 shaft through stone and ore, sealing a water leak, leaving a ladder, striking a pocket and pumping it
 * into an Oil Tank, a Refinery turning that crude into diesel, and everything surviving a save and load (the rig's
 * model gets rebuilt).
 */
final class SmokeTest {
	private static final BlockPos RIG = new BlockPos(0, 80, 0);
	private static final BlockPos POCKET = new BlockPos(0, 70, 0);
	private static final BlockPos ORE = new BlockPos(1, 78, 1);
	private static final BlockPos LEAK = new BlockPos(3, 76, 0);
	private static final BlockPos TANK = new BlockPos(5, 81, 0);
	private static final BlockPos REFINERY = new BlockPos(5, 81, 4);
	private static final BlockPos HAND_POCKET = new BlockPos(20, 60, 20);

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -32 -32 32 32");
			FossilFoolMod.later(100, () -> step(server, () -> start(server, level)));
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

	/** Checks every 5 ticks until the condition holds, then carries on. Fails after maxTicks. */
	private static void waitFor(MinecraftServer server, String what, int maxTicks, BooleanSupplier condition, Step then) {
		poll(server, what, maxTicks, 0, condition, then);
	}

	private static void poll(MinecraftServer server, String what, int maxTicks, int waited, BooleanSupplier condition, Step then) {
		FossilFoolMod.later(5, () -> step(server, () -> {
			if (condition.getAsBoolean()) {
				FossilFoolMod.LOG.info("[smoke] ok: {} (after {} ticks)", what, waited + 5);
				then.run();
			} else if (waited + 5 >= maxTicks) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for " + what);
			} else {
				poll(server, what, maxTicks, waited + 5, condition, then);
			}
		}));
	}

	private static void start(MinecraftServer server, ServerLevel level) {
		FossilConfig c = FossilConfig.get();
		c.ticksPerBlock = 1;
		c.ticksPerBucket = 1;
		c.refineTicks = 10;
		items();
		fuel();
		prices();
		rolls(level);
		c.pocketChance = 0;
		Pockets.clearCache();
		handPocket(level);

		Cmd.run(level, "fill -6 40 -6 6 80 6 minecraft:stone");
		Cmd.run(level, "fill -6 81 -6 6 92 6 minecraft:air");
		Cmd.run(level, "setblock " + ORE.getX() + " " + ORE.getY() + " " + ORE.getZ() + " minecraft:iron_ore");
		Cmd.run(level, "setblock " + LEAK.getX() + " " + LEAK.getY() + " " + LEAK.getZ() + " minecraft:water");
		Pockets.create(level, POCKET, 2, 1, 2);
		Pockets.Reading reading = Pockets.dowse(level, RIG.above(), 48);
		check(reading != null && reading.pocket().x() == POCKET.getX() && reading.depth() == RIG.above().getY() - POCKET.getY(),
			"the dowsing rod points at the pocket under the rig");
		log("dowsing");

		Cmd.run(level, "setblock " + TANK.getX() + " " + TANK.getY() + " " + TANK.getZ() + " minecraft:cauldron");
		Interactions.onPlaced(level, null, TANK, OilItems.tank());
		Tank tank = Machines.tankAt(level, TANK);
		check(tank != null, "the tank is remembered where it was placed");
		check(tank.fill(Fluid.DIESEL, 3) == 3 && tank.fill(Fluid.CRUDE, 1) == 0 && tank.drain(Fluid.DIESEL, 5) == 3 && tank.fluid == Fluid.NONE,
			"a tank holds one oil at a time and empties back to nothing");
		log("tank");

		Rig rig = Rigs.place(level, null, RIG);
		check(rig != null && rig.root != null, "the rig is placed and its model summoned");
		int parts = rig.root.getPassengers().size();
		check(parts == 1 + Rig.FRAME.size() + 1 + Rig.HEAD.size(), "the derrick has all its parts (" + parts + ")");
		check(Rigs.place(level, null, RIG.east(3)) == null, "no second rig right next to the first");
		check(rig.firebox.canPlaceItem(0, OilItems.diesel(1)) && !rig.firebox.canPlaceItem(0, new ItemStack(Items.DIRT)), "the firebox only takes fuel");
		rig.firebox.setItem(0, OilItems.diesel(2));
		log("rig placed");

		waitFor(server, "the rig strikes the pocket", 1200, () -> rig.struck != null, () -> {
			check(rig.layer <= 72 && rig.layer >= 70, "it struck at the pocket's depth (layer " + rig.layer + ")");
			check(level.getBlockState(new BlockPos(0, 79, -2)).is(Blocks.LADDER), "a ladder up the north wall");
			check(level.getBlockState(LEAK).is(Blocks.COBBLESTONE), "the water leak in the wall was sealed");
			check(level.getBlockState(new BlockPos(-2, 75, -2)).isAir() && level.getBlockState(new BlockPos(2, 75, 2)).isAir(), "the shaft is 5×5");
			check(level.getBlockState(new BlockPos(3, 75, 3)).is(Blocks.STONE), "and not wider");
			check(count(rig.ores, Items.RAW_IRON) >= 1, "the iron ore went to the ore hold");
			check(count(rig.stone, Items.COBBLESTONE) >= 150, "the stone went to the stone hold (" + count(rig.stone, Items.COBBLESTONE) + ")");
			check(count(rig.stone, Items.RAW_IRON) == 0, "and the ore didn't");
			check(count(rig.firebox, Items.BUCKET) >= 1 && rig.burning == Fuel.DIESEL, "it burns diesel and keeps the empty buckets");
			Pockets.Pocket p = Pockets.OPENED.get(rig.struck);
			check(p != null && Pockets.left(p) > 0, "the pocket opened into crude blocks");
			int total = Pockets.left(p) + rig.crude;
			log("struck oil: " + total + " buckets");
			waitFor(server, "the rig pumps the pocket dry", 1200, () -> rig.struck == null && Pockets.left(p) == 0, () -> {
				waitFor(server, "the crude is piped into the tank", 200, () -> rig.crude == 0 && tank.amount == total, () -> {
					check(tank.fluid == Fluid.CRUDE, "the tank holds crude");
					check(level.getBlockState(POCKET).isAir(), "the pocket is empty rock now");
					Pockets.Reading after = Pockets.dowse(level, RIG.above(), 48);
					check(after == null || after.pocket().x() != POCKET.getX(), "a dry pocket doesn't twitch the rod");
					rig.on = false;
					refinery(server, level, tank, rig);
				});
			});
		});
	}

	private static void refinery(MinecraftServer server, ServerLevel level, Tank tank, Rig rig) {
		Cmd.run(level, "setblock " + REFINERY.getX() + " " + REFINERY.getY() + " " + REFINERY.getZ() + " minecraft:blast_furnace");
		Interactions.onPlaced(level, null, REFINERY, OilItems.refinery());
		Refinery r = Machines.refineryAt(level, REFINERY);
		check(r != null, "the refinery is remembered where it was placed");
		r.firebox.setItem(0, new ItemStack(Items.COAL, 64));
		int crude = tank.amount;
		// it drinks the tank dry, then pipes its diesel back into the same (now empty) tank
		BooleanSupplier made = () -> r.diesel + (tank.fluid == Fluid.DIESEL ? tank.amount : 0) >= 2;
		waitFor(server, "the refinery makes diesel from the tank's crude", 1200, made, () -> {
			check(tank.fluid != Fluid.CRUDE || tank.amount < crude, "it drank crude from the tank");
			check(r.burning == Fuel.COAL, "it burns coal");
			log("refinery: " + r.diesel + " diesel inside, tank holds " + tank.amount + " " + tank.fluid.title);
			r.on = false;
			saveAndLoad(server, level, rig);
		});
	}

	private static void saveAndLoad(MinecraftServer server, ServerLevel level, Rig before) {
		int rigs = Rigs.BY_ID.size();
		int tanks = Machines.TANKS.size();
		int refineries = Machines.REFINERIES.size();
		int pockets = Pockets.OPENED.size();
		int layer = before.layer;
		int stone = count(before.stone, Items.COBBLESTONE);
		Tank tankBefore = Machines.tankAt(level, TANK);
		Fluid fluid = tankBefore.fluid;
		int amount = tankBefore.amount;
		Store.save();
		Store.load(server);
		check(Rigs.BY_ID.size() == rigs && Machines.TANKS.size() == tanks && Machines.REFINERIES.size() == refineries
			&& Pockets.OPENED.size() == pockets, "rigs, tanks, refineries and pockets survive a save and load");
		Rig rig = Rigs.BY_ID.get(before.id);
		check(rig != null && rig.layer == layer && !rig.on && count(rig.stone, Items.COBBLESTONE) == stone, "the rig remembers its depth and holds");
		Tank tank = Machines.tankAt(level, TANK);
		check(tank != null && tank.fluid == fluid && tank.amount == amount, "the tank remembers its oil");
		check(Pockets.isOil(level, HAND_POCKET), "opened pockets remember their oil");
		waitFor(server, "the rig's model is found again or rebuilt", 400, () -> rig.root != null && !rig.root.isRemoved(), () -> {
			Entity root = rig.root;
			check(root.getPassengers().size() == 1 + Rig.FRAME.size() + 1 + Rig.HEAD.size(), "the model is whole");
			Rigs.packUp(rig, null);
			check(Rigs.BY_ID.isEmpty(), "the rig packs up");
			FossilFoolMod.later(5, () -> step(server, () -> {
				check(root.isRemoved(), "its model is gone");
				FossilFoolMod.LOG.info("FOSSIL FOOL SMOKE TEST PASSED");
				server.halt(false);
			}));
		});
	}

	private static void items() {
		check(OilItems.isCrude(OilItems.crude(3)) && OilItems.crude(3).getCount() == 3, "crude buckets");
		check(OilItems.isDiesel(OilItems.diesel(1)) && !OilItems.isCrude(OilItems.diesel(1)), "diesel buckets");
		check(OilItems.isRig(OilItems.rig()) && OilItems.isTank(OilItems.tank()) && OilItems.isRefinery(OilItems.refinery())
			&& OilItems.isRod(OilItems.rod()), "machines and the rod are recognised");
		check(!OilItems.isRig(new ItemStack(Items.PISTON)) && !OilItems.isTank(new ItemStack(Items.CAULDRON)), "plain blocks aren't machines");
		check(OilItems.crude(1).getMaxStackSize() == OilItems.BUCKET_STACK, "buckets of oil stack to " + OilItems.BUCKET_STACK);
		ItemStack full = OilItems.tank(Fluid.DIESEL, 40);
		check(OilItems.isTank(full) && OilItems.data(full).getIntOr(OilItems.AMOUNT, 0) == 40, "a packed tank keeps its oil");
		log("items");
	}

	private static void fuel() {
		Fuel[] ladder = Fuel.values();
		for (int i = 1; i < ladder.length; i++) {
			check(ladder[i].blocks() > ladder[i - 1].blocks() && ladder[i].speed() > ladder[i - 1].speed(),
				ladder[i].title + " beats " + ladder[i - 1].title);
		}
		check(Fuel.of(new ItemStack(Items.COAL)).fuel() == Fuel.COAL && Fuel.of(new ItemStack(Items.CHARCOAL)).fuel() == Fuel.COAL, "coal and charcoal");
		check(Fuel.of(new ItemStack(Items.COAL_BLOCK)).blocks() == Fuel.COAL.blocks() * 9, "a coal block is nine coal");
		check(Fuel.of(new ItemStack(Items.LAVA_BUCKET)).leftover().is(Items.BUCKET), "lava leaves a bucket");
		check(Fuel.of(OilItems.crude(1)).fuel() == Fuel.CRUDE && Fuel.of(OilItems.diesel(1)).fuel() == Fuel.DIESEL, "crude and diesel burn");
		check(Fuel.of(new ItemStack(Items.OAK_LOG)) == null && Fuel.of(OilItems.rod()) == null, "logs and rods don't");
		log("fuel ladder");
	}

	@SuppressWarnings("unchecked")
	private static void prices() {
		check(Hooks.price(OilItems.crude(1)) == 2500 && Hooks.price(OilItems.diesel(1)) == 4500, "crude ₥25, diesel ₥45");
		check(Hooks.price(OilItems.rig()) == -1 && Hooks.price(new ItemStack(Items.PAPER)) == null, "machines don't sell, paper isn't ours");
		Object hooks = FabricLoader.getInstance().getObjectShare().get(Hooks.PRICE_HOOKS);
		check(hooks instanceof List<?> list && !list.isEmpty(), "the Market price hook is published");
		log("market prices");
	}

	private static void rolls(ServerLevel level) {
		FossilConfig c = FossilConfig.get();
		c.pocketChance = 1;
		Pockets.clearCache();
		Pockets.Pocket a = Pockets.inChunk(level, 50, 50);
		Pockets.clearCache();
		Pockets.Pocket b = Pockets.inChunk(level, 50, 50);
		check(a != null && a.equals(b), "pockets follow from the seed");
		check(a.y() >= c.pocketMinY && a.y() <= c.pocketMaxY && (a.x() >> 4) == 50, "a pocket sits in its chunk, at oil depth");
		log("pocket rolls");
	}

	private static void handPocket(ServerLevel level) {
		Cmd.run(level, "fill 15 55 15 25 65 25 minecraft:stone");
		Pockets.create(level, HAND_POCKET, 2, 1, 2);
		check(!Pockets.isOil(level, HAND_POCKET), "a pocket is just rock until someone breaks in");
		Pockets.Pocket p = Pockets.breach(level, HAND_POCKET.above(2), null);
		check(p != null && Pockets.left(p) > 5 && Pockets.isOil(level, HAND_POCKET), "breaking in next to it turns it into crude");
		check(level.getBlockState(HAND_POCKET).is(Blocks.BLACK_CONCRETE), "crude looks like crude");
		int before = Pockets.left(p);
		check(Pockets.drain(level, HAND_POCKET.east()) && Pockets.left(p) == before - 1 && level.getBlockState(HAND_POCKET.east()).isAir(),
			"a bucket takes one block of crude");
		Cmd.run(level, "setblock 15 66 15 minecraft:black_concrete");
		check(!Pockets.isOil(level, new BlockPos(15, 66, 15)), "placed black concrete isn't oil");
		log("breaking into a pocket by hand");
	}

	private static int count(Container box, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack stack = box.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void log(String what) {
		FossilFoolMod.LOG.info("[smoke] ok: {}", what);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		FossilFoolMod.LOG.error("[smoke] FOSSIL FOOL SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
	}
}
