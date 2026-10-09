package com.thatcoffeelock.mobilehome;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dmobilehome.smokeTest=true (CI). Boots a real server, parks a van and a tank on a
 * test pad, drives them around with fake controls, packs one up, then shuts the server down.
 */
final class SmokeTest {
	private static Vehicle van;
	private static Vehicle tank;
	private static double startZ;
	private static int displaysBefore;

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -48 -48 48 48");
			MobileHomeMod.later(100, () -> step(server, () -> build(level)));
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private interface Step {
		void run() throws Exception;
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void build(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Cmd.run(level, "fill -30 99 -30 30 99 60 minecraft:stone");
		for (int y = 100; y < 115; y += 5) {
			Cmd.run(level, "fill -30 " + y + " -30 30 " + (y + 4) + " 60 minecraft:air"); // fill is capped at 32768 blocks
		}
		displaysBefore = countParts(level);

		check(Fuel.burnTicks(level, new ItemStack(Items.COAL)) == 1600, "coal burns for 1600 ticks");
		check(Fuel.burnTicks(level, new ItemStack(Items.LAVA_BUCKET)) > 10_000, "lava is fuel");
		check(Fuel.burnTicks(level, new ItemStack(Items.DIAMOND)) == 0, "diamonds are not fuel");

		VehicleData vanData = new VehicleData(VehicleType.VAN);
		vanData.owner = "00000000-0000-0000-0000-000000000000";
		vanData.ownerName = "SmokeTest";
		van = Vehicles.spawn(level, vanData, 0.5, 100, 0.5, 0f);
		check(van != null, "van spawned");
		check(van.root.getPassengers().size() == VehicleType.VAN.parts().size() + 1, "van has all " + VehicleType.VAN.parts().size() + " model parts + hitbox");
		check(van.root.getPassengers().stream().anyMatch(e -> e instanceof Interaction), "van has a hitbox");
		check(van.root.getAttached(MobileHomeMod.DATA) == vanData, "van data attached");

		tank = Vehicles.spawn(level, new VehicleData(VehicleType.TANK), 0.5, 106, -12.5, 90f);
		check(tank != null, "tank spawned (in mid-air)");
		MobileHomeMod.later(5, () -> step(server, () -> seats(level)));
	}

	private static void seats(ServerLevel level) {
		MinecraftServer server = level.getServer();
		for (Vehicle v : new Vehicle[] {van, tank}) {
			for (int i = 0; i < v.seats.length; i++) {
				check(v.seats[i] != null && !v.seats[i].isRemoved(), v.type.id + " seat " + i + " exists");
			}
		}
		// a Sellswords mercenary following its captain aboard gets a passenger seat (an armor stand stands in for one)
		java.util.UUID guestId = java.util.UUID.randomUUID();
		Cmd.run(level, "summon minecraft:armor_stand " + Cmd.pos(van.root.getX(), van.root.getY() + 1, van.root.getZ()) + " {" + Cmd.uuidNbt(guestId) + ",NoGravity:1b}");
		net.minecraft.world.entity.Entity guest = level.getEntity(guestId);
		check(guest != null && van.seatGuest(guest) && guest.getVehicle() != null, "a guest (a mercenary) gets a passenger seat");
		guest.stopRiding();
		guest.discard();
		check(net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get("sellswords:board") instanceof java.util.List<?> hooks && !hooks.isEmpty(),
			"the boarding hook for Sellswords is registered");
		int before = van.data.fuel;
		check(van.feed(new ItemStack(Items.COAL, 3)).isEmpty(), "all 3 coal eaten");
		check(van.data.fuel - before == 3 * 1600 * VehicleType.FUEL_EFFICIENCY, "coal goes in the tank");
		ItemStack bucket = van.feed(new ItemStack(Items.LAVA_BUCKET));
		check(bucket.is(Items.BUCKET), "lava bucket gives the bucket back");
		check(van.feed(new ItemStack(Items.DIAMOND)).getCount() == 1, "diamonds are refused");

		startZ = van.root.getZ();
		van.testControls = new Vehicle.Controls(true, false, false, false, false, false);
		MobileHomeMod.later(40, () -> step(server, () -> drive(level)));
	}

	private static void drive(ServerLevel level) {
		MinecraftServer server = level.getServer();
		double moved = van.root.getZ() - startZ;
		check(moved > 3, "van drove forward (" + String.format("%.2f", moved) + " blocks south)");
		check(Math.abs(van.root.getY() - 100) < 0.1, "van stays on the ground (y=" + van.root.getY() + ")");
		check(Math.abs(tank.root.getY() - 100) < 0.1, "tank fell and landed (y=" + tank.root.getY() + ")");
		double seatZ = van.seats[0].getZ();
		check(Math.abs(seatZ - (van.root.getZ() + VehicleType.VAN.seats[0].z())) < 0.01, "driver seat follows the van");

		// a one-block step up onto a plateau ahead: the van should climb it
		int wallZ = (int) Math.floor(van.root.getZ() + VehicleType.VAN.halfLength + 2);
		Cmd.run(level, "fill -6 100 " + wallZ + " 6 100 60 minecraft:stone");

		tank.data.fuel = 10_000;
		tank.testControls = new Vehicle.Controls(false, false, true, false, false, false);
		MobileHomeMod.later(40, () -> step(server, () -> climb(level)));
	}

	private static void climb(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(van.root.getY() > 100.9, "van climbed the step (y=" + van.root.getY() + ")");
		check(Math.abs(tank.root.getYRot() - 90f) > 20, "tank turned on the spot (yaw=" + tank.root.getYRot() + ")");
		check(tank.root.getPassengers().stream().allMatch(e -> e.getYRot() == tank.root.getYRot()), "tank model turned with it");
		van.testControls = null;
		tank.testControls = null;

		// pack up and unpack, storage and fuel must survive
		van.data.storage.setItem(7, new ItemStack(Items.DIAMOND, 5));
		int fuel = van.data.fuel;
		ItemStack kit = Kits.packed(level, van.data);
		van.remove();
		check(Kits.type(kit) == VehicleType.VAN, "packed kit is a van");
		VehicleData restored = Kits.savedData(kit, level);
		check(restored != null && restored.fuel == fuel, "fuel survives packing");
		check(restored.storage.getItem(7).is(Items.DIAMOND) && restored.storage.getItem(7).getCount() == 5, "storage survives packing");
		tank.remove();
		MobileHomeMod.later(5, () -> step(server, () -> cleanup(level)));
	}

	private static void cleanup(ServerLevel level) {
		check(countParts(level) == displaysBefore, "packing up removes every entity (" + countParts(level) + " left, expected " + displaysBefore + ")");
		check(Vehicles.all().isEmpty(), "no vehicles left registered");
		MobileHomeMod.LOG.info("MOBILE HOME SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		MobileHomeMod.LOG.error("MOBILE HOME SMOKE TEST FAILED", t);
		server.halt(false);
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

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		MobileHomeMod.LOG.info("[smoke] ok: {}", what);
	}
}
