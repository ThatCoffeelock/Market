package com.thatcoffeelock.burlapsack;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dburlapsack.smokeTest=true (CI). Boots a real server, bags a named level 3 farmer, lets them out
 * somewhere else and checks they're still the same farmer (with a grudge). Then does a wandering trader, checks colony
 * workers are refused and that iron golems go after the culprit, and shuts down.
 */
final class SmokeTest {
	private static final UUID FARMER = UUID.randomUUID();
	private static final UUID WORKER = UUID.randomUUID();
	private static final UUID TRADER = UUID.randomUUID();
	private static final UUID GOLEM = UUID.randomUUID();
	private static final UUID CULPRIT = UUID.randomUUID();
	private static final UUID KIDNAPPER = UUID.randomUUID();

	private static ItemStack farmerSack;
	private static ItemStack traderSack;

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -32 -32 32 32");
			BurlapSackMod.later(100, () -> step(server, () -> bag(level)));
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

	private static void bag(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Cmd.run(level, "fill -20 99 -20 20 99 20 minecraft:stone");
		Cmd.run(level, "fill -20 100 -20 20 110 20 minecraft:air");

		ItemStack empty = SackItems.empty();
		check(SackItems.isEmptySack(empty) && !SackItems.isFullSack(empty), "empty sack is recognised");
		check(net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get(BurlapSackMod.EMPTY_SACK) instanceof java.util.function.Supplier<?> sacks
			&& sacks.get() instanceof ItemStack fresh && SackItems.isEmptySack(fresh), "the empty-sack hook for Colonycraft is published");
		check(empty.is(Items.BUNDLE), "the sack is a bundle to vanilla clients");
		check(empty.get(DataComponents.BUNDLE_CONTENTS) == null, "the sack can't hold items");
		check(!SackItems.isEmptySack(new ItemStack(Items.BUNDLE)), "a plain bundle is not a sack");

		Cmd.run(level, "summon minecraft:villager 0.5 100 0.5 {" + Cmd.uuidNbt(FARMER) + ",NoAI:1b,PersistenceRequired:1b,CustomName:\"Bob\","
			+ "VillagerData:{profession:\"minecraft:farmer\",level:3,type:\"minecraft:plains\"},Xp:50}");
		Entity farmer = level.getEntity(FARMER);
		check(farmer != null && Sacks.typeId(farmer).equals(Sacks.VILLAGER), "farmer summoned");
		check(Sacks.fits(farmer), "a villager fits in the sack");
		check(Sacks.method(farmer, Sacks.RELEASE_POIS) != null, "villagers still have " + Sacks.RELEASE_POIS + "()");
		check(Sacks.method(farmer, Sacks.IS_TRADING) != null, "villagers still have " + Sacks.IS_TRADING + "()");

		CompoundTag captive = Sacks.save(farmer);
		check(!captive.contains("UUID") && !captive.contains("Pos") && !captive.contains("Brain"), "identity, position and memories are left behind");
		check("minecraft:farmer".equals(captive.getCompoundOrEmpty("VillagerData").getStringOr("profession", "")), "profession is saved");
		check("a level 3 farmer".equals(SackItems.describe("minecraft:villager", captive)),
			"sack describes the captive (" + SackItems.describe("minecraft:villager", captive) + ")");
		check(!Sacks.isColonyWorker(captive), "a plain villager is not a colony worker");
		Sacks.grudge(captive, KIDNAPPER);
		Sacks.bag(farmer);
		check(farmer.isRemoved(), "farmer is gone from the world");
		farmerSack = SackItems.full("minecraft:villager", "Bob", captive);
		check(SackItems.isFullSack(farmerSack) && !SackItems.isEmptySack(farmerSack), "full sack is recognised");
		check("Bob".equals(SackItems.name(farmerSack)), "full sack knows who's inside");

		Cmd.run(level, "summon minecraft:villager 3.5 100 3.5 {" + Cmd.uuidNbt(WORKER) + ",NoAI:1b,Tags:[\"colonycraft\"]}");
		Entity worker = level.getEntity(WORKER);
		check(worker != null && Sacks.isColonyWorker(Sacks.save(worker)), "colony workers are recognised");
		worker.discard();

		Cmd.run(level, "summon minecraft:wandering_trader -3.5 100 -3.5 {" + Cmd.uuidNbt(TRADER) + ",NoAI:1b,DespawnDelay:24000}");
		Entity trader = level.getEntity(TRADER);
		check(trader != null && Sacks.fits(trader), "a wandering trader fits in the sack");
		var share = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare();
		Object realApi = share.get("sellswords:api");
		Entity merc = trader;
		share.put("sellswords:api", (java.util.function.BiFunction<String, java.util.Map<String, Object>, Object>) (op, args) ->
			"is_mercenary".equals(op) && args.get("entity") == merc);
		check(!Sacks.fits(trader), "a Sellswords mercenary doesn't (Sellswords stood in for)");
		if (realApi == null) {
			share.remove("sellswords:api");
		} else {
			share.put("sellswords:api", realApi);
		}
		CompoundTag traderData = Sacks.save(trader);
		check(traderData.getIntOr("DespawnDelay", -1) == 0, "a bagged wandering trader never leaves again");
		Sacks.bag(trader);
		traderSack = SackItems.full("minecraft:wandering_trader", "Wandering Trader", traderData);

		Cmd.run(level, "summon minecraft:iron_golem 6.5 100 0.5 {" + Cmd.uuidNbt(GOLEM) + ",NoAI:1b}");
		Cmd.run(level, "summon minecraft:pig 12.5 100 0.5 {" + Cmd.uuidNbt(CULPRIT) + ",NoAI:1b}");
		check(level.getEntity(CULPRIT) instanceof LivingEntity, "culprit summoned");
		int golems = Sacks.alertGolems(level, (LivingEntity) level.getEntity(CULPRIT), 0.5, 100, 0.5);
		check(golems == 1, "one iron golem witnessed it (" + golems + ")");
		check(level.getEntity(GOLEM) instanceof Mob golem && golem.getTarget() == level.getEntity(CULPRIT), "the golem goes after the culprit");

		BurlapSackMod.later(5, () -> step(server, () -> release(level)));
	}

