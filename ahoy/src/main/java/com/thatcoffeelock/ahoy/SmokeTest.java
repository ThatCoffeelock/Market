package com.thatcoffeelock.ahoy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dahoy.smokeTest=true (CI). Boots a real server, digs a test harbour, launches a
 * ship, sails and turns it, runs it into the wall, then bottles it up and checks nothing was lost.
 */
final class SmokeTest {
	private static Ship ship;
	private static double startZ;
	private static int entitiesBefore;
	private static final double SURFACE = 99.9;

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

		check(Ship.hullFits(level, SURFACE, 0.5, -8, 0) && Ship.afloat(level, SURFACE, 0.5, -8, 0), "the harbour has room for a ship");
		check(!Ship.hullFits(level, SURFACE, 0.5, 22, 0), "no launching into a wall");
		check(!Ship.afloat(level, SURFACE, 0.5, 40, 0), "no launching on land");

		ShipData data = new ShipData();
		data.cargoA.setItem(0, new ItemStack(Items.DIAMOND, 3));
		ship = Ships.launch(level, data, 0.5, SURFACE, -8, 0);
		check(ship != null, "ship launched");
		int parts = ship.root.getPassengers().size();
		check(parts == ShipModel.parts().size() + 2, "ship model has " + parts + " parts (light!)");
		check(ship.root.getAttached(AhoyMod.DATA) == data, "ship data attached");

		startZ = ship.root.getZ();
		ship.testControls = new Ship.Controls(true, false, false, false, false);
		AhoyMod.later(80, () -> step(level.getServer(), () -> sailed(level)));
	}

	private static void sailed(ServerLevel level) {
		double moved = ship.root.getZ() - startZ;
		check(moved > 2, "ship sailed forward (" + String.format("%.2f", moved) + " blocks)");
		check(ship.markerCount() >= ShipModel.HITBOX_Z.length, "hitboxes follow the ship");
		ship.testControls = new Ship.Controls(false, false, true, false, false);
		AhoyMod.later(30, () -> step(level.getServer(), () -> turned(level)));
	}

	private static void turned(ServerLevel level) {
		check(Math.abs(ship.root.getYRot()) > 5, "ship turned (yaw=" + ship.root.getYRot() + ")");
		check(ship.root.getPassengers().stream().allMatch(e -> e.getYRot() == ship.root.getYRot()), "model turned with it");
		// full speed ahead into the harbour wall: it must stop, not sail through
		ship.testControls = new Ship.Controls(true, false, false, false, false);
		AhoyMod.later(200, () -> step(level.getServer(), () -> crashed(level)));
	}

	private static void crashed(ServerLevel level) {
		check(Math.abs(ship.root.getZ()) < 26 && Math.abs(ship.root.getX()) < 16, "ship stayed inside the harbour ("
			+ String.format("%.1f, %.1f", ship.root.getX(), ship.root.getZ()) + ")");
		ship.testControls = null;
		ItemStack bottle = ship.bottleUp();
		check(Bottle.isBottle(bottle), "bottled it up");
		ShipData back = Bottle.savedData(bottle, level);
		check(back != null && back.cargoA.getItem(0).is(Items.DIAMOND) && back.cargoA.getItem(0).getCount() == 3, "cargo survives the bottle");
		check(back.name.equals(ship.data.name), "name survives the bottle (" + back.name + ")");
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
