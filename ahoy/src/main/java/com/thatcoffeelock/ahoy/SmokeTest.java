package com.thatcoffeelock.ahoy;

import java.util.UUID;

import com.thatcoffeelock.cannon.CannonItems;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Only runs with -Dahoy.smokeTest=true (CI). Boots a real server, digs a test harbour, launches a
 * ship, checks what a passenger can do from a seat (fish, draw a bow, milk a cow, use a block, open the menu with an empty
 * hand), sails and turns it, runs it into the wall, then bottles it up and checks nothing was lost.
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
		check(AhoyApi.shipsNear(level, ship.root.position(), 4).contains(ship), "other mods can find the ship (AhoyApi)");
		check(ship.holds().size() == 2 && ship.holds().get(0).getItem(0).is(Items.DIAMOND), "other mods can reach the cargo holds");

		// the ship makes its seats and hitboxes on its first ticks
		AhoyMod.later(20, () -> step(level.getServer(), () -> {
			aboard(level);
			if (ship.gunDeck == null) {
				throw new IllegalStateException("Smoke check failed: the Cannon mod should be loaded, so the ship should have a gun deck");
			}
			ship.data.guns.setItem(0, CannonItems.cannon());
			ship.data.guns.setItem(3, CannonItems.cannon());
			ship.data.guns.setItem(1, new ItemStack(Items.STICK)); // not a cannon: must not be mounted
			AhoyMod.later(10, () -> step(level.getServer(), () -> gunsMounted(level)));
		}));
	}

	/** Cannons slotted into the gun ports get built, sit where the ports are, and can be taken out again. */
	private static void gunsMounted(ServerLevel level) {
		GunDeck deck = ship.gunDeck;
		check(deck.mounted(0) && deck.mounted(3), "cannons slotted into ports 0 and 3 were built");
		check(!deck.mounted(1) && !deck.mounted(2), "a stick and an empty port get no cannon");
		check(deck.gunner(0) == null && deck.gunners().isEmpty(), "nobody is manning them yet");
		check(portDistance(0) < 0.5 && portDistance(3) < 0.5, "they stand on their ports (" + portDistance(0) + ")");
		ship.data.guns.setItem(3, ItemStack.EMPTY);
		AhoyMod.later(5, () -> step(level.getServer(), () -> {
			check(!deck.mounted(3) && deck.mounted(0), "taking a cannon out of its port takes it down, the other stays");
			bunks(level);
		}));
	}

	/** Beds slotted into bunks get drawn; a player can lie down at night (with a hidden bed for the game) and get up again. */
	private static void bunks(ServerLevel level) {
		ship.data.bunks.setItem(0, new ItemStack(Items.BED.blue()));
		ship.data.bunks.setItem(1, new ItemStack(Items.STICK)); // not a bed
		AhoyMod.later(5, () -> step(level.getServer(), () -> {
			BunkDeck deck = ship.bunkDeck;
			check(deck.drawn(0) && !deck.drawn(1), "a bed slotted into a bunk is drawn, a stick isn't");
			FakePlayer player = FakePlayer.get(level);
			player.setPos(ship.root.getX(), ship.root.getY() + 1.0, ship.root.getZ());
			Cmd.run(level, "time set day");
			String why = deck.lieDown(player, 0);
			check(why != null && !player.isSleeping(), "nobody sleeps in the daytime (" + why + ")");
			Cmd.run(level, "time set midnight");
			// the sky only darkens on the next ticks
			// a fake player isn't in the world, so it can't ride or sleep; let it join for this check
			if (!level.players().contains(player)) {
				level.addNewPlayer(player);
			}
			AhoyMod.later(5, () -> step(level.getServer(), () -> {
				String lay = deck.lieDown(player, 0);
				check(lay == null, "lying down at night works (" + lay + ")");
				check(player.isSleeping() && player.getVehicle() != null, "the player is asleep, riding the bunk (sleeping=" + player.isSleeping() + ", vehicle=" + player.getVehicle() + ")");
				check(Ships.shipOf(player) == ship && deck.isSleeping(player), "the sleeper counts as aboard");
				check(deck.lieDown(player, 0) != null, "a second lie-down is refused");
				deck.wake(player, true);
				check(!player.isSleeping() && !deck.isSleeping(player), "waking up works");
				level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
				Cmd.run(level, "time set day");
				startZ = ship.root.getZ();
				ship.testControls = new Ship.Controls(true, false, false, false, false);
				AhoyMod.later(80, () -> step(level.getServer(), () -> sailed(level)));
			}));
		}));
	}

	private static double portDistance(int port) {
		ShipModel.GunPort p = ShipModel.GUN_PORTS.get(port);
		double[] w = Ship.toWorld(ship.root.getX(), ship.root.getZ(), ship.root.getYRot(), p.x(), p.z());
		var at = ship.gunDeck.where(port);
		return at == null ? 999 : Math.hypot(at.x - w[0], Math.hypot(at.y - (ship.root.getY() + ShipModel.DECK_Y), at.z - w[1]));
	}

	/**
	 * A passenger stands inside the ship's click hitbox, so the game thinks every right-click is aimed at the ship. They
	 * must still be able to use what's in their hand and what's in front of them, and only get the menu when there's
	 * nothing else to do with an empty hand.
	 */
	private static void aboard(ServerLevel level) {
		FakePlayer player = FakePlayer.get(level);
		player.setPos(ship.root.getX(), ship.root.getY() + 1.0, ship.root.getZ());
		player.setYRot(0);
		player.setXRot(-40);
		Vec3 eye = player.getEyePosition();
		boolean inside = false;
		for (Entity entity : level.getEntitiesOfClass(Interaction.class, player.getBoundingBox().inflate(0.5))) {
			inside |= entity.getBoundingBox().contains(eye);
		}
		check(inside, "the passenger's eyes are inside the ship's click hitbox (that's why right-click used to open the menu)");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		Ships.Used used = Ships.useThrough(player, ship);
		check(used == Ships.Used.ITEM && player.fishing != null, "a passenger can cast a fishing rod, without the menu (" + used + ")");
		used = Ships.useThrough(player, ship);
		check(used == Ships.Used.ITEM && player.fishing == null, "... and can reel it in again (" + used + ")");

		player.getInventory().add(new ItemStack(Items.ARROW, 8));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		used = Ships.useThrough(player, ship);
		check(used == Ships.Used.ITEM && player.isUsingItem(), "a passenger can draw a bow, without the menu (" + used + ")");
		player.stopUsingItem();

		// something in front of them: a cow at arm's length, a note block in mid-air
		player.setXRot(0);
		UUID cowId = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:cow " + Cmd.pos(eye.x, eye.y - 0.7, eye.z + 2) + " {" + Cmd.uuidNbt(cowId) + ",NoAI:1b,NoGravity:1b,PersistenceRequired:1b}");
		check(level.getEntity(cowId) != null, "a cow was summoned in front of the passenger");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		used = Ships.useThrough(player, ship);
		check(used == Ships.Used.MOB && player.getMainHandItem().is(Items.MILK_BUCKET), "a passenger can milk a cow in front of them (" + used + ", they have " + player.getMainHandItem() + ")");
		level.getEntity(cowId).discard();

		BlockPos note = BlockPos.containing(eye.x, eye.y, eye.z + 2);
		level.setBlockAndUpdate(note, Blocks.NOTE_BLOCK.defaultBlockState());
		int before = level.getBlockState(note).getValue(BlockStateProperties.NOTE);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		used = Ships.useThrough(player, ship);
		int after = level.getBlockState(note).getValue(BlockStateProperties.NOTE);
		check(used == Ships.Used.BLOCK && after != before, "a passenger can use a block in front of them (" + used + ", note " + before + " to " + after + ")");
		level.setBlockAndUpdate(note, Blocks.AIR.defaultBlockState());

		// nothing there: an empty hand opens the ship's menu, a stick doesn't
		player.setXRot(-40);
		used = Ships.useThrough(player, ship);
		check(used == Ships.Used.MENU, "an empty hand and nothing in front opens the ship's menu (" + used + ")");
		player.closeContainer();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		used = Ships.useThrough(player, ship);
		check(used == Ships.Used.NOTHING, "a stick in hand doesn't open the menu (" + used + ")");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private static void sailed(ServerLevel level) {
		double moved = ship.root.getZ() - startZ;
		check(moved > 2, "ship sailed forward (" + String.format("%.2f", moved) + " blocks)");
		check(ship.markerCount() >= ShipModel.HITBOX_Z.length, "hitboxes follow the ship");
		check(portDistance(0) < 1.0, "the cannon sailed along with the ship (" + portDistance(0) + ")");
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
		check(CannonItems.isCannon(back.guns.getItem(0)), "the slotted cannon survives the bottle");
		check(back.bunks.getItem(0).is(Items.BED.blue()), "the slotted bed survives the bottle");
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
