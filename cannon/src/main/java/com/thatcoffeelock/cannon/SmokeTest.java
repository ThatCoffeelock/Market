package com.thatcoffeelock.cannon;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Only runs with -Dcannon.smokeTest=true (CI). Boots a real server, places a cannon on a test pad, swings it
 * around with a fake gunner, shoots a villager standing in front of a dirt wall, checks the villager got hurt and the wall
 * didn't (cannonballs never break blocks), packs up and shuts down.
 */
final class SmokeTest {
	private static final int WALL_Z = 18;

	private static Cannon cannon;
	private static int displaysBefore;
	private static int wallBefore;
	private static final UUID TARGET = UUID.randomUUID();

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -48 -48 48 48");
			CannonMod.later(100, () -> step(server, () -> build(level)));
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
		Cmd.run(level, "fill -30 99 -30 30 99 40 minecraft:stone");
		for (int y = 100; y < 115; y += 5) {
			Cmd.run(level, "fill -30 " + y + " -30 30 " + (y + 4) + " 40 minecraft:air"); // fill is capped at 32768 blocks
		}
		displaysBefore = countParts(level);

		check(CannonItems.isCannon(CannonItems.cannon()), "cannon item is recognised");
		check(CannonItems.cannon().is(Items.DISPENSER), "cannon item is a dispenser to vanilla clients");
		check(CannonItems.isCannonball(CannonItems.cannonballs(4)), "cannonball item is recognised");
		check(!CannonItems.isCannonball(new ItemStack(Items.FIREWORK_STAR)), "a plain firework star is not a cannonball");
		check(!CannonItems.isCannon(new ItemStack(Items.DISPENSER)), "a plain dispenser is not a cannon");

		CannonData data = new CannonData();
		data.owner = "00000000-0000-0000-0000-000000000000";
		data.ownerName = "SmokeTest";
		cannon = Cannons.spawn(level, data, 0.5, 100, 0.5, 0f);
		check(cannon != null, "cannon spawned");
		int parts = Cannon.FRAME.size() + Cannon.BARREL.size() + 1;
		check(cannon.root.getPassengers().size() == parts, "cannon has all " + parts + " model parts + hitbox");
		check(cannon.root.getPassengers().stream().anyMatch(e -> e instanceof Interaction), "cannon has a hitbox");
		for (int i = 0; i < cannon.barrel.length; i++) {
			check(cannon.barrel[i] != null, "barrel part " + i + " found by its tag");
		}
		check(cannon.root.getAttached(CannonMod.DATA) == data, "cannon data attached");

		cannon.testAim = new Cannon.Aim(40f, 35f);
		CannonMod.later(20, () -> step(server, () -> swung(level)));
	}

	private static void swung(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(cannon.seat != null && !cannon.seat.isRemoved(), "gunner's seat exists");
		check(Math.abs(cannon.root.getYRot() - 40f) < 0.01, "cannon turned to 40° (yaw=" + cannon.root.getYRot() + ")");
		check(Math.abs(cannon.data.elevation - 35f) < 0.01, "barrel raised to 35° (elevation=" + cannon.data.elevation + ")");
		check(cannon.root.getPassengers().stream().allMatch(e -> e.getYRot() == cannon.root.getYRot()), "model turned with it");
		double[] seat = Cannon.toWorld(cannon.root.getX(), cannon.root.getZ(), cannon.root.getYRot(), 0, Cannon.SEAT_Z);
		check(Math.abs(cannon.seat.getX() - seat[0]) < 0.01 && Math.abs(cannon.seat.getZ() - seat[1]) < 0.01, "seat swung round behind the breech");
		double range = cannon.estimatedRange();
		check(range > 40 && range < 200, "range at 35° is sensible (" + Math.round(range) + " blocks)");

		cannon.testAim = new Cannon.Aim(0f, 90f);
		CannonMod.later(40, () -> step(server, () -> clamped(level)));
	}

	private static void clamped(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(cannon.data.elevation == Cannon.MAX_ELEVATION, "elevation is capped at " + Cannon.MAX_ELEVATION + "°");
		check(Math.abs(cannon.root.getYRot()) < 0.01, "cannon turned back to 0°");

		// a dirt wall straight ahead with a villager in front of it, then fire at them nearly flat
		Cmd.run(level, "fill -4 100 " + WALL_Z + " 4 106 " + (WALL_Z + 1) + " minecraft:dirt");
		wallBefore = countWall(level);
		check(wallBefore == 9 * 7 * 2, "target wall built");
		Cmd.run(level, "summon minecraft:villager 0.5 100 " + (WALL_Z - 1) + ".5 {" + Cmd.uuidNbt(TARGET) + ",NoAI:1b,PersistenceRequired:1b}");
		check(level.getEntity(TARGET) instanceof LivingEntity, "target villager summoned");
		cannon.testAim = new Cannon.Aim(0f, 5f);
		CannonMod.later(30, () -> step(server, () -> fire(level))); // 55° at 3° a tick
	}

	private static void fire(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(Math.abs(cannon.data.elevation - 5f) < 0.01, "barrel lowered to 5°");
		check(cannon.fire(null), "cannon fired");
		check(Cannonball.flying().size() == 1, "one ball in the air");
		check(!cannon.fire(null), "can't fire again while reloading");
		CannonMod.later(3, () -> step(server, () -> {
			check(Cannonball.flying().size() == 1, "ball still flying after 3 ticks");
			check(Cannonball.flying().get(0).pos.z > 6, "ball moved downrange (z=" + Cannonball.flying().get(0).pos.z + ")");
		}));
		CannonMod.later(Cannon.RELOAD_TICKS + 5, () -> step(server, () -> impact(level)));
	}

	private static void impact(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(Cannonball.flying().isEmpty(), "ball landed");
		check(!(level.getEntity(TARGET) instanceof LivingEntity target) || target.isDeadOrDying() || target.getHealth() < target.getMaxHealth(),
			"the villager got hit");
		int wall = countWall(level);
		check(wall == wallBefore, "no terrain damage: the wall is intact (" + (wallBefore - wall) + " blocks missing)");
		check(cannon.reload == 0, "reloaded after " + Cannon.RELOAD_TICKS + " ticks");
		cannon.testAim = null;

		ItemStack kit = Cannons.pickUp(cannon);
		check(CannonItems.isCannon(kit), "picking up gives the cannon item back");
		CannonMod.later(5, () -> step(server, () -> cleanup(level)));
	}

	private static void cleanup(ServerLevel level) {
		check(countParts(level) == displaysBefore, "picking up removes every entity (" + countParts(level) + " left, expected " + displaysBefore + ")");
		check(Cannons.all().isEmpty(), "no cannons left registered");
		CannonMod.LOG.info("CANNON SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		CannonMod.LOG.error("CANNON SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static int countWall(ServerLevel level) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(-4, 100, WALL_Z, 4, 106, WALL_Z + 1)) {
			if (level.getBlockState(pos).is(Blocks.DIRT)) {
				n++;
			}
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

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		CannonMod.LOG.info("[smoke] ok: {}", what);
	}
}
