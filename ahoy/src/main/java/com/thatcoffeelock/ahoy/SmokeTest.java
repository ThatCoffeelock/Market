package com.thatcoffeelock.ahoy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Only runs with -Dahoy.smokeTest=true (CI). Boots a real server, digs a test harbour, launches a
 * ship, sets sail, sails and turns, drops anchor, then bottles it up and checks nothing was lost.
 */
final class SmokeTest {
	private static Ship ship;
	private static BlockPos helm;
	private static double startZ;
	private static int entitiesBefore;
	private static final int WATER = 99;

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -48 -48 48 48");
		AhoyMod.later(100, () -> step(server, () -> harbour(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			AhoyMod.LOG.error("AHOY SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void harbour(ServerLevel level) {
		// a stone basin 31 x 51, water 4 deep (y 96..99), open sky above
		Cmd.run(level, "fill -16 95 -26 16 95 26 minecraft:stone");
		for (int y = 96; y <= 116; y += 4) {
			Cmd.run(level, "fill -16 " + y + " -26 16 " + Math.min(116, y + 3) + " 26 minecraft:air");
		}
		for (int y = 96; y <= 100; y++) {
			Cmd.run(level, "fill -16 " + y + " -26 16 " + y + " -26 minecraft:stone");
			Cmd.run(level, "fill -16 " + y + " 26 16 " + y + " 26 minecraft:stone");
			Cmd.run(level, "fill -16 " + y + " -26 -16 " + y + " 26 minecraft:stone");
			Cmd.run(level, "fill 16 " + y + " -26 16 " + y + " 26 minecraft:stone");
		}
		Cmd.run(level, "fill -15 96 -25 15 99 25 minecraft:water");
		entitiesBefore = count(level);

		ShipData data = new ShipData(ShipTemplate.blocks());
		check(data.blocks.size() > 300, "template has " + data.blocks.size() + " blocks");
		BlockPos origin = new BlockPos(0, WATER, -8);
		check(Ship.canPlace(level, data.blocks, origin, 0, true), "the harbour is deep enough");
		check(!Ship.canPlace(level, data.blocks, new BlockPos(0, WATER, 20), 0, true), "no launching into a wall");
		ship = Ships.launch(level, data, origin, 0);
		check(ship != null, "ship launched");
		helm = ship.helmPos();
		check(level.getBlockState(helm).is(Blocks.GRINDSTONE), "the wheel is a real grindstone");
		check(level.getBlockState(origin.offset(0, 1, 0)).is(Blocks.SPRUCE_PLANKS), "deck is real planks you can walk on");
		check(level.getBlockState(origin.offset(0, -1, 0)).isAir(), "the hold is dry");
		check(Ships.anchoredAt(level, helm) == ship, "anchored blocks are indexed");
		check(ship.cargoBayAt(origin.offset(2, -1, 0)) == 0, "port barrels are cargo bay A");

		// something in the stove should end up in the cargo when we set sail
		BlockPos stove = Ship.toWorld(origin, 0, -2, -1, -5);
		check(level.getBlockEntity(stove) instanceof Container, "the stove is a real furnace");
		((Container) level.getBlockEntity(stove)).setItem(1, new ItemStack(Items.COAL, 7));
		ship.data.cargoA.setItem(0, new ItemStack(Items.DIAMOND, 3));
		int blocks = ship.data.blocks.size();

		check(ship.setSail(null), "set sail");
		check(ship.data.sailing, "ship is sailing");
		check(level.getBlockState(helm).isAir() || level.getFluidState(helm).isEmpty(), "the wheel left the harbour");
		check(level.getFluidState(origin.offset(0, -1, 0)).isSource(), "the sea is back where the hold was");
		check(ship.root.getPassengers().size() > 100, "sailing model has " + ship.root.getPassengers().size() + " displays");
		check(ship.data.blocks.size() == blocks, "layout kept (" + ship.data.blocks.size() + " blocks)");
		int coal = 0;
		for (int i = 0; i < ShipData.BAY; i++) {
			if (ship.data.cargoA.getItem(i).is(Items.COAL)) {
				coal += ship.data.cargoA.getItem(i).getCount();
			}
		}
		check(coal == 7, "the stove's coal went to the cargo hold");

		startZ = ship.root.getZ();
		ship.testControls = new Ship.Controls(true, false, false, false, false);
		AhoyMod.later(80, () -> step(level.getServer(), () -> sailed(level)));
	}

	private static void sailed(ServerLevel level) {
		double moved = ship.root.getZ() - startZ;
		check(moved > 2, "ship sailed forward (" + String.format("%.2f", moved) + " blocks)");
		ship.testControls = new Ship.Controls(false, false, true, false, false);
		AhoyMod.later(30, () -> step(level.getServer(), () -> turned(level)));
	}

	private static void turned(ServerLevel level) {
		check(Math.abs(ship.root.getYRot()) > 5, "ship turned (yaw=" + ship.root.getYRot() + ")");
		ship.testControls = null;
		ship.speed = 0;
		check(ship.dropAnchor(), "dropped anchor");
		check(!ship.data.sailing, "ship is anchored");
		BlockPos newHelm = ship.helmPos();
		check(level.getBlockState(newHelm).is(Blocks.GRINDSTONE), "the wheel is back as a real block at " + newHelm.toShortString());
		check(Ships.anchoredAt(level, newHelm) == ship, "the new spot is indexed");

		ItemStack bottle = ship.bottleUp(null);
		check(Bottle.isBottle(bottle), "bottled it up");
		check(level.getBlockState(newHelm).isAir(), "the ship left the water");
		ShipData back = Bottle.savedData(bottle, level);
		check(back != null && back.cargoA.getItem(0).is(Items.DIAMOND) && back.cargoA.getItem(0).getCount() == 3, "cargo survives the bottle");
		check(back.blocks.size() > 300, "layout survives the bottle (" + back.blocks.size() + " blocks)");
		AhoyMod.later(5, () -> step(level.getServer(), () -> cleanup(level)));
	}

	private static void cleanup(ServerLevel level) {
		check(count(level) == entitiesBefore, "no entities left behind (" + count(level) + " vs " + entitiesBefore + ")");
		check(Ships.all().isEmpty(), "no ships left registered");
		AhoyMod.LOG.info("AHOY SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static int count(ServerLevel level) {
		int n = 0;
		for (Entity entity : level.getAllEntities()) {
			if ((entity instanceof Display || entity instanceof Interaction) && entity.isAlive()) {
				n++;
			}
		}
		return n;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		AhoyMod.LOG.info("[smoke] ok: {}", what);
	}
}
