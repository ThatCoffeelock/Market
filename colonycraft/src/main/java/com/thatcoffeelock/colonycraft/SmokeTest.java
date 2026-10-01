package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Only runs with -Dcolonycraft.smokeTest=true (CI). Boots a real server (with the Market mod), checks
 * every design, founds a colony, builds one of everything, runs paydays (normal, autosell, strike),
 * kills and replaces a worker, saves and reloads, builds a fortified line (walls, gatehouse, watchtower)
 * and a barracks, lets the archers shoot at a husk, repairs and upgrades, then demolishes it all again.
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
		Colony.Building barracks = Colonies.construct(level, colony, BuildingType.BARRACKS, new BlockPos(24, 99, 12), 0,
			Bank.cents(BuildingType.BARRACKS.price), true);
		check(colony.slotsUsed() == 9, "fortifications don't use building slots (" + colony.slotsUsed() + " used)");
		check(level.getBlockState(wallA.world(new BlockPos(2, 3, -2))).is(Blocks.LADDER), "the wall has its ladder");
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

		// upgrading a wall rebuilds it in stone bricks
		Colonies.upgrade(level, wallA, 0);
		ColonycraftMod.later(200, () -> step(level.getServer(), () -> afterVolley(level)));
	}

	private static void afterVolley(ServerLevel level) {
		check(Colonies.shots > shotsBefore, "the archer shot at the husk (" + (Colonies.shots - shotsBefore) + " arrows)");
		Entity target = level.getEntity(husk);
		check(target == null || !target.isAlive() || (target instanceof LivingEntity l && l.getHealth() < l.getMaxHealth()),
			"the husk got hit");
		check(level.getBlockState(wallA.world(new BlockPos(0, 1, 1))).is(Blocks.POLISHED_ANDESITE), "the upgraded wall is rebuilt in stone");
		check(Colonies.damaged(level, wallA) == 0, "the upgraded wall is complete");
		Cmd.run(level, "kill @e[type=minecraft:husk]");
		Cmd.run(level, "kill @e[type=minecraft:arrow]");

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
