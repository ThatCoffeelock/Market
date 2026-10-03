package com.thatcoffeelock.warehouse;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dwarehouse.smokeTest=true (CI). Boots a real server and builds two warehouses side by side: racks
 * join the right one, a rack between them is disputed, a second core in the same building is refused. Then it fills
 * them by hand, through an intake chest and a rack's barrel, through a Cargo Train Drop-off Station and through a
 * Loading Dock (specialist warehouses first), saves and reloads, packs a warehouse up and unpacks it somewhere else,
 * and checks that a core that vanished drops its stock packed up instead of losing it.
 */
final class SmokeTest {
	private static final BlockPos CORE = new BlockPos(0, 101, 0);
	private static final BlockPos CORE2 = new BlockPos(8, 101, 0);
	private static final BlockPos LONELY = new BlockPos(0, 101, 8);
	private static final BlockPos INTRUDER = new BlockPos(1, 101, 1);
	private static final BlockPos BRIDGE = new BlockPos(4, 101, 0);
	private static final BlockPos INTAKE = new BlockPos(2, 101, -1);
	private static final BlockPos PLAIN = new BlockPos(3, 101, -1);
	private static final BlockPos STATION = new BlockPos(1, 101, -1);
	private static final BlockPos DOCK = new BlockPos(0, 101, -6);
	private static final BlockPos MOVED = new BlockPos(-10, 101, -10);

