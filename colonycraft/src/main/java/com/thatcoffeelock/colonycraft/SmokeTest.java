package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Only runs with -Dcolonycraft.smokeTest=true (CI). Boots a real server (with the Market mod), checks
 * every design, founds a colony, builds one of everything, runs paydays (normal, autosell, strike),
 * kills and replaces a worker, saves and reloads, builds a fortified line (walls, gatehouse, watchtower)
 * and a barracks, lets the archers shoot at a husk, catches illagers and puts them through the cellblock
 * (locked up, moved, executed, ransomed, executed in public on the scaffold), builds the fishery, harbor,
 * train station (a drop-off and a shipment), tobacco farm, wall stairs and a wall tower, hires a villager out
 * of a Burlap Sack, repairs and upgrades, then demolishes it all again.
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
		// every design fits its footprint, every block state in it is real, and the crew has headroom
		for (BuildingType type : BuildingType.values()) {
			for (int tier = 1; tier <= BuildingType.MAX_TIER; tier++) {
				Map<BlockPos, BlockState> plan = type.plan(tier);
				for (BlockPos pos : plan.keySet()) {
					check(Math.abs(pos.getX()) <= type.half && Math.abs(pos.getZ()) <= type.depth && pos.getY() >= 0 && pos.getY() <= type.height,
						type.id + " tier " + tier + " stays inside its footprint (" + pos + ")", false);
				}
				for (int i = 0; i < Math.max(1, type.workers(tier)); i++) {
					BlockPos at = type.station(i);
					// villagers are under two blocks tall, iron golems need three
					BlockPos[] room = type == BuildingType.BARRACKS ? new BlockPos[] {at.above(), at.above(2)} : new BlockPos[] {at.above()};
					for (BlockPos head : room) {
						BlockState state = plan.get(head);
						check(state == null || state.isAir(), type.id + " tier " + tier + " crew " + i + " has headroom", false);
					}
				}
			}
		}
		// every open cell has its holding block, and room for a vindicator (just under two blocks tall) to stand
		for (int tier = 1; tier <= BuildingType.MAX_TIER; tier++) {
			Map<BlockPos, BlockState> plan = BuildingType.CELLBLOCK.plan(tier);
			for (int cell = 0; cell < BuildingType.cells(tier); cell++) {
				BlockState hold = plan.get(BuildingType.holding(cell));
				check(hold != null && hold.is(Blocks.VAULT), "cellblock tier " + tier + " cell " + cell + " has a holding block", false);
				BlockPos spot = BuildingType.cellSpot(cell);
				for (BlockPos at : new BlockPos[] {spot, spot.above()}) {
					BlockState state = plan.get(at);
					check(state == null || state.isAir(), "cellblock tier " + tier + " cell " + cell + " has room for a prisoner", false);
				}
			}
		}
		check(BuildingType.badStates == 0, "every block state in every design parses (" + BuildingType.badStates + " bad)");
		ColonycraftMod.LOG.info("[smoke] ok: every design fits its footprint and leaves its crew headroom");

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
			{BuildingType.RESIDENCE, 16, 0}, {BuildingType.RESIDENCE, 16, 16}, {BuildingType.RESIDENCE, 0, 16},
			{BuildingType.FARM, -16, 0}, {BuildingType.LUMBER_CAMP, -16, 16}, {BuildingType.MINE, -16, -16},
			{BuildingType.WORKSHOP, 0, -16}, {BuildingType.STOREHOUSE, 16, -16}};
		for (Object[] p : plan) {
			BuildingType type = (BuildingType) p[0];
			Colonies.construct(level, colony, type, new BlockPos((int) p[1], 99, (int) p[2]), (int) p[1] == 0 ? 2 : 1, Bank.cents(type.price), true);
		}
		check(colony.buildings.size() == 9, "built one of everything");
		check(colony.workers() == 11, "11 workers moved in (" + colony.workers() + ")");
		check(colony.housing() == 14, "14 beds (" + colony.housing() + ")");
		Colony.Building farm = find(BuildingType.FARM);
		check(level.getBlockState(farm.world(new BlockPos(2, 1, 4))).is(Blocks.COMPOSTER), "the farm has its composter");
		Colony.Building store = find(BuildingType.STOREHOUSE);
		check(level.getBlockState(store.world(new BlockPos(-4, 2, 0))).is(Blocks.BARREL), "the storehouse walls are barrels");
		for (Colony.Building b : colony.buildings) {
			for (UUID v : b.villagers) {
				check(v != null && level.getEntity(v) != null, b.type.id + " worker exists");
			}
		}

		Colonies.upgrade(level, store, 0);
		Colonies.finishJobs(); // the upgrade rebuilds it with more storage racks
		check(store.storage.getContainerSize() == 54, "storehouse upgrade gives 54 slots");
		check(level.getBlockState(store.world(BuildingType.STORE_CORE)).is(Blocks.CARTOGRAPHY_TABLE), "the storehouse has its warehouse core");
		check(level.getBlockState(store.world(BuildingType.rack(5))).is(Blocks.BARREL), "a tier 2 storehouse has six storage racks");

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
		check(back.get(0).buildings.get(1).half == BuildingType.RESIDENCE.half, "buildings keep their size through a save and load");
		// a save from before buildings had sizes of their own: they come back at the old, smaller size
		List<Colony> old = ColonyStore.fromJson(level.getServer(), json.replaceAll("\\s*\"(half|depth|height)\": \\d+,", ""));
		Colony.Building oldHouse = old.get(0).buildings.get(1);
		check(oldHouse.type == BuildingType.RESIDENCE && oldHouse.half == 4 && oldHouse.height == 12, "a residence from an old save keeps the old size ("
			+ oldHouse.half + ")");
		check(ColonyStore.clock == 1234, "the payday clock survives a save and load");
		defences(level);
	}

	private static Colony.Building tower;
	private static Colony.Building wallA;
	private static UUID husk;
	private static int shotsBefore;

	private static void defences(ServerLevel level) {
		// the colony is still on strike from earlier (guards don't shoot then): pay up first
		Colonies.payday(colony);
		check(!colony.striking, "paying the wages ends the strike");

		// a wall, a second one that snaps onto its end, a gatehouse after that and a tower at the other end
		wallA = Colonies.construct(level, colony, BuildingType.WALL, new BlockPos(-8, 99, -26), 0, Bank.cents(BuildingType.WALL.price), true);
		BlockPos second = Colonies.snap(level, BuildingType.WALL, new BlockPos(2, 99, -25), 0);
		check(second.equals(new BlockPos(1, 99, -26)), "a wall snaps onto the end of the one next to it (" + second + ")");
		Colonies.construct(level, colony, BuildingType.WALL, second, 0, Bank.cents(BuildingType.WALL.price), true);
		BlockPos gate = Colonies.snap(level, BuildingType.GATEHOUSE, new BlockPos(10, 99, -27), 0);
		check(gate.equals(new BlockPos(9, 99, -26)), "a gatehouse snaps onto the end of the wall (" + gate + ")");
		Colonies.construct(level, colony, BuildingType.GATEHOUSE, gate, 0, Bank.cents(BuildingType.GATEHOUSE.price), true);
		BlockPos corner = Colonies.snap(level, BuildingType.WATCHTOWER, new BlockPos(-17, 99, -25), 0);
		check(corner.equals(new BlockPos(-16, 99, -26)), "a watchtower snaps onto the other end (" + corner + ")");
		tower = Colonies.construct(level, colony, BuildingType.WATCHTOWER, corner, 0, Bank.cents(BuildingType.WATCHTOWER.price), true);
		Colony.Building barracks = Colonies.construct(level, colony, BuildingType.BARRACKS, new BlockPos(36, 99, 16), 0,
			Bank.cents(BuildingType.BARRACKS.price), true);
		check(colony.slotsUsed() == 9, "fortifications don't use building slots (" + colony.slotsUsed() + " used)");
		check(level.getBlockState(wallA.world(new BlockPos(-2, 2, -2))).is(Blocks.WALL_TORCH), "the wall has a torch in its arch");
		check(level.getBlockState(wallA.world(new BlockPos(4, 7, 2))).is(Blocks.STONE_BRICKS), "the wall's joints are quoined in stone bricks");
		check(level.getBlockState(gate.offset(0, 1, 0)).is(Blocks.SPRUCE_FENCE_GATE), "the gatehouse has its gates");

		// everything stands exactly as designed: no lantern, ladder, torch or banner fell off
		for (Colony.Building b : colony.buildings) {
			int off = Colonies.damaged(level, b);
			check(off == 0, b.type.id + " was built as designed (" + off + " blocks off)");
		}

		// the crews
		Entity golem = level.getEntity(barracks.villagers.get(0));
		check(golem != null && BuiltInRegistries.ENTITY_TYPE.getKey(golem.getType()).getPath().equals("iron_golem"), "the barracks hired an iron golem");
		Entity archer = level.getEntity(tower.villagers.get(0));
		check(archer instanceof Mob m && m.isNoAi(), "the archer stands still at their post");
		check(archer instanceof LivingEntity l && l.getMainHandItem().is(Items.CROSSBOW), "the archer holds a crossbow");
		long wages = Bank.cents(Colonies.WAGE) * 11 + Bank.cents(BuildingType.BARRACKS.wage) + Bank.cents(BuildingType.WATCHTOWER.wage);
		check(colony.dailyWages() == wages, "guards earn more than workers (" + Bank.format(colony.dailyWages()) + " a day)");

		// a husk (it doesn't burn in daylight) walks up in front of the tower
		husk = UUID.randomUUID();
		BlockPos front = tower.world(new BlockPos(0, 1, -14));
		Cmd.run(level, "summon minecraft:husk " + Cmd.pos(front.getX() + 0.5, front.getY(), front.getZ() + 0.5)
			+ " {" + Cmd.uuidNbt(husk) + ",PersistenceRequired:1b}");
		check(level.getEntity(husk) != null, "a husk showed up");
		shotsBefore = Colonies.shots;

		// a broken wall gets repaired
		level.setBlock(wallA.world(new BlockPos(0, 5, 0)), Blocks.AIR.defaultBlockState(), 3);
		check(Colonies.damaged(level, wallA) == 1, "a hole in the walkway is noticed");
		check(Colonies.whyNoRebuild(level, wallA) == null, "the wall can be repaired");
		Colonies.rebuild(level, wallA, true);
		check(Colonies.damaged(level, wallA) == 0, "the wall is repaired");

		// a chest with something in it stops a rebuild (rebuilding would clear it)
		BlockPos chest = tower.world(new BlockPos(-2, 1, -2));
		Cmd.run(level, "setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ()
			+ " minecraft:chest{Items:[{Slot:0b,id:\"minecraft:stick\",count:1}]}");
		check(Colonies.whyNoRebuild(level, tower) != null, "a full chest stops a rebuild");
		Cmd.run(level, "setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " minecraft:air");

		prison(level);
		newBuildings(level);

		// upgrading a wall rebuilds it in stone bricks
		Colonies.upgrade(level, wallA, 0);
		ColonycraftMod.later(200, () -> step(level.getServer(), () -> afterVolley(level)));
	}

	/** A height on the wall's outside face above the rubble footing. */
	private static final int WALL_FACE = 3;

	/** The new buildings, a train at the station, wall stairs and a wall tower, and a villager out of a Burlap Sack. */
	private static void newBuildings(ServerLevel level) {
		Colony.Building store = find(BuildingType.STOREHOUSE);
		empty(store);
		if (WarehouseLink.present()) {
			// with the Warehouse mod, the storehouse is a real warehouse: a core and six racks at tier 2
			String warehouse = Colonies.warehouseOf(store);
			check(warehouse != null, "the storehouse is a warehouse");
			Map<String, Object> info = WarehouseLink.info(warehouse);
			check(Integer.valueOf(6).equals(info.get("racks")), "a tier 2 storehouse's warehouse counts six racks (" + info.get("racks") + ")");
			check(info.get("capacity") instanceof Number n && n.longValue() > 20_000, "and has room for over 20,000 items (" + info.get("capacity") + ")");
			Colonies.stow(List.of(store), new ItemStack(Items.BRICKS, 40));
			check(count(store, Items.BRICKS) == 40 && store.storage.isEmpty(), "the colony's goods go onto the shelves, not into the old slots");
			store.storage.setItem(0, new ItemStack(Items.FLINT, 9));
			Colonies.migrate(store);
			check(store.storage.isEmpty() && count(store, Items.FLINT) == 9, "what's left in the old slots moves onto the shelves");
			empty(store);
		} else {
			ColonycraftMod.LOG.info("[smoke] Warehouse isn't loaded; skipping the warehouse checks");
		}

		Colony.Building fishery = Colonies.construct(level, colony, BuildingType.FISHERY, new BlockPos(-26, 99, 36), 0,
			Bank.cents(BuildingType.FISHERY.price), true);
		check(fishery.alive() == 2, "two fishers moved in");
		check(level.getBlockState(fishery.world(new BlockPos(1, 0, 2))).is(Blocks.WATER), "the fishery has its basin");
		check(!Production.gather(BuildingType.FISHERY, 1, 2, new Random(7)).isEmpty(), "fishers bring in fish");

		Colony.Building harbor = Colonies.construct(level, colony, BuildingType.HARBOR_OFFICE, new BlockPos(-8, 99, 36), 0,
			Bank.cents(BuildingType.HARBOR_OFFICE.price), true);
		check(level.getBlockState(harbor.world(BuildingType.HARBOR_DOCK)).is(Blocks.LANTERN), "the harbor's pier ends in a Loading Dock lantern");
		check(harbor.alive() == 1 && Math.abs(Colonies.harborBonus(colony) - 0.05) < 1e-9, "a staffed harbor adds 5% to auto-sales");

		Colony.Building station = Colonies.construct(level, colony, BuildingType.TRAIN_STATION, new BlockPos(12, 99, 36), 0,
			Bank.cents(BuildingType.TRAIN_STATION.price), true);
		BlockPos pickup = station.world(BuildingType.PICKUPS[0]);
		BlockPos drop = station.world(BuildingType.DROP_OFFS[0]);
		check(level.getBlockEntity(pickup) instanceof Nameable n && n.getCustomName() != null && n.getCustomName().getString().equals("Pickup Station"),
			"the station's barrel is a Pickup Station");
		check(level.getBlockEntity(drop) instanceof Nameable n && n.getCustomName() != null && n.getCustomName().getString().equals("Drop-off Station"),
			"the station's chest is a Drop-off Station");
		check(level.getBlockState(station.world(new BlockPos(6, 1, BuildingType.TRACKS[0]))).is(Blocks.RAIL), "the track runs out of the station");
		check(!level.getBlockState(station.world(new BlockPos(0, 1, BuildingType.TRACKS[1]))).is(Blocks.RAIL), "a tier 1 station has one track");
		Container dropBox = (Container) level.getBlockEntity(drop);
		Container pickBox = (Container) level.getBlockEntity(pickup);
		empty(store);
		dropBox.setItem(0, new ItemStack(Items.IRON_INGOT, 20));
		Colonies.stations();
		check(dropBox.isEmpty() && count(store, Items.IRON_INGOT) == 20, "what a train drops off goes into the storehouse");
		Colonies.stations();
		check(pickBox.isEmpty(), "the Pickup Station stays empty while shipping is off");
		station.export = true;
		Colonies.stations();
		check(countIn(pickBox, Items.IRON_INGOT) == 20 && count(store, Items.IRON_INGOT) == 0, "shipping out fills the Pickup Station from the storehouse");
		station.export = false;
		// a pickup with something in it stays put through upgrades: tier 2 and 3 add a track each
		pickBox.setItem(0, new ItemStack(Items.COAL, 5));
		check(Colonies.whyNoRebuild(level, station) == null, "the station's own barrels don't stop an upgrade");
		Colonies.upgrade(level, station, 0);
		Colonies.finishJobs();
		Colonies.upgrade(level, station, 0);
		Colonies.finishJobs();
		check(station.tier == 3 && level.getBlockState(station.world(new BlockPos(0, 1, BuildingType.TRACKS[1]))).is(Blocks.RAIL)
			&& level.getBlockState(station.world(new BlockPos(0, 1, BuildingType.TRACKS[2]))).is(Blocks.RAIL), "a tier 3 station has three tracks");
		check(level.getBlockEntity(station.world(BuildingType.PICKUPS[2])) instanceof Nameable n && n.getCustomName() != null
			&& n.getCustomName().getString().equals("Pickup Station"), "and the third track has its own Pickup Station");
		check(level.getBlockEntity(pickup) instanceof Container kept && countIn(kept, Items.COAL) == 5, "the first Pickup Station kept its coal");
		((Container) level.getBlockEntity(pickup)).clearContent();

		Colony.Building tobacco = Colonies.construct(level, colony, BuildingType.TOBACCO_FARM, new BlockPos(32, 99, 36), 0,
			Bank.cents(BuildingType.TOBACCO_FARM.price), true);
		check(level.getBlockState(tobacco.world(new BlockPos(1, 1, -2))).is(Blocks.LARGE_FERN), "the tobacco farm has its rows");
		if (!HavanaLink.present()) {
			check(!BuildingType.TOBACCO_FARM.available() && Production.tobacco(1, 2, new Random(7)).isEmpty(),
				"without Havana, tobacco farms aren't for sale and grow nothing");
		} else {
			List<ItemStack> crop = Production.tobacco(3, 4, new Random(7));
			check(BuildingType.TOBACCO_FARM.available() && crop.size() >= 3, "with Havana, a tier 3 tobacco farm brings in leaves, cured and aged tobacco ("
				+ crop.size() + " stacks)");
			check(crop.get(0).is(Items.PAPER) && crop.get(0).getCount() > 0, "and it's Havana's tobacco");
		}

		Colony.Building wallTower = Colonies.construct(level, colony, BuildingType.WALL_TOWER, new BlockPos(-8, 99, 52), 0,
			Bank.cents(BuildingType.WALL_TOWER.price), true);
		BlockPos stairsAt = Colonies.snap(level, BuildingType.WALL_STAIRS, new BlockPos(1, 99, 53), 0);
		check(stairsAt.equals(new BlockPos(0, 99, 52)), "wall stairs snap onto a wall tower (" + stairsAt + ")");
		Colony.Building stairs = Colonies.construct(level, colony, BuildingType.WALL_STAIRS, stairsAt, 0, Bank.cents(BuildingType.WALL_STAIRS.price), true);
		check(level.getBlockState(stairs.world(new BlockPos(-3, 1, -2))).is(Blocks.COBBLESTONE_STAIRS), "the wall stairs have their steps");
		check(level.getBlockState(wallTower.world(new BlockPos(2, 5, 2))).is(Blocks.LADDER), "the wall tower has its ladder");
		check(level.getBlockState(wallTower.world(new BlockPos(3, 6, 0))).isAir(), "the wall tower is open where walls join");

		for (Colony.Building b : new Colony.Building[] {fishery, harbor, station, tobacco, wallTower, stairs}) {
			int off = Colonies.damaged(level, b);
			check(off == 0, b.type.id + " was built as designed (" + off + " blocks off)");
		}

		// a villager brought in a Burlap Sack takes an empty job for free
		CompoundTag tag = new CompoundTag();
		tag.putString("burlapsack", "full");
		tag.putString("type", "minecraft:villager");
		tag.putString("name", "Bob");
		tag.put("captive", new CompoundTag());
		ItemStack sack = new ItemStack(Items.BUNDLE);
		sack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		check(SackLink.isVillagerSack(sack) && SackLink.name(sack).equals("Bob"), "a sack with a villager in it is recognised");
		check(!SackLink.isVillagerSack(new ItemStack(Items.BUNDLE)), "a plain bundle isn't a sack");
		check(!Colonies.hireFromSack(level, fishery, "Carl"), "no empty job, no hire");
		Entity gone = level.getEntity(fishery.villagers.get(0));
		if (gone != null) {
			gone.discard();
		}
		fishery.villagers.set(0, null);
		check(Colonies.hireFromSack(level, fishery, "Bob") && fishery.dead() == 0, "Bob from the sack took the empty job");
		Entity bob = level.getEntity(fishery.villagers.get(0));
		check(bob != null && bob.getCustomName() != null && bob.getCustomName().getString().equals("Bob the Fisher"), "and is called Bob the Fisher");
		check(!Colonies.hiresVillagers(BuildingType.BARRACKS), "iron golems don't come in sacks");
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("burlapsack")) {
			ItemStack back = SackLink.emptySack();
			check(back != null && !SackLink.isVillagerSack(back) && back.is(Items.BUNDLE), "Burlap Sack hands back an empty sack");
		}
	}

	private static int countIn(Container box, Item item) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(item)) {
				n += box.getItem(i).getCount();
			}
		}
		return n;
	}

	private static Colony.Building cellblock;
	private static Colony.Building scaffold;
	private static UUID convict;
	private static UUID condemned;

	/** A cellblock: a vindicator gets beaten, shackled, locked up, saved, moved to another cell and executed. */
	private static void prison(ServerLevel level) {
		long wagesBefore = colony.dailyWages();
		cellblock = Colonies.construct(level, colony, BuildingType.CELLBLOCK, new BlockPos(36, 99, -16), 0,
			Bank.cents(BuildingType.CELLBLOCK.price), true);
		check(level.getBlockState(cellblock.world(BuildingType.holding(0))).is(Blocks.VAULT), "cell 1 has its holding block");
		check(level.getBlockState(cellblock.world(BuildingType.holding(1))).is(Blocks.VAULT), "cell 2 has its holding block");
		check(level.getBlockState(cellblock.world(BuildingType.holding(2))).is(Blocks.BRICKS), "cell 3 is bricked up at tier 1");
		int off = Colonies.damaged(level, cellblock);
		check(off == 0, "the cellblock was built as designed (" + off + " blocks off)");
		check(cellblock.alive() == 1, "the jailer moved in");
		check(BuiltInRegistries.ITEM.containsKey(Prison.chainModel()), "the shackles are drawn as a chain (" + Prison.chainModel() + ")");

		// a healthy vindicator won't go quietly; a beaten one will
		UUID id = UUID.randomUUID();
		BlockPos yard = new BlockPos(36, 100, -26);
		Cmd.run(level, "summon minecraft:vindicator " + Cmd.pos(yard.getX() + 0.5, yard.getY(), yard.getZ() + 0.5)
			+ " {" + Cmd.uuidNbt(id) + ",PersistenceRequired:1b}");
		check(level.getEntity(id) instanceof Mob, "a vindicator showed up");
		Mob vindicator = (Mob) level.getEntity(id);
		check(!Prison.weakEnough(vindicator), "a healthy vindicator can't be shackled");
		vindicator.setHealth(vindicator.getMaxHealth() * 0.3f);
		check(Prison.weakEnough(vindicator), "a beaten vindicator can be shackled");
		vindicator.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		ItemStack shackles = Prison.capture(vindicator);
		check(vindicator.isRemoved(), "the vindicator went into the shackles");
		check(Prison.isFull(shackles) && Prison.type(shackles).equals("minecraft:vindicator"),
			"the shackles hold a vindicator (" + Prison.name(shackles) + ")");

		// locked up in cell 1: there, unarmed, harmless and out of the guards' reach
		check(Prison.lockUp(level, cellblock, 2, shackles) != null, "a bricked-up cell takes nobody");
		check(Prison.lockUp(level, cellblock, 0, Prison.emptyShackles()) != null, "empty shackles lock nobody up");
		String why = Prison.lockUp(level, cellblock, 0, shackles);
		check(why == null, "the vindicator is locked up in cell 1 (" + why + ")");
		Prison.Prisoner p = cellblock.prisoners.get(0);
		Entity inmate = level.getEntity(p.id());
		check(inmate instanceof Mob m && m.isNoAi(), "the prisoner has no AI: no moving, no fighting");
		check(inmate.isInvulnerable(), "the guards can't kill the prisoner");
		check(inmate instanceof LivingEntity l && l.getMainHandItem().isEmpty(), "the prisoner's axe was confiscated");
		check(Prison.isPrisoner(inmate), "the prisoner is marked as one");
		BlockPos spot = cellblock.world(BuildingType.cellSpot(0));
		check(inmate.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) < 0.5, "the prisoner stands in their cell");
		check(Prison.lockUp(level, cellblock, 0, shackles) != null, "one prisoner per cell");
		long wages = colony.dailyWages() - wagesBefore;
		check(wages == Bank.cents(BuildingType.CELLBLOCK.wage) + Bank.cents(Prison.UPKEEP),
			"the jailer is paid and the prisoner fed (" + Bank.format(wages) + " a day)");
		check(Colonies.whyNoDemolish(cellblock) != null, "a cellblock with prisoners in it can't be demolished");

		// prisoners survive a save and load
		List<Colony> back = ColonyStore.fromJson(level.getServer(), ColonyStore.toJson(level.getServer(), Colonies.all(), 0));
		boolean kept = false;
		for (Colony.Building b : back.get(0).buildings) {
			kept |= b.type == BuildingType.CELLBLOCK && b.prisoners.containsKey(0) && b.prisoners.get(0).id().equals(p.id());
		}
		check(kept, "prisoners survive a save and load");

		// out of cell 1, back into the shackles, and into cell 2 across the corridor
		ItemStack out = Prison.takeOut(level, cellblock, 0);
		check(out != null && Prison.isFull(out) && cellblock.prisoners.isEmpty(), "the prisoner goes back into the shackles");
		check(level.getEntity(p.id()) == null, "and is gone from cell 1");
		check(Prison.lockUp(level, cellblock, 1, out) == null, "and is locked up in cell 2");
		convict = cellblock.prisoners.get(1).id();

		// executed: the bounty is paid, and the cell is free again
		long before = Bank.balance(OWNER);
		long bounty = Prison.execute(level, cellblock, 1);
		check(bounty == Bank.cents(Prison.BOUNTY.get("minecraft:vindicator")) && Bank.balance(OWNER) - before == bounty,
			"executing a vindicator pays a bounty of " + Bank.format(bounty));
		check(cellblock.prisoners.isEmpty(), "the cell is free again");

		// ransom: the offer grows by a quarter of the bounty a day, up to 2.5 times
		check(Prison.ransomValue("minecraft:pillager", 0) == Bank.cents(25), "a fresh pillager's ransom is the bounty");
		check(Prison.ransomValue("minecraft:pillager", 2) == Bank.cents(37.5), "two days later it's half as much again");
		check(Prison.ransomValue("minecraft:pillager", 100) == Bank.cents(62.5), "and it tops out at 2.5 times the bounty");
		check(Prison.lockUp(level, cellblock, 0, catchOne(level, "minecraft:pillager")) == null, "a pillager is locked up");
		UUID hostage = cellblock.prisoners.get(0).id();
		before = Bank.balance(OWNER);
		long ransom = Prison.ransom(level, cellblock, 0);
		check(ransom == Bank.cents(25) && Bank.balance(OWNER) - before == ransom, "the illagers paid a ransom of " + Bank.format(ransom));
		check(level.getEntity(hostage) == null && cellblock.prisoners.isEmpty(), "the envoy took the pillager home");

		// a public execution needs a scaffold, and pays double
		check(Prison.whyNotPublic(level, colony) != null, "no scaffold, no public execution");
		scaffold = Colonies.construct(level, colony, BuildingType.SCAFFOLD, new BlockPos(36, 99, 0), 0, Bank.cents(BuildingType.SCAFFOLD.price), true);
		off = Colonies.damaged(level, scaffold);
		check(off == 0, "the scaffold was built as designed (" + off + " blocks off)");
		check(level.getBlockState(scaffold.world(new BlockPos(0, 5, 2))).is(Blocks.BELL), "the scaffold has its bell");
		check(Prison.lockUp(level, cellblock, 1, catchOne(level, "minecraft:evoker")) == null, "an evoker is locked up");
		condemned = cellblock.prisoners.get(1).id();
		check(Prison.whyNotPublic(level, colony) == null, "the scaffold is ready");
		before = Bank.balance(OWNER);
		long paid = Prison.publicExecution(level, cellblock, 1);
		check(paid == Bank.cents(200) && Bank.balance(OWNER) - before == paid, "a public execution of an evoker pays twice the bounty ("
			+ Bank.format(paid) + ")");
		check(cellblock.prisoners.isEmpty(), "the evoker left their cell");
		check(Prison.busy(scaffold) && Prison.whyNotPublic(level, colony) != null, "one show at a time");
		Entity evoker = level.getEntity(condemned);
		BlockPos stage = scaffold.world(BuildingType.SCAFFOLD_SPOT);
		check(evoker != null && evoker.distanceToSqr(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5) < 0.5, "the evoker stands on the scaffold");
		check(Prison.isPrisoner(evoker) && evoker instanceof Mob m2 && m2.isNoAi(), "and can't cast anything up there");

		// upgrading unbricks two more cells (checked once the builders are done)
		Colonies.upgrade(level, cellblock, 0);
	}

	/** Summons an illager, beats them down and shackles them. */
	private static ItemStack catchOne(ServerLevel level, String type) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon " + type + " 24.5 100 -20.5 {" + Cmd.uuidNbt(id) + ",PersistenceRequired:1b}");
		check(level.getEntity(id) instanceof LivingEntity, "a " + type + " showed up");
		LivingEntity illager = (LivingEntity) level.getEntity(id);
		illager.setHealth(illager.getMaxHealth() * 0.3f);
		check(Prison.weakEnough(illager), "the " + type + " is beaten down");
		return Prison.capture(illager);
	}

	private static void afterVolley(ServerLevel level) {
		Entity dead = level.getEntity(convict);
		check(dead == null || !dead.isAlive(), "the executed prisoner is dead");
		Entity evoker = level.getEntity(condemned);
		check(evoker == null || !evoker.isAlive(), "the crowd got its public execution");
		check(!Prison.busy(scaffold), "and the scaffold is free again");
		BlockPos spot = cellblock.world(BuildingType.cellSpot(1));
		int drops = level.getEntities((Entity) null, new AABB(spot).inflate(4), e -> Prison.typeId(e).equals("minecraft:item")).size();
		check(drops == 0, "nothing dropped at the execution (" + drops + " items)");
		check(level.getBlockState(cellblock.world(BuildingType.holding(3))).is(Blocks.VAULT), "the upgraded cellblock has a fourth cell");
		int off = Colonies.damaged(level, cellblock);
		check(off == 0, "the upgraded cellblock is complete (" + off + " blocks off)");

		check(Colonies.shots > shotsBefore, "the archer shot at the husk (" + (Colonies.shots - shotsBefore) + " arrows)");
		Entity target = level.getEntity(husk);
		check(target == null || !target.isAlive() || (target instanceof LivingEntity l && l.getHealth() < l.getMaxHealth()),
			"the husk got hit");
		check(level.getBlockState(wallA.world(new BlockPos(4, 3, 2))).is(Blocks.POLISHED_ANDESITE), "the upgraded wall is rebuilt with dressed quoins");
		check(level.getBlockState(wallA.world(new BlockPos(0, WALL_FACE, 2))).is(Blocks.STONE_BRICKS)
			|| level.getBlockState(wallA.world(new BlockPos(0, WALL_FACE, 2))).is(Blocks.MOSSY_STONE_BRICKS)
			|| level.getBlockState(wallA.world(new BlockPos(0, WALL_FACE, 2))).is(Blocks.CRACKED_STONE_BRICKS), "the upgraded wall is stone bricks");
		check(Colonies.damaged(level, wallA) == 0, "the upgraded wall is complete");
		Cmd.run(level, "kill @e[type=minecraft:husk]");
		Cmd.run(level, "kill @e[type=minecraft:arrow]");

		street(level);
		proost(level);
		compagnie(level);

		// demolish everything, Town Hall last
		Colony.Building workshop = find(BuildingType.WORKSHOP);
		BlockPos anvil = workshop.world(new BlockPos(0, 1, 2));
		check(level.getBlockState(anvil).is(Blocks.ANVIL), "the workshop has its anvil");
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
		// the colony network, far from here: load its land first, give it time to wake up
		Cmd.run(level, "forceload add 1560 -40 1640 40");
		Cmd.run(level, "forceload add 1560 1560 1640 1640");
		ColonycraftMod.later(100, () -> step(level.getServer(), () -> network(level)));
	}

	private static Colony capital;
	private static Colony outpost;
	private static Colony.Building outpostFarm;

	/** Headquarters: two colonies 1,600 blocks apart, linked, with roles, a convoy, a standing order and a mayor who governs. */
	private static void network(ServerLevel level) {
		capital = new Colony("smoke-capital", "Hoofdstad", OWNER, "SmokeTest", Colonies.dim(level));
		outpost = new Colony("smoke-outpost", "Buitenpost", OWNER, "SmokeTest", Colonies.dim(level));
		Colonies.all().add(capital);
		Colonies.all().add(outpost);
		Colonies.construct(level, capital, BuildingType.TOWN_HALL, new BlockPos(1600, 99, 0), 0, Bank.cents(2500), true);
		Colonies.construct(level, outpost, BuildingType.TOWN_HALL, new BlockPos(1600, 99, 1600), 0, Bank.cents(2500), true);
		Colony.Building capitalStore = Colonies.construct(level, capital, BuildingType.STOREHOUSE, new BlockPos(1584, 99, 0), 1, Bank.cents(500), true);
		Colonies.construct(level, outpost, BuildingType.STOREHOUSE, new BlockPos(1584, 99, 1600), 1, Bank.cents(500), true);
		Colonies.construct(level, outpost, BuildingType.RESIDENCE, new BlockPos(1616, 99, 1600), 3, Bank.cents(400), true);
		outpostFarm = Colonies.construct(level, outpost, BuildingType.FARM, new BlockPos(1600, 99, 1616), 0, Bank.cents(800), true);
		Colonies.finishJobs();

		// Headquarters: one per player, with its Governor's Table
		check(Network.hq(OWNER) == null && Network.capacity(OWNER) == 0, "no Headquarters yet, no network");
		check(Network.link(outpost) != null, "and nothing to link to");
		Colony.Building hq = Colonies.construct(level, capital, BuildingType.HEADQUARTERS, new BlockPos(1600, 99, 17), 0,
			Bank.cents(BuildingType.HEADQUARTERS.price), true);
		Colonies.finishJobs();
		check(Network.hq(OWNER) == hq, "Headquarters in " + capital.name);
		check(level.getBlockState(hq.world(BuildingType.HQ_TABLE)).is(Blocks.LECTERN), "with the Governor's Table in its hall");
		check(Network.linked(capital) && !Network.linked(outpost) && Network.capacity(OWNER) == 3,
			"its own colony is in the network, the outpost isn't yet, and there's room for 3 more");

		// linking and roles
		check(Network.setRole(outpost, Network.Role.BREADBASKET) != null, "an unlinked colony takes no role");
		check(Network.link(outpost) == null && Network.linked(outpost), "the outpost joins the network");
		check(Network.role(outpost) == Network.Role.NONE && Network.gather(outpost, BuildingType.FARM) == 1.0, "roles are optional: none to start with");
		check(Network.setRole(outpost, Network.Role.BREADBASKET) == null && Network.gather(outpost, BuildingType.FARM) == 1.25
			&& Network.gather(outpost, BuildingType.MINE) == 0.85, "a Breadbasket farms better, digs worse");
		long before = Bank.balance(OWNER);
		check(Network.setRole(outpost, Network.Role.MINING_TOWN) == null && before - Bank.balance(OWNER) == Network.ROLE_CHANGE,
			"changing a role costs " + Bank.format(Network.ROLE_CHANGE));
		check(Network.setRole(outpost, Network.Role.NONE) == null, "and it can go back to no role at all");

		// a convoy: wheat from the capital to the outpost, two days on the road
		capitalStore.storage.clearContent();
		capitalStore.storage.addItem(new ItemStack(Items.WHEAT, 64));
		capitalStore.storage.addItem(new ItemStack(Items.WHEAT, 64));
		ItemStack wheat = new ItemStack(Items.WHEAT);
		check(Network.count(capital, wheat) >= 128, "the capital has wheat to spare");
		long start = Network.count(capital, wheat);
		check(Network.days(capital, outpost) == 2, "1,600 blocks is two days on the road");
		before = Bank.balance(OWNER);
		long fee = Network.fee(capital, outpost, 100);
		check(Network.send(capital, outpost, wheat, 100, "") == null, "a convoy of 100 wheat leaves the capital");
		check(Network.count(capital, wheat) == start - 100 && before - Bank.balance(OWNER) == fee, "the wheat is loaded and the fee paid (" + Bank.format(fee) + ")");
		long there = Network.count(outpost, wheat);
		Network.newDay();
		Network.arrivals(outpost);
		check(Network.count(outpost, wheat) == there, "a day later it's still on the road");
		Network.newDay();
		Network.arrivals(outpost);
		check(Network.count(outpost, wheat) == there + 100 && Network.of(OWNER).convoys.isEmpty(), "two days later it's in the outpost's storehouse");

		// a standing order: every day, up to 10 wheat
		check(Network.addOrder(capital, outpost, wheat, 10) == null && Network.of(OWNER).orders.size() == 1, "a standing order is placed");
		long left = Network.count(capital, wheat);
		Network.dispatch(capital);
		check(Network.count(capital, wheat) == left - 10 && Network.of(OWNER).convoys.size() == 1, "and the morning's 10 wheat leave");

		// the mayor governs: a farmer dies, the mayor hires another
		check(outpost.townHall().alive() == 1, "the outpost has its mayor");
		java.util.UUID farmer = outpostFarm.villagers.get(0);
		check(farmer != null, "the farm has its farmers");
		net.minecraft.world.entity.Entity body = level.getEntity(farmer);
		if (body != null) {
			body.discard();
		}
		outpostFarm.villagers.set(0, null);
		Network.govern(outpost);
		ColonycraftMod.later(10, () -> step(level.getServer(), () -> networkAfter(level)));
	}

	private static void networkAfter(ServerLevel level) {
		check(outpostFarm.villagers.get(0) != null && outpostFarm.dead() == 0, "the mayor hired a new farmer without being asked");
		check(Network.of(OWNER).report.stream().anyMatch(l -> l.contains("hired")), "and wrote it in the report");

		// saving and loading
		Network.save();
		Network.load(level.getServer());
		Network.Net net = Network.of(OWNER);
		check(net.orders.size() == 1 && net.orders.get(0).kind.is(Items.WHEAT) && net.convoys.size() == 1 && net.convoys.get(0).items == 10,
			"the network survives a save and load, orders, convoys and all");
		check(net.linked.contains(outpost.id), "with its links");

		// a colony that goes takes its orders with it
		for (Colony c : List.of(outpost, capital)) {
			for (Colony.Building b : new ArrayList<>(c.buildings)) {
				if (b.type == BuildingType.STOREHOUSE) {
					empty(b);
				}
			}
			for (Colony.Building b : new ArrayList<>(c.buildings)) {
				if (b.type != BuildingType.TOWN_HALL && b.type != BuildingType.RESIDENCE) {
					Colonies.demolish(level, b);
				}
			}
			for (Colony.Building b : new ArrayList<>(c.buildings)) {
				if (b.type == BuildingType.RESIDENCE) {
					Colonies.demolish(level, b);
				}
			}
			Colonies.demolish(level, c.townHall());
		}
		check(Network.hq(OWNER) == null && Network.of(OWNER).orders.isEmpty() && Network.of(OWNER).linked.isEmpty(),
			"with the colonies gone, so is the network");
		ColonycraftMod.LOG.info("[smoke] ok: the colony network");
		Cmd.run(level, "forceload remove 1560 -40 1640 40");
		Cmd.run(level, "forceload remove 1560 1560 1640 1640");
		Network.forgetAll();
		ColonycraftMod.later(5, () -> step(level.getServer(), () -> cleanup(level)));
	}

	/**
	 * The town street: a trading post (master traders with their wares, more stalls per tier), a bank (its vault,
	 * ledger and public door with Riches; paying in, taking out), a museum (Riches cases, more per tier), a chapel
	 * (cheaper replacements), a library (a level-30 table), a ranch (its animals, its goods), an apiary and a fuel
	 * depot (Fossil Fool tanks, refinery and manifold). Then a payday with their earnings, and demolishing them.
	 */
	private static void street(ServerLevel level) {
		Object[][] plan = {
			{BuildingType.BANK, -50, -46}, {BuildingType.MUSEUM, -32, -46}, {BuildingType.CHAPEL, -14, -46}, {BuildingType.TRADING_POST, 4, -46},
			{BuildingType.LIBRARY, 22, -46}, {BuildingType.RANCH, 40, -46}, {BuildingType.APIARY, -48, -26}, {BuildingType.FUEL_DEPOT, -48, -8},
			{BuildingType.GUILDHOUSE, -48, 10}};
		List<Colony.Building> built = new ArrayList<>();
		for (Object[] p : plan) {
			BuildingType type = (BuildingType) p[0];
			check(type.available(), type.id + " can be bought here (Riches, Fossil Fool and Sellswords are on the smoke test's server)");
			built.add(Colonies.construct(level, colony, type, new BlockPos((int) p[1], 99, (int) p[2]), 0, Bank.cents(type.price), true));
		}
		Colony.Building bank = find(BuildingType.BANK);
		Colony.Building museum = find(BuildingType.MUSEUM);
		Colony.Building chapel = find(BuildingType.CHAPEL);
		Colony.Building shop = find(BuildingType.TRADING_POST);
		Colony.Building library = find(BuildingType.LIBRARY);
		Colony.Building ranch = find(BuildingType.RANCH);
		Colony.Building depot = find(BuildingType.FUEL_DEPOT);
		Colony.Building guild = find(BuildingType.GUILDHOUSE);

		// the trading post: a master toolsmith who sells for emeralds and stays put; two more masters at tier 3
		check(shop.alive() == 1 && level.getEntity(shop.villagers.get(0)) != null, "the trading post has its master toolsmith");
		Entity toolsmith = level.getEntity(shop.villagers.get(0));
		check(Street.stocked(toolsmith, 0), "the toolsmith sells our wares for emeralds");
		check(toolsmith instanceof Mob m && m.isNoAi(), "masters stay behind their counters");
		Colonies.upgrade(level, shop, 0);
		Colonies.upgrade(level, shop, 0);
		check(shop.alive() == 3, "a tier 3 trading post has three masters (" + shop.alive() + ")");
		check(Street.stocked(level.getEntity(shop.villagers.get(1)), 1), "the armorer sells diamond armour");
		check(Street.stocked(level.getEntity(shop.villagers.get(2)), 2), "the librarian sells enchanted books (they loaded on this game version)");
		check(level.getEntity(shop.villagers.get(2)).getCustomName().getString().endsWith("the Master Librarian"), "masters are named for their trade");

		// the bank: the ledger, the vault door, the teller; Riches knows the vault and the door; a shared vault
		check(level.getBlockState(bank.world(BuildingType.BANK_TELLER)).is(Blocks.LECTERN), "the bank has its teller's lectern");
		check(level.getBlockState(bank.world(BuildingType.BANK_LEDGER)).is(Blocks.LODESTONE), "the vault has its ledger");
		check(level.getBlockState(bank.world(BuildingType.BANK_DOOR)).is(Blocks.IRON_DOOR), "and its door");
		Map<String, Object> vault = RichesLink.info(level, bank.world(BuildingType.BANK_LEDGER));
		check(vault != null && "vault".equals(vault.get("kind")) && ("pool:colonycraft:" + bank.id).equals(vault.get("owner")),
			"Riches piles the bank's vault around the ledger (" + vault + ")");
		Map<String, Object> door = RichesLink.info(level, bank.world(BuildingType.BANK_DOOR));
		check(door != null && "door".equals(door.get("kind")) && "".equals(door.get("owner")), "the vault door opens for anyone");
		UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
		Bank.credit(stranger, "Stranger", Bank.cents(500));
		check(Street.deposit(OWNER, bank, Bank.cents(1000)) && bank.vault == Bank.cents(1000), "paying into the vault");
		check(Colonies.vaultOf(bank.id) == Bank.cents(1000), "Riches sees what's in the vault");
		check(Colonies.whyNoDemolish(bank) != null, "a bank with money in the vault can't be knocked down");
		check(!Street.deposit(stranger, bank, Bank.cents(600)), "nobody pays in more than they have");
		long before = Bank.balance(stranger);
		check(Street.withdraw(stranger, "Stranger", bank, Bank.cents(1500)) == Bank.cents(1000) && Bank.balance(stranger) - before == Bank.cents(1000)
			&& bank.vault == 0, "anyone can take out what's in it, and no more");

		// the museum: Riches display cases and pedestals, four more per tier
		for (int i = 0; i < BuildingType.shows(1); i++) {
			Map<String, Object> show = RichesLink.info(level, museum.world(BuildingType.show(i)));
			check(show != null && "case".equals(show.get("kind")) && OWNER.toString().equals(show.get("owner")), "museum case " + i + " is a Riches display case");
		}
		Colonies.upgrade(level, museum, 0);
		Colonies.upgrade(level, museum, 0);
		Colonies.finishJobs();
		check(level.getBlockState(museum.world(BuildingType.show(7))).is(Blocks.GLASS), "a tier 3 museum has more cases");
		Map<String, Object> pedestal = RichesLink.info(level, museum.world(BuildingType.show(11)));
		check(pedestal != null && "pedestal".equals(pedestal.get("kind")), "and pedestals down the middle");
		check(RichesLink.occupied(level, Street.shows(museum)) == 0 && Colonies.whyNoDemolish(museum) == null, "an empty museum");

		// the chapel: replacing the dead costs a quarter less; the library: a level-30 table
		check(chapel.alive() == 1 && Colonies.replacePrice(colony) == Bank.cents(75), "a chapel makes replacements cheaper ("
			+ Bank.format(Colonies.replacePrice(colony)) + ")");
		BlockPos table = library.world(BuildingType.ENCHANTING);
		check(level.getBlockState(table).is(Blocks.ENCHANTING_TABLE), "the library has its enchanting table");
		int shelves = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					BlockPos at = table.offset(dx, dy, dz);
					BlockPos between = table.offset(dx / 2, dy, dz / 2);
					if (ring && level.getBlockState(at).is(Blocks.BOOKSHELF) && level.getBlockState(between).isAir()) {
						shelves++;
					}
				}
			}
		}
		check(shelves >= 15, "with at least 15 bookshelves around it, so it enchants at level 30 (" + shelves + ")");
		check(library.villagers.isEmpty() && library.type.maxTier() == 1, "the library needs no staff and no upgrades");

		// the ranch's animals, in their paddock
		BlockPos c = ranch.origin;
		int animals = level.getEntities((Entity) null, new AABB(c.getX() - 7, c.getY(), c.getZ() - 7, c.getX() + 8, c.getY() + 5, c.getZ() + 8),
			e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().matches("cow|sheep|pig|chicken")).size();
		check(animals == 7, "the ranch has its cows, sheep, pigs and chickens (" + animals + ")");

		// the fuel depot: Fossil Fool tanks (crude left, diesel right), a refinery, a pipe manifold out both sides
		Map<String, Object> crude = FossilLink.info(level, depot.world(BuildingType.depotTank(0)));
		Map<String, Object> diesel = FossilLink.info(level, depot.world(BuildingType.depotTank(1)));
		check(crude != null && "tank".equals(crude.get("kind")) && "CRUDE".equals(crude.get("set")), "the depot's left tank takes crude (" + crude + ")");
		check(diesel != null && "DIESEL".equals(diesel.get("set")), "the right one diesel");
		Map<String, Object> refinery = FossilLink.info(level, depot.world(BuildingType.DEPOT_REFINERY));
		check(refinery != null && "refinery".equals(refinery.get("kind")), "the depot has its refinery");
		Map<String, Object> inlet = FossilLink.info(level, depot.world(new BlockPos(6, 1, BuildingType.DEPOT_PIPE_Z)));
		check(inlet != null && "pipe".equals(inlet.get("kind")), "and a pipe manifold coming out of the side wall");
		Colonies.upgrade(level, depot, 0);
		Colonies.finishJobs();
		Map<String, Object> more = FossilLink.info(level, depot.world(BuildingType.depotTank(3)));
		check(more != null && "DIESEL".equals(more.get("set")), "a tier 2 depot has four tanks");
		check(Colonies.whyNoDemolish(depot) == null, "an empty depot can go");

		// the guildhouse: a Sellswords Mercenary Station (cheaper hiring, more so per tier) and a Fuck Illagers Bounty Station
		BlockPos hiring = guild.world(BuildingType.GUILD_MERCENARIES);
		BlockPos bounties = guild.world(BuildingType.GUILD_BOUNTIES);
		check(level.getBlockState(hiring).is(Blocks.TARGET) && level.getBlockState(bounties).is(Blocks.FLETCHING_TABLE),
			"the guildhouse has its target and its fletching table");
		Map<String, Object> station = GuildLink.mercenaryInfo(level, hiring);
		check(station != null && Math.abs(((Number) station.get("discount")).doubleValue() - 0.20) < 1e-9
			&& "Testville Guildhouse".equals(station.get("name")), "Sellswords hires at the guildhouse for 20% less (" + station + ")");
		check(GuildLink.isBountyStation(level, bounties), "and Fuck Illagers posts contracts at its fletching table");
		check(guild.alive() == 1, "the guildmaster is in");
		Colonies.upgrade(level, guild, 0);
		Colonies.upgrade(level, guild, 0);
		Colonies.finishJobs();
		station = GuildLink.mercenaryInfo(level, hiring);
		check(station != null && Math.abs(((Number) station.get("discount")).doubleValue() - 0.50) < 1e-9, "a tier 3 guildhouse hires at half price");
		check(level.getBlockState(guild.world(new BlockPos(0, 4, 4))).is(Blocks.GOLD_BLOCK), "with a trophy over the hearth");

		// payday: the ranch and apiary deliver; the trading post, bank and museum earn more than their staff cost
		Colony.Building store = find(BuildingType.STOREHOUSE);
		empty(store);
		long earnings = 0;
		for (Colony.Building b : List.of(shop, bank, museum)) {
			long earns = Street.earnings(level, b);
			check(earns > Bank.cents(b.type.wage) * b.alive(), b.type.id + " earns more (" + Bank.format(earns) + ") than its staff cost");
			earnings += earns;
		}
		check(Street.earnings(level, shop) == Bank.cents(100) && Street.earnings(level, museum) == Bank.cents(75), "earnings go up with the tier");
		Bank.credit(OWNER, "SmokeTest", Bank.cents(10_000));
		long wages = colony.dailyWages();
		before = Bank.balance(OWNER);
		Colonies.payday(colony);
		check(!colony.striking, "the wages were paid");
		check(Bank.balance(OWNER) - before == earnings - wages, "payday paid " + Bank.format(earnings) + " in earnings and " + Bank.format(wages)
			+ " in wages (" + Bank.format(Bank.balance(OWNER) - before) + ")");
		check(count(store, Items.LEATHER) + count(store, Items.EGG) > 0, "the ranch delivered");
		check(count(store, Items.HONEYCOMB) > 0, "the apiary delivered");
		check(count(store, Items.COOKED_BEEF) + count(store, Items.BEEF) > 0, "beef, cooked or not");

		// knocking them down clears up after them
		BlockPos ledger = bank.world(BuildingType.BANK_LEDGER);
		BlockPos tank = depot.world(BuildingType.depotTank(0));
		for (Colony.Building b : built) {
			check(Colonies.whyNoDemolish(b) == null, b.type.id + " can be demolished");
			Colonies.demolish(level, b);
		}
		check(RichesLink.info(level, ledger) == null && FossilLink.info(level, tank) == null, "Riches and Fossil Fool forget the demolished buildings");
		check(GuildLink.mercenaryInfo(level, hiring) == null && !GuildLink.isBountyStation(level, bounties), "and so do Sellswords and Fuck Illagers");
		ColonycraftMod.LOG.info("[smoke] ok: the town street");
	}

	/**
	 * Proost (on the smoke test's server): the distillery's day planner, then a real distillery (named Mash Tuns and
	 * Oak Casks, stills over lit campfires) that bottles the storehouse's wheat on payday, and a tavern whose barkeep
	 * stays behind the bar, prices its bottles and pours the colony's bottles for money. Then tier 3 of both.
	 */
	private static void proost(ServerLevel level) {
		Production.Distilled day = Production.distill(1, 2, 100, 0, 16, 3);
		check(day.total() == 6 && day.bottles().get("brandy") == 2 && day.bottles().get("genever") == 3 && day.bottles().get("whisky") == 1
			&& day.wheat() == 32 && day.apples() == 16 && day.berries() == 3 && day.cane() == 0,
			"a distillery's day: apples into brandy first, genever while the berries last, then whisky (" + day + ")");
		Production.Distilled cellar = Production.distill(3, 4, 1000, 0, 0, 0);
		check(cellar.bottles().get("whisky") == 18 && cellar.bottles().get("whisky:aged") == 6, "a tier 3 cellar ages one bottle in four (" + cellar + ")");
		check(Production.distill(1, 2, 7, 7, 7, 7).total() == 0, "under 8 crops, no bottle");
		if (!ProostLink.present()) {
			check(!BuildingType.DISTILLERY.available() && !BuildingType.TAVERN.available(), "without Proost, no distilleries or taverns for sale");
			return;
		}
		check(BuildingType.DISTILLERY.available() && BuildingType.TAVERN.available(), "with Proost, distilleries and taverns are for sale");
		ItemStack rum = ProostLink.item("rum", 1);
		ItemStack opened = rum.copy();
		opened.set(DataComponents.MAX_DAMAGE, 4);
		check(ProostLink.isSealedBottle(rum) && !ProostLink.isSealedBottle(opened) && !ProostLink.isSealedBottle(new ItemStack(Items.PAPER)),
			"a sealed bottle is one; an opened one and plain paper aren't");
		for (Colony.Building s : Colonies.storesOf(colony)) {
			empty(s);
			s.autosell = false;
		}

		Colony.Building distillery = Colonies.construct(level, colony, BuildingType.DISTILLERY, new BlockPos(4, 99, -46), 0,
			Bank.cents(BuildingType.DISTILLERY.price), true);
		check(distillery.alive() == 2, "the distillery has its two distillers");
		check(level.getBlockEntity(distillery.world(BuildingType.TUNS[0])) instanceof Nameable tun && tun.getCustomName() != null
			&& tun.getCustomName().getString().equals("Mash Tun"), "its barrels on the left are Mash Tuns");
		check(level.getBlockEntity(distillery.world(BuildingType.CASKS[0])) instanceof Nameable cask && cask.getCustomName() != null
			&& cask.getCustomName().getString().equals("Oak Cask"), "and on the right Oak Casks");
		BlockPos still = distillery.world(BuildingType.STILLS[0]);
		check(level.getBlockState(still).is(Blocks.WATER_CAULDRON) && level.getBlockState(still.below()).is(Blocks.CAMPFIRE),
			"its still is a cauldron over a campfire");

		List<Colony.Building> stores = Colonies.storesOf(colony);
		Colonies.stow(stores, new ItemStack(Items.WHEAT, 64));
		Colonies.stow(stores, new ItemStack(Items.SWEET_BERRIES, 8));
		Bank.credit(OWNER, "SmokeTest", Bank.cents(10_000));
		Colonies.payday(colony);
		check(!colony.striking, "the wages were paid");
		check(sealed() == 6, "payday: two distillers bottled six bottles of the colony's wheat (" + sealed() + ")");

		Colony.Building tavern = Colonies.construct(level, colony, BuildingType.TAVERN, new BlockPos(22, 99, -46), 0,
			Bank.cents(BuildingType.TAVERN.price), true);
		check(tavern.alive() == 1 && level.getEntity(tavern.villagers.get(0)) instanceof Mob barkeep && barkeep.isNoAi(),
			"the tavern's barkeep stays behind the bar");
		check(level.getEntity(tavern.villagers.get(0)).getCustomName().getString().endsWith("the Barkeep"), "and is called a barkeep");
		ItemStack genever = ProostLink.item("genever", 1);
		long value = Bank.sellValue(genever);
		check(value > 0 && TavernMenu.price(genever, false) == Math.round(value * Street.BAR_MARKUP)
			&& TavernMenu.price(genever, true) < TavernMenu.price(genever, false), "guests pay double at the bar, the owner pays less");
		long poured = Colonies.pour(Colonies.storesOf(colony), Street.pours(tavern.tier));
		check(poured == Math.round(value * 6 * Street.POUR_MARKUP) && sealed() == 0,
			"the tavern pours all six genevers for half again their Market price (" + Bank.format(poured) + ")");
		check(Street.earnings(level, tavern) == Bank.cents(Street.income(BuildingType.TAVERN, 1)) && Street.income(BuildingType.TAVERN, 1) > 0,
			"and earns a little besides");

		Colonies.upgrade(level, distillery, 0);
		Colonies.upgrade(level, distillery, 0);
		Colonies.upgrade(level, tavern, 0);
		Colonies.upgrade(level, tavern, 0);
		Colonies.finishJobs();
		check(distillery.alive() == 4 && level.getBlockState(distillery.world(BuildingType.STILLS[2])).is(Blocks.WATER_CAULDRON),
			"a tier 3 distillery has four distillers and three stills");
		check(level.getBlockEntity(distillery.world(BuildingType.CASKS[7])) instanceof Nameable top && top.getCustomName() != null
			&& top.getCustomName().getString().equals("Oak Cask"), "and casks stacked two high in its aging cellar");
		check(tavern.alive() == 3 && BuiltInRegistries.BLOCK.getKey(level.getBlockState(tavern.world(new BlockPos(-3, 6, 4))).getBlock()).getPath().endsWith("_bed"),
			"a tier 3 tavern has three barkeeps and guest beds upstairs");
		for (Colony.Building b : List.of(distillery, tavern)) {
			check(Colonies.whyNoDemolish(b) == null, b.type.id + " can be demolished");
			Colonies.demolish(level, b);
		}
		ColonycraftMod.LOG.info("[smoke] ok: the distillery and the tavern");
	}

	/** De Compagnie (on the smoke test's server): a Company House whose cartography table is a built-in Company Desk. */
	private static void compagnie(ServerLevel level) {
		if (!CompagnieLink.present()) {
			check(!BuildingType.COMPANY_HOUSE.available(), "without De Compagnie, no Company Houses for sale");
			return;
		}
		check(BuildingType.COMPANY_HOUSE.available(), "with De Compagnie, Company Houses are for sale");
		Colony.Building house = Colonies.construct(level, colony, BuildingType.COMPANY_HOUSE, new BlockPos(4, 99, -46), 0,
			Bank.cents(BuildingType.COMPANY_HOUSE.price), true);
		BlockPos desk = house.world(BuildingType.COMPANY_DESK);
		check(level.getBlockState(desk).is(Blocks.CARTOGRAPHY_TABLE) && CompagnieLink.isDesk(level, desk),
			"the Company House's cartography table is a Company Desk");
		check(house.alive() == 1, "a clerk keeps the books");
		Colonies.upgrade(level, house, 0);
		Colonies.upgrade(level, house, 0);
		Colonies.finishJobs();
		check(house.tier == 3 && CompagnieLink.isDesk(level, desk) && level.getBlockState(house.world(new BlockPos(0, 7, 4))).is(Blocks.GOLD_BLOCK),
			"a tier 3 Company House keeps its desk, and gets a trophy");
		check(Colonies.whyNoDemolish(house) == null, "it can be demolished");
		Colonies.demolish(level, house);
		check(!CompagnieLink.isDesk(level, desk), "and De Compagnie forgets the desk");
		ColonycraftMod.LOG.info("[smoke] ok: the Company House");
	}

	/** Sealed Proost bottles in all the colony's storehouses, slots and warehouse shelves. */
	private static int sealed() {
		int n = 0;
		for (Colony.Building s : Colonies.storesOf(colony)) {
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				if (ProostLink.isSealedBottle(stack)) {
					n += stack.getCount();
				}
			}
			String warehouse = Colonies.warehouseOf(s);
			if (warehouse != null) {
				for (Map.Entry<ItemStack, Long> e : WarehouseLink.stock(warehouse)) {
					if (ProostLink.isSealedBottle(e.getKey())) {
						n += e.getValue().intValue();
					}
				}
			}
		}
		return n;
	}

	private static void cleanup(ServerLevel level) {
		int workers = 0;
		for (Entity e : level.getAllEntities()) {
			if (e.hasAttached(ColonycraftMod.WORKER) && e.isAlive()) {
				workers++;
			}
		}
		check(workers == 0, "every worker left (" + workers + " still around)");
		int prisoners = 0;
		for (Entity e : level.getAllEntities()) {
			if (e.hasAttached(ColonycraftMod.PRISONER) && e.isAlive()) {
				prisoners++;
			}
		}
		check(prisoners == 0, "no prisoners left behind (" + prisoners + ")");
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

	/** How many of the item a storehouse holds: on its warehouse shelves (with the Warehouse mod) and in its own slots. */
	private static int count(Colony.Building store, Item item) {
		return (int) Colonies.count(List.of(store), item);
	}

	/** Empties a storehouse completely, warehouse and all. */
	private static void empty(Colony.Building store) {
		store.storage.clearContent();
		String warehouse = Colonies.warehouseOf(store);
		if (warehouse != null) {
			for (Map.Entry<ItemStack, Long> e : WarehouseLink.stock(warehouse)) {
				WarehouseLink.take(warehouse, e.getKey(), e.getValue());
			}
		}
	}

	/** Slots in use, or kinds of items on the shelves of its warehouse. */
	private static int used(Colony.Building store) {
		String warehouse = Colonies.warehouseOf(store);
		int n = warehouse == null ? 0 : WarehouseLink.stock(warehouse).size();
		for (int i = 0; i < store.storage.getContainerSize(); i++) {
			n += store.storage.getItem(i).isEmpty() ? 0 : 1;
		}
		return n;
	}

	private static void check(boolean ok, String what) {
		check(ok, what, true);
	}

	private static void check(boolean ok, String what, boolean log) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		if (log) {
			ColonycraftMod.LOG.info("[smoke] ok: {}", what);
		}
	}
}
