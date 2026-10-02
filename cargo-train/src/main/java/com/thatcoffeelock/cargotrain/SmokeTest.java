package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dcargotrain.smokeTest=true (CI). Boots a real server and lays an L-shaped line with a curve and
 * a slope: 20 rails east, a corner, 10 rails south, a slope up and 5 more rails. A Pickup Station chest stands
 * near the start, a Drop-off Station barrel at the far end. The train has to pick the cargo up, carry it round
 * the bend and up the hill, drop it off, turn around, survive being unloaded and loaded again, stop short of a
 * rail someone broke, turn around again, park, and get packed up without leaving anything behind.
 */
final class SmokeTest {
	private static final BlockPos PICKUP = new BlockPos(5, 100, 1);
	private static final BlockPos PLAIN = new BlockPos(5, 100, -1);
	private static final BlockPos DROPOFF = new BlockPos(21, 101, 16);
	private static final BlockPos BROKEN = new BlockPos(12, 100, 0);
	private static final int CARGO = 64 + 10 + 1;

	private static Train train;
	private static int entitiesBefore;

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -48 -48 48 48");
			CargoTrainMod.later(100, () -> step(server, () -> build(level)));
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
		CargoTrainMod.later(5, () -> step(server, () -> {
			if (condition.getAsBoolean()) {
				CargoTrainMod.LOG.info("[smoke] ok: {} (after {} ticks)", what, waited + 5);
				then.run();
			} else if (waited + 5 >= maxTicks) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for " + what + " (" + describe() + ")");
			} else {
				poll(server, what, maxTicks, waited + 5, condition, then);
			}
		}));
	}

	private static String describe() {
		if (train == null) {
			return "no train";
		}
		return "status=" + train.status() + " dir=" + train.dir() + " speed=" + train.speed + " loco=" + train.root.blockPosition()
			+ " wagons=" + train.data.wagons() + " last=" + (last() == null ? "none" : last().blockPosition()) + " cargo=" + train.data.itemCount()
			+ " route=" + (train.route == null ? "none" : train.route.size() + " rails, s=" + train.route.s);
	}

	/** One rail of the test line, with the shape vanilla gives it once its neighbours are down. */
	private record Rail(BlockPos pos, String shape) {
	}

	private static List<Rail> line() {
		List<Rail> rails = new ArrayList<>();
		for (int x = 0; x < 20; x++) {
			rails.add(new Rail(new BlockPos(x, 100, 0), "east_west"));
		}
		rails.add(new Rail(new BlockPos(20, 100, 0), "south_west"));
		for (int z = 1; z <= 10; z++) {
			rails.add(new Rail(new BlockPos(20, 100, z), "north_south"));
		}
		rails.add(new Rail(new BlockPos(20, 100, 11), "ascending_south"));
		for (int z = 12; z <= 16; z++) {
			rails.add(new Rail(new BlockPos(20, 101, z), "north_south"));
		}
		return rails;
	}

	private static void build(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Cmd.run(level, "fill -10 99 -10 30 99 30 minecraft:stone");
		for (int y = 100; y < 110; y += 5) {
			Cmd.run(level, "fill -10 " + y + " -10 30 " + (y + 4) + " 30 minecraft:air"); // fill is capped at 32768 blocks
		}
		entitiesBefore = countParts(level);

		check(Stations.parse("Pickup Station") == Stations.Mode.PICKUP, "'Pickup Station' is a pickup station");
		check(Stations.parse("drop off station") == Stations.Mode.DROPOFF, "'drop off station' is a drop-off station");
		check(Stations.parse("SWAP-STATION!") == Stations.Mode.SWAP, "'SWAP-STATION!' is a swap station");
		check(Stations.parse("Pickup") == null && Stations.parse("My Station") == null, "other names aren't stations");
		check(TrainItems.isTrain(TrainItems.train()), "train item is recognised");
		check(TrainItems.isWagon(TrainItems.wagon()) && !TrainItems.isTrain(TrainItems.wagon()), "wagon item is recognised");
		check(!TrainItems.isWagon(new ItemStack(Items.CHEST_MINECART)), "a plain chest minecart is not a wagon");
		check(!TrainItems.isTrain(new ItemStack(Items.FURNACE_MINECART)), "a plain furnace minecart is not a train");

		// lay the line one rail at a time, the way a player would (the shapes are what vanilla would pick anyway)
		Cmd.run(level, "fill 20 100 12 20 100 16 minecraft:stone");
		for (Rail rail : line()) {
			BlockPos p = rail.pos();
			Cmd.run(level, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " minecraft:rail[shape=" + rail.shape() + "]");
		}
		for (Rail rail : line()) {
			check(Track.isRail(level.getBlockState(rail.pos())), "rail at " + rail.pos().toShortString() + " is " + level.getBlockState(rail.pos()));
		}
		Route whole = Route.start(level, new BlockPos(3, 100, 0), -90f, 1);
		check(whole != null, "a route starts at x=3");
		int guard = 0;
		while (!whole.headDead && guard++ < 100) {
			whole.extend(true);
		}
		while (!whole.tailDead && guard++ < 200) {
			whole.extend(false);
		}
		check(whole.size() == line().size(), "the route follows all " + line().size() + " rails round the corner and up the slope (found " + whole.size() + ")");
		check(whole.node(0).pos().equals(new BlockPos(0, 100, 0)), "the route starts at the west end");
		check(whole.node(whole.size() - 1).pos().equals(new BlockPos(20, 101, 16)), "the route ends at the top of the slope");
		check(whole.nodes.stream().anyMatch(Track.Node::slope), "the slope is recognised");

		// stations: a chest at the start, a barrel at the far end, and an ordinary chest that must be left alone
		Cmd.run(level, "setblock " + PICKUP.getX() + " " + PICKUP.getY() + " " + PICKUP.getZ() + " minecraft:chest");
		Cmd.run(level, "setblock " + PLAIN.getX() + " " + PLAIN.getY() + " " + PLAIN.getZ() + " minecraft:chest");
		Cmd.run(level, "setblock " + DROPOFF.getX() + " " + DROPOFF.getY() + " " + DROPOFF.getZ() + " minecraft:barrel");
		Stations.setMode(level, PICKUP, Stations.Mode.PICKUP);
		Stations.setMode(level, DROPOFF, Stations.Mode.DROPOFF);
		check(Stations.modeAt(level, PICKUP) == Stations.Mode.PICKUP, "the chest became a Pickup Station");
		check(Stations.modeAt(level, DROPOFF) == Stations.Mode.DROPOFF, "the barrel became a Drop-off Station");
		check(Stations.modeAt(level, PLAIN) == null, "a plain chest is not a station");
		Container pickup = Stations.container(level, PICKUP);
		pickup.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		pickup.setItem(5, new ItemStack(Items.DIAMOND, 10));
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
		pickup.setItem(9, sword);
		((Container) level.getBlockEntity(PLAIN)).setItem(0, new ItemStack(Items.APPLE, 7));

		swapCheck(level);
		overflowCheck(level);

		TrainData data = new TrainData();
		data.owner = "00000000-0000-0000-0000-000000000000";
		data.ownerName = "SmokeTest";
		train = Trains.spawn(level, data, new BlockPos(3, 100, 0), -90f);
		check(train != null, "train put on the track");
		check(train.root.getPassengers().size() == TrainModel.loco().size() + 1, "locomotive has all its parts and a hitbox");
		check(Math.abs(Mth.wrapDegrees(train.root.getYRot() + 90f)) < 1f, "locomotive faces east (yaw=" + train.root.getYRot() + ")");
		check(train.whyNoCoupling() == null && !train.couple() && train.data.wagons() == 1,
			"no second wagon at the very start of the line: there's no track behind for it");
		CargoTrainMod.later(3, () -> step(server, () -> {
			Entity wagon = train.wagon(0);
			check(wagon != null, "wagon built");
			check(wagon.getPassengers().size() == TrainModel.wagon().size() + TrainModel.CRATES.size() + 1, "wagon has all its parts, crates and a hitbox");
			check(train.seat != null && !train.seat.isRemoved(), "driver's seat built");
			check(wagon.getX() < train.root.getX() - 1.5, "the wagon is behind the locomotive");
			waitFor(server, "the train to stop at the Pickup Station and load everything", 300,
				() -> Stations.container(level, PICKUP).isEmpty() && train.data.itemCount() == CARGO, () -> loaded(level));
		}));
	}

	/** The swap logic on its own: what was in the chest goes on the train, what was on the train goes in the chest. */
	private static void swapCheck(ServerLevel level) {
		BlockPos at = new BlockPos(-5, 100, -5);
		Cmd.run(level, "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " minecraft:chest");
		Stations.setMode(level, at, Stations.Mode.SWAP);
		check(Stations.modeAt(level, at) == Stations.Mode.SWAP, "a chest became a Swap Station");
		Container chest = Stations.container(level, at);
		chest.setItem(3, new ItemStack(Items.APPLE, 5));
		SimpleContainer wagon = new SimpleContainer(TrainData.SLOTS);
		wagon.setItem(0, new ItemStack(Items.BREAD, 3));
		Stations.Visit visit = Stations.serve(level, at, List.of(wagon));
		check(visit != null && visit.loaded() == 5 && visit.unloaded() == 3, "swap moved 5 out and 3 in");
		check(wagon.countItem(Items.APPLE) == 5 && wagon.countItem(Items.BREAD) == 0, "the wagon took the apples and left the bread");
		boolean bread = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			bread |= chest.getItem(i).is(Items.BREAD) && chest.getItem(i).getCount() == 3;
			check(!chest.getItem(i).is(Items.APPLE), "no apples left in the swap chest");
		}
		check(bread, "the bread is in the swap chest");
		check(Stations.Mode.PICKUP.next() == Stations.Mode.DROPOFF && Stations.Mode.SWAP.next() == Stations.Mode.PICKUP, "modes cycle");
	}

	/** Two wagons: when the first is full, a pickup spills over into the second. */
	private static void overflowCheck(ServerLevel level) {
		BlockPos at = new BlockPos(-5, 100, -7);
		Cmd.run(level, "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " minecraft:chest");
		Stations.setMode(level, at, Stations.Mode.PICKUP);
		Container chest = Stations.container(level, at);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(2, new ItemStack(Items.COBBLESTONE, 10));
		SimpleContainer first = new SimpleContainer(TrainData.SLOTS);
		for (int i = 0; i < TrainData.SLOTS - 1; i++) {
			first.setItem(i, new ItemStack(Items.DIRT, 64));
		}
		first.setItem(TrainData.SLOTS - 1, new ItemStack(Items.COBBLESTONE, 60));
		SimpleContainer second = new SimpleContainer(TrainData.SLOTS);
		Stations.Visit visit = Stations.serve(level, at, List.of(first, second));
		check(visit != null && visit.loaded() == 138, "pickup into two wagons loaded all 138 cobblestone (" + (visit == null ? "-" : visit.loaded()) + ")");
		check(first.countItem(Items.COBBLESTONE) == 64, "the first wagon's last stack was topped up to 64");
		check(second.countItem(Items.COBBLESTONE) == 134, "the rest spilled into the second wagon (" + second.countItem(Items.COBBLESTONE) + ")");
		check(chest.isEmpty(), "the pickup chest is empty");
	}

	private static void loaded(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(train.crateCount(0) >= 1, "crates show up in the wagon");
		check(train.dwell > 0 || train.speed > 0, "it's either loading or already on its way");

		// couple a second wagon while it's standing at the station
		if (train.speed == 0) {
			check(train.whyNoCoupling() == null, "coupling is allowed while it stands at a station");
			check(train.couple(), "a second wagon coupled");
		} else {
			check(train.whyNoCoupling() != null, "no coupling while it's moving");
			train.speed = 0;
			check(train.couple(), "a second wagon coupled once it stopped");
		}
		check(train.data.wagons() == 2 && train.data.totalSlots() == 2 * TrainData.SLOTS, "the train has 2 wagons and 54 slots");

		// pretend the chunk unloaded and loaded again: the wagons and seat are rebuilt, and the train finds the track again
		Entity oldWagon = train.wagon(0);
		Entity root = train.root;
		train.unload();
		check(oldWagon.isRemoved(), "unloading removes the wagon");
		check(Trains.all().isEmpty(), "unloaded train is unregistered");
		Trains.onLoad(root, level);
		CargoTrainMod.later(3, () -> step(server, () -> {
			check(Trains.all().size() == 1, "train registered again after loading");
			train = Trains.all().get(0);
			check(train.route != null, "it found the track again");
			check(train.wagon(0) != null && train.wagon(1) != null, "both wagons were built again");
			check(train.wagon(1).getX() < train.wagon(0).getX() - 1.5, "the second wagon is behind the first");
			check(train.data.itemCount() == CARGO, "the cargo survived");
			waitFor(server, "the cargo to reach the Drop-off Station round the corner and up the slope", 800,
				() -> train.data.isEmpty() && countIn(Stations.container(level, DROPOFF)) == CARGO, () -> delivered(level));
		}));
	}

	private static void delivered(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Container barrel = Stations.container(level, DROPOFF);
		boolean excalibur = false;
		for (int i = 0; i < barrel.getContainerSize(); i++) {
			ItemStack stack = barrel.getItem(i);
			excalibur |= stack.is(Items.DIAMOND_SWORD) && stack.getHoverName().getString().equals("Excalibur");
		}
		check(excalibur, "the named sword arrived with its name");
		check(train.data.hauled == CARGO, "hauled counter = " + CARGO + " (" + train.data.hauled + ")");
		check(train.root.getY() > 100.9, "the locomotive climbed the slope (y=" + train.root.getY() + ")");
		check(Math.abs(train.root.getX() - 20.5) < 0.01, "the locomotive is on the north-south leg");
		double gap = train.root.position().distanceTo(train.wagon(0).position());
		check(gap > 1.6 && gap < 2.4, "the first wagon follows at a sensible distance (" + gap + ")");
		double gap2 = train.wagon(0).position().distanceTo(train.wagon(1).position());
		check(gap2 > 1.6 && gap2 < 2.4, "the second wagon follows the first at a sensible distance (" + gap2 + ")");
		waitFor(server, "the train to turn around at the end of the line", 200, () -> train.dir() == -1 && train.speed > 0, () -> {
			// someone breaks a rail on the way back: the train must stop short of the gap and turn around again
			Cmd.run(level, "setblock " + BROKEN.getX() + " " + BROKEN.getY() + " " + BROKEN.getZ() + " minecraft:air");
			watchGap(level, 0);
		});
	}

	private static void watchGap(ServerLevel level, int waited) {
		MinecraftServer server = level.getServer();
		CargoTrainMod.later(5, () -> step(server, () -> {
			check(last() != null && last().getX() > BROKEN.getX() + 0.4, "the train never runs past the broken rail (" + describe() + ")");
			if (train.dir() == 1) {
				CargoTrainMod.LOG.info("[smoke] ok: stopped at the broken rail and turned around (after {} ticks)", waited + 5);
				check(Math.abs(last().getX() - (BROKEN.getX() + 1.5)) < 0.3, "the last wagon stopped right at the gap (x=" + last().getX() + ")");
				check(countIn((Container) level.getBlockEntity(PLAIN)) == 7, "the ordinary chest next to the track was left alone");
				park(level);
			} else if (waited + 5 >= 900) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for the train to turn around at the gap (" + describe() + ")");
			} else {
				watchGap(level, waited + 5);
			}
		}));
	}

	private static void park(ServerLevel level) {
		MinecraftServer server = level.getServer();
		train.data.running = false;
		waitFor(server, "the train to brake and park", 100, () -> train.speed == 0 && train.status().startsWith("Parked"), () -> {
			List<ItemStack> items = Trains.pickUp(train);
			check(items.size() == 2 && TrainItems.isTrain(items.get(0)) && TrainItems.isWagon(items.get(1)),
				"picking it up gives back the train and the extra wagon");
			CargoTrainMod.later(5, () -> step(server, () -> {
				check(Trains.all().isEmpty(), "no trains left registered");
				check(countParts(level) == entitiesBefore, "picking up removes every entity (" + countParts(level) + " left, expected " + entitiesBefore + ")");
				CargoTrainMod.LOG.info("CARGO TRAIN SMOKE TEST PASSED");
				server.halt(false);
			}));
		});
	}

	/** The last wagon (the one that leads on the way back). */
	private static Entity last() {
		return train == null ? null : train.wagon(train.data.wagons() - 1);
	}

	private static int countIn(Container container) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			n += container.getItem(i).getCount();
		}
		return n;
	}

	private static int countParts(ServerLevel level) {
		int n = 0;
		for (Entity entity : level.getAllEntities()) {
			if ((entity instanceof Display || entity instanceof Interaction) && entity.isAlive()) {
				n++;
			}
		}
		return n;
	}

	private static void fail(MinecraftServer server, Throwable t) {
		CargoTrainMod.LOG.error("CARGO TRAIN SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		CargoTrainMod.LOG.info("[smoke] ok: {}", what);
	}
}
