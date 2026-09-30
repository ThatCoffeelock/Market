package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Only runs with -Dcolonycraft.smokeTest=true (CI). Boots a real server (with the Market mod), founds
 * a colony, builds one of everything, runs paydays (normal, autosell, strike), kills and replaces a
 * worker, saves and reloads, then demolishes it all again.
 */
final class SmokeTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000c01a");

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -64 -64 64 64");
		ColonycraftMod.later(100, () -> step(server, () -> build(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			ColonycraftMod.LOG.error("COLONYCRAFT SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static Colony colony;

	private static void build(ServerLevel level) {
		for (int x = -60; x < 60; x += 20) {
			Cmd.run(level, "fill " + x + " 96 -60 " + (x + 19) + " 98 60 minecraft:stone");
			Cmd.run(level, "fill " + x + " 99 -60 " + (x + 19) + " 99 60 minecraft:grass_block");
			Cmd.run(level, "fill " + x + " 100 -60 " + (x + 19) + " 111 60 minecraft:air");
		}
		Bank.credit(OWNER, "SmokeTest", Bank.cents(100_000));
		check(Bank.balance(OWNER) >= Bank.cents(100_000), "the Market bank works (balance " + Bank.format(Bank.balance(OWNER)) + ")");

		colony = new Colony("smoke", "Testville", OWNER, "SmokeTest", Colonies.dim(level));
		Colonies.all().add(colony);
		Colony.Building hall = Colonies.construct(level, colony, BuildingType.TOWN_HALL, new BlockPos(0, 99, 0), 0, Bank.cents(2500), true);
		check(level.getBlockState(hall.world(BuildingType.COUNTER)).is(Blocks.LECTERN), "the Town Hall has its counter");
		check(hall.alive() == 1, "the mayor moved in");

		Object[][] plan = {
			{BuildingType.RESIDENCE, 12, 0}, {BuildingType.RESIDENCE, 12, 12}, {BuildingType.RESIDENCE, 0, 12},
			{BuildingType.FARM, -12, 0}, {BuildingType.LUMBER_CAMP, -12, 12}, {BuildingType.MINE, -12, -12},
			{BuildingType.WORKSHOP, 0, -12}, {BuildingType.STOREHOUSE, 12, -12}};
		for (Object[] p : plan) {
			BuildingType type = (BuildingType) p[0];
			Colonies.construct(level, colony, type, new BlockPos((int) p[1], 99, (int) p[2]), (int) p[1] == 0 ? 2 : 1, Bank.cents(type.price), true);
		}
		check(colony.buildings.size() == 9, "built one of everything");
		check(colony.workers() == 11, "11 workers moved in (" + colony.workers() + ")");
		check(colony.housing() == 14, "14 beds (" + colony.housing() + ")");
		Colony.Building farm = find(BuildingType.FARM);
		check(level.getBlockState(farm.world(new BlockPos(3, 1, 3))).is(Blocks.COMPOSTER), "the farm has its composter");
		Colony.Building store = find(BuildingType.STOREHOUSE);
		check(level.getBlockState(store.world(new BlockPos(-3, 2, 0))).is(Blocks.BARREL), "the storehouse walls are barrels");
		for (Colony.Building b : colony.buildings) {
			for (UUID v : b.villagers) {
				check(v != null && level.getEntity(v) != null, b.type.id + " worker exists");
			}
		}

		Colonies.upgrade(level, store, 0);
		check(store.storage.getContainerSize() == 54, "storehouse upgrade gives 54 slots");

		// payday 1: wages out, goods in, workshop at work
		long before = Bank.balance(OWNER);
		Colonies.payday(colony);
		check(!colony.striking, "wages were paid");
		check(before - Bank.balance(OWNER) == Bank.cents(Colonies.WAGE) * 11, "wages were " + Bank.format(before - Bank.balance(OWNER)));
		check(count(store, Items.WHEAT) + count(store, Items.BREAD) > 0, "the farm delivered");
		check(count(store, Items.COBBLESTONE) + count(store, Items.STONE) > 0, "the mine delivered");
		check(count(store, Items.OAK_PLANKS) > 0, "the workshop turned logs into planks (" + count(store, Items.OAK_PLANKS) + ")");

		// payday 2: autosell empties the storehouse into the bank
		store.autosell = true;
		before = Bank.balance(OWNER);
		Colonies.payday(colony);
		check(Bank.balance(OWNER) > before, "autosell earned " + Bank.format(Bank.balance(OWNER) - before + Bank.cents(Colonies.WAGE) * 11) + " before wages");
		check(used(store) < 5, "autosell emptied the storehouse (" + used(store) + " slots left)");
		store.autosell = false;

		// payday 3: broke, so on strike: nothing gathered
		long all = Bank.balance(OWNER);
		check(Bank.charge(OWNER, all), "emptied the bank");
		int items = used(store);
		Colonies.payday(colony);
		check(colony.striking, "no wages, so the workers are on strike");
		check(used(store) == items, "nothing was gathered during the strike");
		Bank.credit(OWNER, "SmokeTest", Bank.cents(50_000));

		// a farmer dies, and gets replaced
		UUID farmer = farm.villagers.get(0);
		Cmd.run(level, "kill " + farmer);
		ColonycraftMod.later(5, () -> step(level.getServer(), () -> afterDeath(level)));
	}

	private static void afterDeath(ServerLevel level) {
		Colony.Building farm = find(BuildingType.FARM);
		check(farm.dead() == 1, "the dead farmer left a vacancy");
		check(Colonies.replaceDead(level, farm) == 1 && farm.dead() == 0, "hired a replacement");

		// save and load
		String json = ColonyStore.toJson(level.getServer(), Colonies.all(), 1234);
		List<Colony> back = ColonyStore.fromJson(level.getServer(), json);
		check(back.size() == 1 && back.get(0).buildings.size() == 9, "colonies survive a save and load");
		check(ColonyStore.clock == 1234, "the payday clock survives a save and load");

		// demolish everything, Town Hall last
		Colony.Building workshop = find(BuildingType.WORKSHOP);
		BlockPos anvil = workshop.world(new BlockPos(0, 1, 2));
		long before = Bank.balance(OWNER);
		long refund = Colonies.demolish(level, workshop);
		check(level.getBlockState(anvil).isAir(), "the workshop is gone");
		check(Bank.balance(OWNER) - before == refund && refund == Bank.cents(1500 * Colonies.REFUND), "half the price came back");
		Colony.Building hall = colony.townHall();
		check(Colonies.whyNoDemolish(hall) != null, "the Town Hall can't go while other buildings stand");
		for (Colony.Building b : new ArrayList<>(colony.buildings)) {
			if (b.type == BuildingType.STOREHOUSE) {
				b.storage.clearContent();
			}
		}
		for (Colony.Building b : new ArrayList<>(colony.buildings)) {
			if (b.type != BuildingType.TOWN_HALL && b.type != BuildingType.RESIDENCE) {
				Colonies.demolish(level, b);
			}
		}
		for (Colony.Building b : new ArrayList<>(colony.buildings)) {
			if (b.type == BuildingType.RESIDENCE) {
				Colonies.demolish(level, b);
			}
		}
		Colonies.demolish(level, hall);
		check(Colonies.all().isEmpty(), "the colony is disbanded");
		ColonycraftMod.later(5, () -> step(level.getServer(), () -> cleanup(level)));
	}

	private static void cleanup(ServerLevel level) {
		int workers = 0;
		for (Entity e : level.getAllEntities()) {
			if (e.hasAttached(ColonycraftMod.WORKER) && e.isAlive()) {
				workers++;
			}
		}
		check(workers == 0, "every worker left (" + workers + " still around)");
		ColonycraftMod.LOG.info("COLONYCRAFT SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static Colony.Building find(BuildingType type) {
		for (Colony.Building b : colony.buildings) {
			if (b.type == type) {
				return b;
			}
		}
		throw new IllegalStateException("no " + type.id);
	}

	private static int count(Colony.Building store, Item item) {
		int n = 0;
		for (int i = 0; i < store.storage.getContainerSize(); i++) {
			ItemStack stack = store.storage.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static int used(Colony.Building store) {
		int n = 0;
		for (int i = 0; i < store.storage.getContainerSize(); i++) {
			n += store.storage.getItem(i).isEmpty() ? 0 : 1;
		}
		return n;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		ColonycraftMod.LOG.info("[smoke] ok: {}", what);
	}
}