	private static void release(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(level.getEntity(FARMER) == null && level.getEntity(TRADER) == null, "bagged entities stay gone");

		Entity bob = Sacks.release(level, farmerSack, 0.5, 100, 8.5, 90f);
		check(bob != null && Sacks.typeId(bob).equals(Sacks.VILLAGER), "farmer let out of the sack");
		check(!FARMER.equals(bob.getUUID()), "the freed farmer gets a new UUID");
		check("Bob".equals(bob.getName().getString()), "the freed farmer is still called Bob (" + bob.getName().getString() + ")");
		CompoundTag after = Sacks.save(bob);
		CompoundTag data = after.getCompoundOrEmpty("VillagerData");
		check("minecraft:farmer".equals(data.getStringOr("profession", "")) && data.getIntOr("level", 0) == 3,
			"the freed farmer is still a level 3 farmer (" + data + ")");
		check(after.getIntOr("Xp", 0) == 50, "the freed farmer kept their XP");
		check(after.getListOrEmpty("Gossips").toString().contains("minor_negative"), "the freed farmer holds a grudge (" + after.getListOrEmpty("Gossips") + ")");

		Entity trader = Sacks.release(level, traderSack, -3.5, 100, 8.5, 0f);
		check(trader != null && Sacks.typeId(trader).equals(Sacks.WANDERING_TRADER), "wandering trader let out of the sack");
		check(Sacks.save(trader).getIntOr("DespawnDelay", -1) == 0, "the freed trader stays for good");

		check(Sacks.release(level, SackItems.empty(), 0.5, 100, 0.5, 0f) == null, "an empty sack releases nobody");

		bob.discard();
		trader.discard();
		level.getEntity(GOLEM).discard();
		level.getEntity(CULPRIT).discard();
		BurlapSackMod.LOG.info("BURLAP SACK SMOKE TEST PASSED");
		server.halt(false);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		BurlapSackMod.LOG.error("BURLAP SACK SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		BurlapSackMod.LOG.info("[smoke] ok: {}", what);
	}
}
