package com.thatcoffeelock.blimey;

import java.util.Locale;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Only runs with -Dblimey.smokeTest=true (CI). Boots a real server, builds a stone arena high in the sky, unfolds an
 * airship, checks it won't fly on an empty tank, fuels it, flies it (climb, cruise, hover, turn), bombs the arena floor
 * from the air and with a fuse, runs it dry so it glides down, refits it, folds it up and checks nothing was lost.
 */
final class SmokeTest {
	private static final int FLOOR = 150;
	private static final double KEEL = FLOOR + 1;
	private static Airship ship;
	private static int entitiesBefore;
	private static double fuelAfterClimb;

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -56 -56 56 56");
		BlimeyMod.later(100, () -> step(server, () -> arena(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			BlimeyMod.LOG.error("BLIMEY SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void later(ServerLevel level, int ticks, Step step) {
		BlimeyMod.later(ticks, () -> step(level.getServer(), step));
	}

	private static void arena(ServerLevel level) {
		// a stone floor 97 × 97 at y 150 with 40 blocks of open sky above it
		Cmd.run(level, "fill -48 " + FLOOR + " -48 48 " + FLOOR + " 48 minecraft:stone");
		for (int y = FLOOR + 1; y <= FLOOR + 40; y += 3) {
			Cmd.run(level, "fill -48 " + y + " -48 48 " + Math.min(FLOOR + 40, y + 2) + " 48 minecraft:air");
		}
		entitiesBefore = count(level);

		check(Airship.fits(level, 0.5, KEEL, -8, 0), "the arena has room for an airship");
		check(!Airship.fits(level, 0.5, FLOOR - 1, -8, 0), "an airship can't sit inside the stone");
		check(BlimeyItems.isAirship(BlimeyItems.airship()) && !BlimeyItems.isAirship(new ItemStack(Items.PAPER)), "the Flat-Pack Airship is recognised, plain paper isn't");
		for (BlimeyItems.BombKind kind : BlimeyItems.BombKind.values()) {
			check(BlimeyItems.bombKind(BlimeyItems.bomb(kind, 1)) == kind, kind.title + " is recognised");
		}
		check(BlimeyItems.bombKind(new ItemStack(Items.FIREWORK_STAR)) == null, "a plain firework star is no bomb");
		check(BlimeyItems.isDiesel(BlimeyItems.diesel(1)) && !BlimeyItems.isDiesel(new ItemStack(Items.LAVA_BUCKET)), "diesel is recognised by Fossil Fool's tag; lava isn't diesel");
		check(BlimeyConfig.get().smallBombPower < BlimeyConfig.get().bigBombPower
			&& BlimeyConfig.get().bigBombPower < BlimeyConfig.get().hugeBombPower, "bigger bombs are bigger");

		AirshipData data = new AirshipData();
		data.cargoA.setItem(0, new ItemStack(Items.DIAMOND, 3));
		ship = Airships.launch(level, data, 0.5, KEEL, -8, 0);
		check(ship != null, "airship unfolded");
		int parts = ship.root.getPassengers().size();
		check(parts == AirshipModel.parts().size() + Airship.PLATES, "the airship model has " + parts + " parts");
		check(ship.root.getAttached(BlimeyMod.DATA) == data, "airship data attached");
		check(Airships.near(level, ship.root.position(), 4).contains(ship), "the airship can be found");
		check(data.holds().size() == 2 && data.capacity() == 108, "two 54-slot holds to start with");

		// no diesel: Space does nothing
		ship.testControls = new Airship.Controls(false, false, false, false, true, false);
		later(level, 40, () -> noFuel(level));
	}

	private static void noFuel(ServerLevel level) {
		check(ship.markerCount() >= AirshipModel.HITBOX_Z.length, "the hitboxes were made");
		check(ship.isGrounded() && !ship.burning, "with an empty tank it stays on the ground");
		check(Math.abs(ship.y - KEEL) < 0.01, "at the height it was unfolded (" + fmt(ship.y) + ")");

		ship.data.tank.setItem(0, BlimeyItems.diesel(2));
		later(level, 40, () -> climbed(level));
	}

	private static void climbed(ServerLevel level) {
		check(ship.y > KEEL + 3, "with diesel in the tank it climbs (" + fmt(ship.y) + ")");
		check(!ship.isGrounded() && ship.burning, "it's airborne and the engines run");
		check(ship.data.dieselAboard() == 1, "the engines took one bucket of diesel (" + ship.data.dieselAboard() + " left)");
		boolean bucket = false;
		for (int i = 0; i < AirshipData.TANK; i++) {
			bucket |= ship.data.tank.getItem(i).is(Items.BUCKET);
		}
		check(bucket, "and gave back an empty bucket");
		double perBucket = BlimeyConfig.get().ticksPerBucket;
		check(ship.data.fuel > 0 && ship.data.fuel < perBucket, "and are burning through it (" + fmt(ship.data.fuel) + " of " + fmt(perBucket) + ")");
		fuelAfterClimb = ship.data.fuel;
		ship.testControls = new Airship.Controls(true, false, false, false, false, false);
		double z = ship.root.getZ();
		later(level, 30, () -> cruised(level, z));
	}

	private static void cruised(ServerLevel level, double z) {
		check(ship.root.getZ() - z > 1.5, "it flies forward (" + fmt(ship.root.getZ() - z) + " blocks)");
		check(ship.data.fuel < fuelAfterClimb, "burning diesel all the way");
		ship.testControls = Airship.Controls.NONE;
		double y = ship.y;
		later(level, 40, () -> hovered(level, y));
	}

	private static void hovered(ServerLevel level, double y) {
		check(Math.abs(ship.y - y) < 1.0 && !ship.isGrounded(), "with nobody touching anything it holds its height (" + fmt(y) + " → " + fmt(ship.y) + ")");
		check(ship.speed < 0.2, "and slows down (" + fmt(ship.speed) + ")");
		ship.testControls = new Airship.Controls(false, true, false, true, false, false);
		later(level, 30, () -> turned(level));
	}

	private static void turned(ServerLevel level) {
		check(Math.abs(ship.root.getYRot()) > 5, "it turns (yaw " + fmt(ship.root.getYRot()) + ")");
		check(ship.root.getPassengers().stream().allMatch(e -> e.getYRot() == ship.root.getYRot()), "and the model turns with it");
		ship.testControls = new Airship.Controls(false, true, false, false, false, false);
		later(level, 30, () -> bombsAway(level));
	}

	/** A small bomb dropped from the hatch blows a hole in the stone floor (a cannonball wouldn't). */
	private static void bombsAway(ServerLevel level) {
		ship.testControls = Airship.Controls.NONE;
		FakePlayer player = FakePlayer.get(level);
		int holesBefore = holes(level);
		ItemStack bombs = BlimeyItems.bomb(BlimeyItems.BombKind.SMALL, 2);
		String why = ship.dropBomb(player, bombs);
		check(why == null, "a bomb drops out of the hatch (" + why + ")");
		check(bombs.getCount() == 1, "and it came out of the stack in hand");
		check(Bomb.live().size() == 1, "one bomb is falling");
		later(level, 80, () -> {
			check(Bomb.live().isEmpty(), "the bomb landed and went off");
			int holesAfter = holes(level);
			check(holesAfter > holesBefore + 3, "it blew a hole in the stone floor (" + holesBefore + " → " + holesAfter + " missing blocks)");
			check(!ship.isRemoved() && !ship.isGrounded(), "the airship is fine, thanks");
			fuse(level);
		});
	}

	/** A bomb set on the ground by hand waits for its fuse, then goes off. */
	private static void fuse(ServerLevel level) {
		int holesBefore = holes(level);
		Bomb bomb = Bomb.launch(level, BlimeyItems.BombKind.BIG, new Vec3(-30.5, KEEL, 30.5), Vec3.ZERO, 30, null);
		check(bomb != null, "a big bomb is set on the floor with a 1.5 s fuse");
		later(level, 15, () -> {
			check(!bomb.isDone() && Math.abs(bomb.pos.y - KEEL) < 0.01, "halfway through the fuse it's still sitting there, fizzing");
			later(level, 30, () -> {
				check(bomb.isDone() && Bomb.live().isEmpty(), "then it goes off");
				check(level.getBlockState(new BlockPos(-31, FLOOR, 30)).isAir(), "and takes the floor under it with it");
				check(holes(level) > holesBefore + 4, "it blew a hole of its own (" + holesBefore + " → " + holes(level) + ")");
				dry(level);
			});
		});
	}

	/** Empty the tank and the engines: it sinks down gently and lands. */
	private static void dry(ServerLevel level) {
		ship.data.tank.clearContent();
		ship.data.fuel = 0;
		ship.testControls = new Airship.Controls(true, false, false, false, false, false);
		later(level, 200, () -> {
			check(ship.isGrounded() && !ship.burning, "out of diesel, it glided down and landed (" + fmt(ship.y) + ")");
			check(ship.speed == 0 || Math.abs(ship.speed) < 0.05, "and stopped");
			ship.testControls = null;
			grounded(level);
		});
	}

	private static void grounded(ServerLevel level) {
		FakePlayer player = FakePlayer.get(level);
		ItemStack bomb = BlimeyItems.bomb(BlimeyItems.BombKind.HUGE, 1);
		String why = ship.dropBomb(player, bomb);
		check(why != null && bomb.getCount() == 1 && Bomb.live().isEmpty(), "no bombing from a parked airship (" + why + ")");
		engineer(level);
	}

	/** The Engineer refits only when the captain has the materials, and takes them. */
	private static void engineer(ServerLevel level) {
		FakePlayer player = FakePlayer.get(level);
		player.getInventory().clearContent();
		check(ship.data.speedLevel == 0 && Engineer.speedFactor(0) == 1.0, "a new airship has stock engines");
		String why = Engineer.upgrade(player, ship, Engineer.SPEED);
		check(why != null && why.startsWith("You need") && ship.data.speedLevel == 0, "no refit without materials (" + why + ")");
		for (Engineer.Track track : Engineer.TRACKS) {
			for (int level1 = 1; level1 <= track.max(); level1++) {
				for (Engineer.Cost cost : track.levels().get(level1).costs()) {
					check(cost.item() != Items.BARRIER, cost.id() + " exists in this Minecraft");
				}
			}
		}
		for (Engineer.Cost cost : Engineer.SPEED.levels().get(1).costs()) {
			player.getInventory().add(new ItemStack(cost.item(), cost.count()));
		}
		why = Engineer.upgrade(player, ship, Engineer.SPEED);
		check(why == null && ship.data.speedLevel == 1, "with the materials the engines get Bigger propellers (" + why + ")");
		check(Engineer.count(player, Engineer.item("minecraft:piston")) == 0, "and the materials are used up");
		check(Engineer.speedFactor(1) > 1.0 && Engineer.burnFactor(Engineer.MAX_LEVEL) < Engineer.burnFactor(0), "refits make it faster and thriftier");
		for (Engineer.Cost cost : Engineer.CARGO.levels().get(1).costs()) {
			player.getInventory().add(new ItemStack(cost.item(), cost.count()));
		}
		why = Engineer.upgrade(player, ship, Engineer.CARGO);
		check(why == null && ship.data.holds().size() == 3, "the Third hold refit adds Cargo C (" + why + ")");
		ship.data.cargoC.setItem(7, new ItemStack(Items.EMERALD, 5));
		ship.data.tank.setItem(3, BlimeyItems.diesel(3));
		folded(level);
	}

	private static void folded(ServerLevel level) {
		String name = ship.data.name;
		ItemStack packed = ship.packUp();
		check(BlimeyItems.isAirship(packed), "folded it up");
		AirshipData back = BlimeyItems.savedData(packed, level);
		check(back != null && back.cargoA.getItem(0).is(Items.DIAMOND) && back.cargoA.getItem(0).getCount() == 3, "cargo survives folding");
		check(back.cargoLevel == 1 && back.cargoC.getItem(7).getCount() == 5, "the extra hold and its cargo survive folding");
		check(back.speedLevel == 1, "the engine refit survives folding");
		check(back.dieselAboard() == 3, "the diesel in the tank survives folding");
		check(back.name.equals(name), "the name survives folding (" + back.name + ")");
		later(level, 5, () -> {
			Airship again = Airships.launch(level, back, 0.5, KEEL, -8, 0);
			check(again != null && again.data.speedLevel == 1, "it unfolds again from the item");
			later(level, 5, () -> {
				again.packUp();
				later(level, 5, () -> cleanup(level));
			});
		});
	}

	private static void cleanup(ServerLevel level) {
		check(count(level) == entitiesBefore, "no entities left behind (" + count(level) + " vs " + entitiesBefore + ")");
		check(Airships.all().isEmpty(), "no airships left registered");
		check(Bomb.live().isEmpty(), "no bombs left");
		BlimeyMod.LOG.info("BLIMEY SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	/** Missing blocks in the arena floor. */
	private static int holes(ServerLevel level) {
		int n = 0;
		for (int x = -48; x <= 48; x++) {
			for (int z = -48; z <= 48; z++) {
				if (level.getBlockState(new BlockPos(x, FLOOR, z)).isAir()) {
					n++;
				}
			}
		}
		return n;
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

	private static String fmt(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		BlimeyMod.LOG.info("[smoke] ok: {}", what);
	}
}