	private static int displaysBefore;
	private static String id1;
	private static String id2;

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -32 -32 32 32");
		WarehouseMod.later(100, () -> step(server, () -> build(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			WarehouseMod.LOG.error("WAREHOUSE SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void then(ServerLevel level, int ticks, Step step) {
		WarehouseMod.later(ticks, () -> step(level.getServer(), step));
	}

	private static void setblock(ServerLevel level, BlockPos p, String block) {
		Cmd.run(level, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + block);
	}

	/** Places a block the way a player would: the block, then what the BlockItem mixin does. */
	private static void place(ServerLevel level, BlockPos p, String block, ItemStack item) {
		setblock(level, p, block);
		Interactions.onPlaced(level, null, p, item);
	}

	private static Warehouse.Key key(net.minecraft.world.item.Item item) {
		return Warehouse.Key.of(new ItemStack(item));
	}

	private static void build(ServerLevel level) {
		Cmd.run(level, "fill -20 100 -20 20 100 20 minecraft:stone");
		Cmd.run(level, "fill -20 101 -20 20 108 20 minecraft:air");
		displaysBefore = displays(level);
		WarehouseConfig config = WarehouseConfig.get();

		check(Parts.isCore(Parts.core()) && !Parts.isCore(new ItemStack(Items.CARTOGRAPHY_TABLE)), "core item is recognised, a plain cartography table isn't");
		check(Parts.isRack(Parts.rack()) && !Parts.isRack(new ItemStack(Items.BARREL)), "rack item is recognised, a plain barrel isn't");
		check(Parts.isDock(Parts.dock()) && !Parts.isDock(new ItemStack(Items.LANTERN)), "dock item is recognised, a plain lantern isn't");
		check(Category.of(new ItemStack(Items.IRON_INGOT)) == Category.MINERALS && Category.of(new ItemStack(Items.DIAMOND_ORE)) == Category.MINERALS,
			"iron ingots and diamond ore are minerals");
		check(Category.of(new ItemStack(Items.BREAD)) == Category.FOOD, "bread is food");
		check(Category.of(new ItemStack(Items.IRON_PICKAXE)) == Category.GEAR, "a pickaxe is gear");
		check(Category.of(new ItemStack(Items.BRICKS)) == Category.BLOCKS, "bricks are building blocks");
		check(Category.of(new ItemStack(Items.STRING)) == Category.OTHER, "string is everything else");

		// warehouse 1: a core and four racks (three in a row, one stacked)
		place(level, CORE, "minecraft:cartography_table", Parts.core());
		Warehouse w = Warehouses.coreAt(level, CORE);
		check(w != null, "core placed: warehouse 1 exists");
		id1 = w.id;
		check(w.capacity() == config.coreCapacity, "an empty core holds " + w.capacity());
		for (int x = 1; x <= 3; x++) {
			place(level, new BlockPos(x, 101, 0), "minecraft:barrel", Parts.rack());
		}
		place(level, new BlockPos(1, 102, 0), "minecraft:barrel", Parts.rack());
		check(w.racks == 4 && w.capacity() == config.coreCapacity + 4L * config.rackCapacity, "four racks count (" + w.racks + ", room " + w.capacity() + ")");
		check(Warehouses.at(level, new BlockPos(3, 101, 0)) == w, "the far rack belongs to warehouse 1");

		place(level, LONELY, "minecraft:barrel", Parts.rack());
		check(Warehouses.at(level, LONELY) == null && !Warehouses.isDisputed(level, LONELY), "a rack on its own doesn't count for anyone");

		place(level, INTRUDER, "minecraft:cartography_table", Parts.core());
		check(Warehouses.coreAt(level, INTRUDER) == null, "a second core touching the building isn't registered");

		// warehouse 2, three racks towards warehouse 1, then one rack in between that touches both
		place(level, CORE2, "minecraft:cartography_table", Parts.core());
		Warehouse w2 = Warehouses.coreAt(level, CORE2);
		check(w2 != null && w2 != w, "warehouse 2 exists");
		id2 = w2.id;
		for (int x = 5; x <= 7; x++) {
			place(level, new BlockPos(x, 101, 0), "minecraft:barrel", Parts.rack());
		}
		place(level, BRIDGE, "minecraft:barrel", Parts.rack());
		check(Warehouses.isDisputed(level, BRIDGE) && Warehouses.at(level, BRIDGE) == null, "the rack between two warehouses is disputed");
		check(w.racks == 4 && w2.racks == 3 && w.disputed == 1 && w2.disputed == 1, "the disputed rack counts for neither (" + w.racks + ", " + w2.racks + ")");

		then(level, 3, () -> deposits(level));
	}

	private static void deposits(ServerLevel level) {
		check(!level.getBlockState(INTRUDER).is(net.minecraft.world.level.block.Blocks.CARTOGRAPHY_TABLE), "the second core was taken down again");
		Warehouse w = Warehouses.byId(id1);
		Warehouse w2 = Warehouses.byId(id2);

		check(w.deposit(new ItemStack(Items.COBBLESTONE, 64)) == 64, "64 cobblestone go in by hand");
		ItemStack worn = new ItemStack(Items.IRON_SWORD);
		worn.setDamageValue(10);
		check(w.deposit(worn.copy()) == 1 && w.deposit(new ItemStack(Items.IRON_SWORD)) == 1, "two swords go in");
		check(w.kinds() == 3 && w.count(Warehouse.Key.of(worn)) == 1 && w.count(key(Items.IRON_SWORD)) == 1,
			"a worn sword and a new sword are kept apart (" + w.kinds() + " kinds)");
		check(w.take(key(Items.COBBLESTONE), 10) == 10 && w.count(key(Items.COBBLESTONE)) == 54, "taking 10 out leaves 54");

		w2.filter = Category.FOOD;
		check(w2.deposit(new ItemStack(Items.DIAMOND, 5)) == 0, "the food warehouse refuses diamonds");
		check(w2.deposit(new ItemStack(Items.BREAD, 10)) == 10, "the food warehouse takes bread");
		int guard = 0;
		while (w2.deposit(new ItemStack(Items.BREAD, 64)) > 0 && guard++ < 10_000) {
			// fill it right up
		}
		check(w2.total() == w2.capacity() && w2.space() == 0, "a full warehouse stops at its capacity (" + w2.total() + ")");
		check(w2.deposit(new ItemStack(Items.BREAD, 1)) == 0, "a full warehouse takes nothing more");
		w2.take(key(Items.BREAD), w2.total() - 10);
		check(w2.total() == 10, "emptied back down to 10 bread");

		// an intake chest and a rack's own barrel get swept onto the shelves; a plain chest is left alone
		setblock(level, INTAKE, "minecraft:chest{CustomName:{text:\"Warehouse Intake\"}}");
		setblock(level, PLAIN, "minecraft:chest");
		Cmd.run(level, "item replace block " + INTAKE.getX() + " " + INTAKE.getY() + " " + INTAKE.getZ() + " container.0 with minecraft:iron_ingot 32");
		Cmd.run(level, "item replace block 3 101 0 container.0 with minecraft:oak_log 16");
		Cmd.run(level, "item replace block " + PLAIN.getX() + " " + PLAIN.getY() + " " + PLAIN.getZ() + " container.0 with minecraft:gold_ingot 5");
		Warehouses.sweep();
		check(w.count(key(Items.IRON_INGOT)) == 32, "the intake chest was emptied into the warehouse");
		check(w.count(key(Items.OAK_LOG)) == 16, "a rack's barrel (where hoppers feed in) was emptied into the warehouse");
		check(level.getBlockEntity(INTAKE) instanceof Container c && c.isEmpty(), "the intake chest is empty now");
		check(level.getBlockEntity(PLAIN) instanceof Container c && c.getItem(0).getCount() == 5, "a plain chest next to it is left alone");

		if (WarehouseMod.train()) {
			var intake = TrainLink.intake(level, STATION);
			check(intake != null, "a Drop-off Station touching a rack leads into the warehouse");
			ItemStack coal = new ItemStack(Items.COAL, 20);
			check(intake.accept(coal) == 20 && coal.isEmpty() && w.count(key(Items.COAL)) == 20, "the train unloads straight onto the shelves");
			check(TrainLink.intake(level, new BlockPos(-5, 101, -5)) == null, "a station that touches nothing has no warehouse behind it");
		} else {
			WarehouseMod.LOG.info("[smoke] Cargo Train isn't loaded; skipping the train checks");
		}

		// a Loading Dock unloads a ship's holds: bread to the food warehouse first, the rest to the general one
		place(level, DOCK, "minecraft:lantern", Parts.dock());
		check(Warehouses.isDock(level, DOCK), "dock placed");
		List<Warehouse> served = Docks.served(level, DOCK);
		check(served.contains(w) && served.contains(w2), "the dock serves both warehouses");
		check(DOCK.equals(Warehouses.nearestDock(level, 0.5, 101, -12, WarehouseConfig.get().shipReach)), "a ship 6 blocks away finds the dock");
		SimpleContainer hold = new SimpleContainer(54);
		hold.setItem(0, new ItemStack(Items.BREAD, 30));
		hold.setItem(1, new ItemStack(Items.DIAMOND, 7));
		SimpleContainer holdB = new SimpleContainer(54);
		holdB.setItem(5, new ItemStack(Items.BRICKS, 64));
		Docks.Report report = Docks.unload(served, List.of(hold, holdB));
		check(report.moved() == 101 && hold.isEmpty() && holdB.isEmpty(), "the whole cargo was unloaded (" + report.moved() + ")");
		check(w2.count(key(Items.BREAD)) == 40 && w.count(key(Items.BREAD)) == 0, "bread went to the food warehouse");
		check(w.count(key(Items.DIAMOND)) == 7 && w.count(key(Items.BRICKS)) == 64, "diamonds and bricks went to the general warehouse");
		if (WarehouseMod.ahoy()) {
			check(AhoyLink.nearestShip(level, DOCK, null) == null, "no ship is moored at the dock (yet)");
		}

		Warehouses.labels();
		then(level, 5, () -> labelled(level));
	}

	private static void labelled(ServerLevel level) {
		check(displays(level) == displaysBefore + 5, "name labels float over both cores and the dock (" + (displays(level) - displaysBefore) + ")");

		// save, forget everything, load it back
		Warehouse w = Warehouses.byId(id1);
		long total = w.total();
		int kinds = w.kinds();
		ItemStack worn = new ItemStack(Items.IRON_SWORD);
		worn.setDamageValue(10);
		Warehouses.save();
		Warehouses.load(level.getServer());
		w = Warehouses.byId(id1);
		Warehouse w2 = Warehouses.byId(id2);
		check(w != null && w2 != null, "both warehouses come back after a reload");
		check(w.total() == total && w.kinds() == kinds, "the stock survives a reload (" + w.total() + " items, " + w.kinds() + " kinds)");
		check(w.count(Warehouse.Key.of(worn)) == 1, "the worn sword is still worn after a reload");
		check(w2.filter == Category.FOOD, "the food warehouse is still a food warehouse");
		Warehouses.refresh();
		check(w.racks == 4 && w2.racks == 3 && Warehouses.isDisputed(level, BRIDGE), "racks and docks come back after a reload");
		check(Warehouses.isDock(level, DOCK), "the dock comes back after a reload");

		// pack warehouse 1 up: its racks are free, so they join warehouse 2 through the bridge
		ItemStack packed = Warehouses.pack(w);
		setblock(level, CORE, "minecraft:air");
		check(Parts.isCore(packed) && Parts.packedId(packed).equals(w.id) && w.packed, "packing up gives a core that knows its warehouse");
		check(Warehouses.coreAt(level, CORE) == null, "nothing is registered where the core stood");
		Warehouses.refresh();
		check(w2.racks == 8 && !Warehouses.isDisputed(level, BRIDGE), "the freed racks join the warehouse they're connected to (" + w2.racks + ")");

		// unpack it somewhere else
		place(level, MOVED, "minecraft:cartography_table", packed);
		check(!w.packed && w.pos.equals(MOVED) && w.total() == total, "unpacked somewhere else with all " + total + " items");

		// the core vanishes (an explosion, say): it drops packed up, nothing is lost
		setblock(level, MOVED, "minecraft:air");
		Warehouses.validate();
		check(w.packed && w.total() == total, "a vanished core drops packed up with its stock");

		// clean up
		Warehouses.pack(w2);
		setblock(level, CORE2, "minecraft:air");
		Warehouses.removeDock(level, DOCK);
		setblock(level, DOCK, "minecraft:air");
		Cmd.run(level, "kill @e[type=minecraft:item]");
		then(level, 5, () -> cleanup(level));
	}

	private static void cleanup(ServerLevel level) {
		check(displays(level) == displaysBefore, "no labels left behind (" + (displays(level) - displaysBefore) + " extra)");
		WarehouseMod.LOG.info("WAREHOUSE SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static int displays(ServerLevel level) {
		int n = 0;
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Display && entity.isAlive()) {
				n++;
			}
		}
		return n;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		WarehouseMod.LOG.info("[smoke] ok: {}", what);
	}
}
