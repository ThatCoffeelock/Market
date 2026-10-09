package com.thatcoffeelock.fossilfool;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
	/** A hopper beside the derrick, one block above the ground, with a chest under it (the ring around the shaft). */
	private static final BlockPos HOPPER = new BlockPos(-3, 81, 3);
	private static final BlockPos HAND_POCKET = new BlockPos(20, 60, 20);
	/** A pipeline runs west from the ring around the shaft to a Lava Tank and a chest, far out of pipe reach. */
	private static final BlockPos FAR_TANK = new BlockPos(-25, 81, 0);
	private static final BlockPos FAR_CHEST = new BlockPos(-24, 80, 0);
	/** A walled-in bit of sea, 10 deep, with an offshore rig on its floor. */
	private static final BlockPos SEABED = new BlockPos(0, 70, -25);

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
		tank.set = Fluid.LAVA;
		check(tank.name().equals("Lava Tank") && tank.fill(Fluid.CRUDE, 1) == 0 && tank.fill(Fluid.LAVA, 2) == 2 && !tank.canSet(Fluid.WATER),
			"a Lava Tank takes lava only, and can't be switched while it holds lava");
		Interactions.showFluid(level, tank);
		check(BuiltInRegistries.BLOCK.getKey(level.getBlockState(TANK).getBlock()).getPath().equals("lava_cauldron")
			&& Interactions.isTankBlock(level.getBlockState(TANK)), "a lava tank looks like a lava cauldron");
		check(tank.drain(Fluid.LAVA, 2) == 2 && tank.cycle() == Fluid.NONE, "emptied, it cycles back to taking anything");
		Interactions.showFluid(level, tank);
		check(level.getBlockState(TANK).is(Blocks.CAULDRON), "and looks like a plain cauldron again");
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
					hoppers(server, level, tank, rig);
				});
			});
		});
	}

	/** A hopper next to the derrick takes the holds' contents into a Pickup Station chest below it. */
	private static void hoppers(MinecraftServer server, ServerLevel level, Tank tank, Rig rig) {
		check(rig.isHopperSpot(HOPPER) && !rig.isHopperSpot(new BlockPos(0, 81, 0)) && !rig.isHopperSpot(HOPPER.above(2)),
			"hoppers count around the shaft, not over it or up in the air");
		Cmd.run(level, "setblock " + HOPPER.getX() + " " + (HOPPER.getY() - 1) + " " + HOPPER.getZ()
			+ " minecraft:chest{CustomName:{text:\"Pickup Station\"}}");
		Cmd.run(level, "setblock " + HOPPER.getX() + " " + HOPPER.getY() + " " + HOPPER.getZ() + " minecraft:hopper");
		int ores = count(rig.ores, Items.RAW_IRON);
		waitFor(server, "the rig feeds a hopper into a Pickup Station chest", 600,
			() -> level.getBlockEntity(HOPPER.below()) instanceof Container chest && count(chest, Items.COBBLESTONE) >= 8, () -> {
				Container chest = (Container) level.getBlockEntity(HOPPER.below());
				check(count(chest, Items.RAW_IRON) + count((Container) level.getBlockEntity(HOPPER), Items.RAW_IRON) == ores,
					"ores went down the hopper first");
				refinery(server, level, tank, rig);
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
			pipeline(server, level, rig);
		});
	}

	/** A pipeline from the ring around the shaft to a Lava Tank and a chest 25 blocks away: fuel, ore and stone go down it. */
	private static void pipeline(MinecraftServer server, ServerLevel level, Rig rig) {
		for (int x = -3; x >= -24; x--) {
			Cmd.run(level, "setblock " + x + " 81 0 minecraft:lightning_rod[facing=east]");
			Interactions.onPlaced(level, null, new BlockPos(x, 81, 0), OilItems.pipe(1));
		}
		check(Pipes.count() == 22 && Pipes.isPipeBlock(level.getBlockState(new BlockPos(-10, 81, 0))), "a pipeline of 22 pipes is laid");
		Cmd.run(level, "setblock " + FAR_TANK.getX() + " " + FAR_TANK.getY() + " " + FAR_TANK.getZ() + " minecraft:cauldron");
		Interactions.onPlaced(level, null, FAR_TANK, OilItems.tank(Fluid.LAVA));
		Tank far = Machines.tankAt(level, FAR_TANK);
		check(far != null && far.set == Fluid.LAVA, "a Lava Tank at the far end");
		Cmd.run(level, "setblock " + FAR_CHEST.getX() + " " + FAR_CHEST.getY() + " " + FAR_CHEST.getZ() + " minecraft:chest");
		Pipes.Network net = rig.pipeline(level);
		check(net.tanks().contains(far) && net.storage().contains(FAR_CHEST), "the pipeline reaches the tank and the chest");
		check(Machines.tanksFor(level, rig).contains(far) && !Machines.tanksNear(level, rig.center(), 3).contains(far), "only through the pipe");

		far.fill(Fluid.LAVA, 3);
		rig.firebox.clearContent();
		Tank near = Machines.tankAt(level, TANK);
		near.drain(near.fluid, near.amount);
		near.fill(Fluid.DIESEL, 1);
		rig.energy = 0;
		check(rig.refuel(level, 1) && rig.burning == Fuel.DIESEL && near.amount == 0 && far.amount == 3,
			"an empty firebox burns the best fuel first: diesel from the tank next door");
		rig.energy = 0;
		check(rig.refuel(level, 1) && rig.burning == Fuel.LAVA && far.amount == 2, "then lava from the tank down the pipe");
		rig.crude = 4;
		Machines.pipeOut(level, rig);
		check(far.amount == 2 && far.fluid == Fluid.LAVA, "the rig's crude doesn't go into the Lava Tank");
		rig.crude = 0;

		if (count(rig.stone, Items.COBBLESTONE) < Rig.PIPE_BATCH) {
			rig.stone.addItem(new ItemStack(Items.COBBLESTONE, 32));
		}
		rig.feedPipes(level);
		check(level.getBlockEntity(FAR_CHEST) instanceof Container chest && count(chest, Items.COBBLESTONE) > 0,
			"stone goes down the pipe into the far chest");

		Cmd.run(level, "setblock -12 81 0 minecraft:air");
		Machines.validate();
		check(!Pipes.has(level, new BlockPos(-12, 81, 0)) && !rig.pipeline(level).tanks().contains(far), "a broken pipe cuts the line");
		log("pipeline");
		oven(level, rig);
		offshore(server, level, rig);
	}

	/**
	 * An Industrial Oven on the rig's pipeline: it drinks diesel from a tank on the line, the rig sends it raw iron
	 * (and not diamonds), it smelts the iron double and cobblestone single, and hands output to a hopper underneath.
	 */
	private static void oven(ServerLevel level, Rig rig) {
		BlockPos at = new BlockPos(-6, 82, 0);
		Cmd.run(level, "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " minecraft:smoker");
		Interactions.onPlaced(level, null, at, OilItems.oven());
		Oven oven = Machines.ovenAt(level, at);
		check(oven != null && rig.pipeline(level).ovens().contains(oven), "an Industrial Oven sits on the rig's pipeline");
		check(Oven.smelt(level, new ItemStack(Items.RAW_IRON)).is(Items.IRON_INGOT) && Oven.smelt(level, new ItemStack(Items.RAW_IRON)).getCount() == 2
			&& Oven.smelt(level, new ItemStack(Items.COBBLESTONE)).is(Items.STONE) && Oven.smelt(level, new ItemStack(Items.COBBLESTONE)).getCount() == 1
			&& Oven.smelt(level, new ItemStack(Items.DIAMOND)).isEmpty(), "it smelts like a furnace, ores double");

		Cmd.run(level, "setblock -5 82 0 minecraft:cauldron");
		Interactions.onPlaced(level, null, new BlockPos(-5, 82, 0), OilItems.tank(Fluid.DIESEL));
		Tank dieselTank = Machines.tankAt(level, new BlockPos(-5, 82, 0));
		dieselTank.fill(Fluid.DIESEL, 3);
		Machines.pipeIn(level, oven);
		check(oven.diesel == 3 && dieselTank.amount == 0, "it drinks diesel from a tank next to it");

		rig.ores.clearContent();
		rig.ores.addItem(new ItemStack(Items.RAW_IRON, 10));
		rig.ores.addItem(new ItemStack(Items.DIAMOND, 3));
		rig.feedPipes(level);
		check(count(oven.input, Items.RAW_IRON) == 10 && count(oven.input, Items.DIAMOND) == 0 && count(rig.ores, Items.DIAMOND) == 3,
			"the rig sends its raw iron down the pipe to the oven, and keeps the diamonds");
		oven.input.addItem(new ItemStack(Items.COBBLESTONE, 4));
		for (int i = 0; i < 40; i++) {
			oven.tick(level);
		}
		// every second the oven moves output into the smoker's own bottom slot, where a hopper underneath takes it
		check(level.getBlockEntity(at) instanceof Container box && count(box, Items.IRON_INGOT) + count(box, Items.STONE) > 0,
			"output waits in the smoker's own bottom slot for a hopper");
		Container own = (Container) level.getBlockEntity(at);
		int iron = count(oven.output, Items.IRON_INGOT) + count(own, Items.IRON_INGOT);
		int stoneOut = count(oven.output, Items.STONE) + count(own, Items.STONE);
		check(iron == 20 && stoneOut == 4 && oven.input.isEmpty(), "14 items smelted in 40 ticks: 20 iron ingots and 4 stone (" + iron + ", " + stoneOut + ")");
		check(oven.diesel == 2 && oven.charge == FossilConfig.get().ovenItemsPerDiesel - 14, "on one bucket of diesel");
		oven.on = false;
		log("industrial oven");
	}

	/** A rig set up on the seabed builds a deck and a cofferdam, pumps it dry and drills on down. */
	private static void offshore(MinecraftServer server, ServerLevel level, Rig landRig) {
		int x = SEABED.getX();
		int y = SEABED.getY();
		int z = SEABED.getZ();
		Cmd.run(level, "fill " + (x - 10) + " 40 " + (z - 6) + " " + (x + 10) + " 80 " + (z + 6) + " minecraft:stone");
		Cmd.run(level, "fill " + (x - 9) + " " + (y + 1) + " " + (z - 5) + " " + (x + 9) + " " + (y + 10) + " " + (z + 5) + " minecraft:water");
		Cmd.run(level, "fill " + (x - 10) + " 81 " + (z - 6) + " " + (x + 10) + " 92 " + (z + 6) + " minecraft:air");
		Cmd.run(level, "setblock " + (x + 1) + " " + (y + 1) + " " + (z + 1) + " minecraft:sand");
		Rig sea = Rigs.place(level, null, SEABED);
		check(sea != null && sea.offshore() && sea.deck == y + 10 && sea.top == y && !sea.drained(), "a rig on the seabed goes offshore");
		check(sea.modelY() == y + 11 && sea.root != null, "the derrick stands on the deck, at the surface");
		check(!level.getBlockState(new BlockPos(x - 4, y + 10, z)).isAir() && !Rig.isWater(level.getBlockState(new BlockPos(x - 4, y + 10, z))),
			"a plank deck around the shaft");
		check(level.getBlockState(new BlockPos(x + 3, y + 5, z)).is(Blocks.COBBLESTONE)
			&& level.getBlockState(new BlockPos(x - 3, y + 2, z + 3)).is(Blocks.COBBLESTONE), "a cobblestone cofferdam down to the seabed");
		check(Rig.isWater(level.getBlockState(new BlockPos(x + 4, y + 5, z))), "the sea stays outside it");
		check(sea.isHopperSpot(new BlockPos(x - 3, y + 11, z)) && !sea.isHopperSpot(new BlockPos(x - 3, y + 1, z)), "hoppers and pipes go on the deck");
		sea.firebox.setItem(0, OilItems.diesel(2));
		waitFor(server, "the offshore rig pumps the cofferdam dry and drills two layers", 1200, () -> sea.drained() && sea.layer <= y - 2, () -> {
			for (int dy = 1; dy <= 10; dy++) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						check(!Rig.isWater(level.getBlockState(new BlockPos(x + dx, y + dy, z + dz))), "no water left in the cofferdam");
					}
				}
			}
			check(level.getBlockState(new BlockPos(x + 1, y + 1, z + 1)).isAir() && count(sea.stone, Items.SAND) >= 1,
				"a lump of sand in the cofferdam was dug out into the stone hold");
			check(level.getBlockState(new BlockPos(x, y + 5, z - 2)).is(Blocks.LADDER), "a ladder down from the deck");
			check(level.getBlockState(new BlockPos(x, y - 1, z)).isAir() && level.getBlockState(new BlockPos(x + 3, y - 1, z)).is(Blocks.STONE),
				"and it drills the seabed like any rig");
			sea.on = false;
			log("offshore rig");
			upgrades(server, level, landRig);
		});
	}

	/** Rig Workshop: a rig widened to 7×7 mid-shaft keeps its ladder in line; bigger holds, faster engine, better fuel. */
	private static void upgrades(MinecraftServer server, ServerLevel level, Rig landRig) {
		BlockPos at = new BlockPos(0, 80, 25);
		Cmd.run(level, "fill -8 60 17 8 80 33 minecraft:stone");
		Cmd.run(level, "fill -8 81 17 8 92 33 minecraft:air");
		Rig big = Rigs.place(level, null, at);
		check(big != null && big.cells() == 25 && big.ores.getContainerSize() == Rig.HOLD_PAGE, "a fresh rig is 5×5 with 18-slot holds");
		check(Workshop.blocked(Workshop.SIZE, big) == null, "nothing near it stops it getting wider");
		big.firebox.setItem(0, OilItems.diesel(4));
		big.on = true;
		waitFor(server, "the rig drills its first layer at 5×5", 400, () -> big.layer <= 79, () -> {
			big.ores.setItem(5, new ItemStack(Items.RAW_GOLD, 7));
			Workshop.apply(level, big, Workshop.HOLD, 2);
			check(big.ores.getContainerSize() == 3 * Rig.HOLD_PAGE && count(big.ores, Items.RAW_GOLD) == 7, "deep bins: 54-slot holds that keep what was in them");
			Window page2 = new Window(() -> big.ores, () -> Rig.HOLD_PAGE, Rig.HOLD_PAGE);
			big.ores.setItem(Rig.HOLD_PAGE + 3, new ItemStack(Items.RAW_COPPER, 2));
			check(page2.getItem(3).is(Items.RAW_COPPER) && page2.getContainerSize() == Rig.HOLD_PAGE, "the second page of the hold shows slots 18 to 35");
			Workshop.apply(level, big, Workshop.SPEED, 3);
			Workshop.apply(level, big, Workshop.EFFICIENCY, 1);
			check(Workshop.speedFactor(big.speedLevel) == 1.75 && Workshop.fuelBonus(big.effLevel) == 0.2, "diamond drill head +75%, lagged boiler +20%");
			Workshop.apply(level, big, Workshop.SIZE, 1);
			check(big.cells() == 49 && big.half() == 3 && big.reach() == 5 && big.root != null && !big.root.isRemoved()
				&& big.root.getPassengers().size() == 1 + Rig.FRAME.size() + 1 + Rig.HEAD.size(), "the wide bit: a 7×7 shaft and a bigger derrick");
			check(big.isHopperSpot(new BlockPos(-4, 81, 25)) && !big.isHopperSpot(new BlockPos(-3, 81, 25)), "hoppers go around the wider shaft");
			int layer = big.layer;
			waitFor(server, "the widened rig drills two 7×7 layers", 600, () -> big.layer <= layer - 2, () -> {
				int y = layer;
				check(level.getBlockState(new BlockPos(3, y, 28)).isAir() && level.getBlockState(new BlockPos(-3, y, 22)).isAir(), "the shaft is 7×7");
				check(level.getBlockState(new BlockPos(4, y, 25)).is(Blocks.STONE), "and not wider");
				check(level.getBlockState(new BlockPos(3, 80, 28)).is(Blocks.STONE), "above the upgrade, it stayed 5×5");
				check(level.getBlockState(new BlockPos(0, y, 23)).is(Blocks.LADDER) && level.getBlockState(new BlockPos(0, 80, 23)).is(Blocks.LADDER)
					&& level.getBlockState(new BlockPos(0, y, 22)).is(Blocks.COBBLESTONE), "the ladder runs on in one line, on a cobblestone spine");
				ItemStack packed = OilItems.rig(big);
				check(OilItems.isRig(packed) && OilItems.data(packed).getIntOr("size", 0) == 1 && OilItems.data(packed).getIntOr("speed", 0) == 3,
					"a packed-up rig keeps its upgrades");
				Rig probe = new Rig("", big.dimension, 0, 0, 0);
				Workshop.read(probe, OilItems.data(packed));
				check(probe.half() == 3 && probe.holdLevel == 2 && probe.ores.getContainerSize() == 54, "and brings them back when it's set up again");
				big.on = false;
				log("rig workshop");
				saveAndLoad(server, level, landRig);
			});
		});
	}

	private static void saveAndLoad(MinecraftServer server, ServerLevel level, Rig before) {
		int rigs = Rigs.BY_ID.size();
		int tanks = Machines.TANKS.size();
		int refineries = Machines.REFINERIES.size();
		int pockets = Pockets.OPENED.size();
		int pipes = Pipes.count();
		int ovens = Machines.OVENS.size();
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
		Tank far = Machines.tankAt(level, FAR_TANK);
		check(far != null && far.set == Fluid.LAVA && far.amount == 2 && Pipes.count() == pipes && pipes == 21,
			"the Lava Tank remembers its setting, and the pipes are all still there");
		check(Pockets.isOil(level, HAND_POCKET), "opened pockets remember their oil");
		check(Machines.OVENS.size() == ovens && ovens == 1 && count(Machines.OVENS.values().iterator().next().output, Items.STONE)
			+ count(Machines.OVENS.values().iterator().next().output, Items.IRON_INGOT) > 0, "the oven and its output survive a save and load");
		Rig sea = null;
		for (Rig r : Rigs.BY_ID.values()) {
			if (r.offshore()) {
				sea = r;
			}
		}
		check(sea != null && sea.deck == SEABED.getY() + 10 && sea.drained(), "the offshore rig remembers its deck and its dry cofferdam");
		Rigs.packUp(sea, null);
		Rig big = null;
		for (Rig r : Rigs.BY_ID.values()) {
			if (r.sizeLevel > 0) {
				big = r;
			}
		}
		check(big != null && big.half() == 3 && big.speedLevel == 3 && big.effLevel == 1 && big.holdLevel == 2 && big.ores.getContainerSize() == 54
			&& count(big.ores, Items.RAW_GOLD) == 7, "the upgraded rig keeps its upgrades and its 54-slot holds through a save and load");
		Rigs.packUp(big, null);
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
		ItemStack full = OilItems.tank(Fluid.NONE, Fluid.DIESEL, 40);
		check(OilItems.isTank(full) && OilItems.data(full).getIntOr(OilItems.AMOUNT, 0) == 40, "a packed tank keeps its oil");
		ItemStack lava = OilItems.tank(Fluid.LAVA);
		check(OilItems.isTank(lava) && OilItems.data(lava).getStringOr(OilItems.SET, "").equals("LAVA"), "a Lava Tank is a tank set to lava");
		check(OilItems.isOven(OilItems.oven()) && !OilItems.isOven(new ItemStack(Items.SMOKER))
			&& OilItems.data(OilItems.oven(5)).getIntOr(OilItems.DIESEL_IN, 0) == 5, "Industrial Ovens, and a packed one keeps its diesel");
		check(Oven.doubles(new ItemStack(Items.RAW_IRON)) && !Oven.doubles(new ItemStack(Items.COBBLESTONE)), "raw iron smelts double, cobblestone doesn't");
		check(OilItems.isPipe(OilItems.pipe(4)) && OilItems.pipe(4).getCount() == 4 && !OilItems.isPipe(new ItemStack(OilItems.pipeBase())),
			"pipes, and a plain lightning rod isn't one");
		check(OilItems.fluidOf(new ItemStack(Items.WATER_BUCKET)) == Fluid.WATER && OilItems.fluidOf(new ItemStack(Items.LAVA_BUCKET)) == Fluid.LAVA
			&& OilItems.fluidOf(new ItemStack(Items.BUCKET)) == Fluid.NONE, "water and lava buckets pour into tanks");
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
		check(Hooks.price(OilItems.crude(1)) == 4000 && Hooks.price(OilItems.diesel(1)) == 10000, "crude ₥40, diesel ₥100");
		check(Hooks.price(OilItems.oven()) == -1, "ovens don't sell either");
		check(Hooks.price(OilItems.rig()) == -1 && Hooks.price(new ItemStack(Items.PAPER)) == null, "machines don't sell, paper isn't ours");
		Object hooks = FabricLoader.getInstance().getObjectShare().get(Hooks.PRICE_HOOKS);
		check(hooks instanceof List<?> list && !list.isEmpty(), "the Market price hook is published");
		check(FabricLoader.getInstance().getObjectShare().get(FossilFoolApi.KEY) instanceof java.util.function.BiFunction<?, ?, ?>,
			"fossilfool:api is published (Colonycraft's Fuel Depot uses it)");
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
		check(Pockets.isCrudeBlock(level.getBlockState(HAND_POCKET)) && Pockets.crudeBlock() != Blocks.COAL_BLOCK, "crude looks like crude (black concrete)");
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
