package com.thatcoffeelock.fuckillagers;

import java.util.List;
import java.util.UUID;

import com.thatcoffeelock.market.MarketData;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Only runs with -Dfuckillagers.smokeTest=true (CI). Boots a real server and goes through it all: the items, a
 * station placed and broken, fingers from a player kill (and none from a non-player kill), selling, accepting and
 * abandoning contracts, building all six hideouts with their bosses and loot, killing a boss for its skull and
 * cashing it in, and saving and loading.
 */
final class SmokeTest {
	@FunctionalInterface
	private interface Step {
		void run();
	}

	private static final int[][] SPOTS = {{300, 0}, {300, 120}, {300, 240}, {450, 0}, {450, 160}, {620, 0}};

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int[] s : SPOTS) {
			Cmd.run(level, "forceload add " + (s[0] - 24) + " " + (s[1] - 24) + " " + (s[0] + 24) + " " + (s[1] + 24));
		}
		Cmd.run(level, "forceload add -16 -16 16 16");
		FuckIllagersMod.later(100, () -> step(server, () -> basics(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			FuckIllagersMod.LOG.error("FUCK ILLAGERS SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void basics(ServerLevel level) {
		check(Trophies.isStation(Trophies.station()) && !Trophies.isStation(new ItemStack(Blocks.FLETCHING_TABLE)), "a Bounty Station is a tagged fletching table");
		check(Trophies.isFinger(Trophies.finger(3)) && Trophies.finger(3).getCount() == 3, "fingers are tagged bones");
		check("N".equals(Bounties.direction(0, -10)) && "E".equals(Bounties.direction(10, 0)) && "SW".equals(Bounties.direction(-5, 5)), "compass directions");

		// a station: remembered, and gives itself back when broken
		FakePlayer player = FakePlayer.get(level);
		BlockPos pos = new BlockPos(5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 5, 5), 5);
		level.setBlockAndUpdate(pos, Blocks.FLETCHING_TABLE.defaultBlockState());
		Bounties.addStation(level, pos);
		check(Bounties.isStation(level, pos), "a placed station is remembered");
		check(!Stations.allowBreak(player, level, pos) && level.getBlockState(pos).isAir() && !Bounties.isStation(level, pos), "breaking it removes it");
		check(!level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), e -> Trophies.isStation(e.getItem())).isEmpty(), "and drops a Bounty Station, not a plain fletching table");

		// fingers: only from player kills
		BlockPos ground = pos.above(2);
		LivingEntity killed = summon(level, "vindicator", ground);
		Bounties.onDeath(killed, level.damageSources().playerAttack(player));
		List<ItemEntity> fingers = level.getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(3), e -> Trophies.isFinger(e.getItem()));
		check(fingers.size() == 1 && fingers.get(0).getItem().getCount() == 1, "a vindicator killed by a player drops a finger");
		fingers.forEach(Entity::discard);
		// a Sellswords mercenary's kill counts too (Sellswords isn't here, so stand in for its API)
		LivingEntity merc = summon(level, "wandering_trader", ground.offset(6, 0, 0));
		var share = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare();
		Object realApi = share.get("sellswords:api");
		share.put("sellswords:api", (java.util.function.BiFunction<String, java.util.Map<String, Object>, Object>) (op, args) ->
			"is_mercenary".equals(op) && args.get("entity") == merc);
		LivingEntity pillager = summon(level, "pillager", ground);
		Bounties.onDeath(pillager, level.damageSources().mobAttack(merc));
		fingers = level.getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(3), e -> Trophies.isFinger(e.getItem()));
		check(fingers.size() == 1, "a pillager killed by a mercenary drops a finger");
		fingers.forEach(Entity::discard);
		if (realApi == null) {
			share.remove("sellswords:api");
		} else {
			share.put("sellswords:api", realApi);
		}
		merc.discard();
		pillager.discard();
		LivingEntity evoker = summon(level, "evoker", ground);
		Bounties.onDeath(evoker, level.damageSources().playerAttack(player));
		fingers = level.getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(3), e -> Trophies.isFinger(e.getItem()));
		check(fingers.size() == 1 && fingers.get(0).getItem().getCount() == 2, "an evoker drops two");
		fingers.forEach(Entity::discard);
		LivingEntity drowned = summon(level, "vindicator", ground);
		Bounties.onDeath(drowned, level.damageSources().generic());
		check(level.getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(3), e -> Trophies.isFinger(e.getItem())).isEmpty(), "no finger when nobody killed it");
		LivingEntity cow = summon(level, "cow", ground);
		Bounties.onDeath(cow, level.damageSources().playerAttack(player));
		check(level.getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(3), e -> Trophies.isFinger(e.getItem())).isEmpty(), "cows don't have illager fingers");
		killed.discard();
		evoker.discard();
		drowned.discard();
		cow.discard();

		// selling
		player.getInventory().clearContent();
		player.getInventory().add(Trophies.finger(5));
		long before = MarketData.balance(player);
		long paid = Bounties.sellAll(player);
		check(paid == 5 * Bounties.fingerPrice() && MarketData.balance(player) == before + paid, "5 fingers sell for " + paid + " cents");
		check(Bounties.count(player, false) == 0, "and are gone from the inventory");
		check(Bounties.fingerPrice() == 300, "a finger is worth 3 Marks");

		// contracts: one at a time, 1000-2000 blocks away, with a poster
		BlockPos origin = new BlockPos(0, 64, 0);
		Contract c = Bounties.accept(player, level, origin, Tier.EASY);
		check(c != null && Contract.POSTED.equals(c.state), "an easy contract is posted");
		double distance = Math.sqrt((double) c.x * c.x + (double) c.z * c.z);
		check(distance >= Bounties.MIN_DISTANCE - 1 && distance <= Bounties.MAX_DISTANCE + 1, "the target is " + Math.round(distance) + " blocks away");
		check(Tier.EASY.sites.contains(c.site()), "hiding in an easy place (" + c.site().what + ")");
		check(hasPoster(player, c), "the player got a Wanted Poster");
		check(Bounties.accept(player, level, origin, Tier.HARD) == null, "only one contract at a time");
		check(!Sites.loaded(level, c) || c.state.equals(Contract.POSTED), "nothing is built while nobody is near");
		Bounties.abandon(player);
		check(Contract.ABANDONED.equals(c.state) && Bounties.openContract(player.getUUID()) == null, "a contract can be abandoned");

		sites(level, player);
	}

	/** Builds every hideout next to the test area, checks its boss and loot, then cashes in one boss. */
	private static void sites(ServerLevel level, FakePlayer player) {
		Tier.Site[] all = Tier.Site.values();
		Contract wagon = null;
		for (int i = 0; i < all.length; i++) {
			Tier.Site site = all[i];
			Contract c = new Contract();
			c.id = "smoke" + i;
			c.owner = player.getUUID().toString();
			c.ownerName = "Smoke";
			c.site = site.name();
			c.tier = (site == Tier.Site.WAGON || site == Tier.Site.TOWER ? Tier.EASY : site == Tier.Site.CAMP || site == Tier.Site.FORTRESS ? Tier.MEDIUM : Tier.HARD).name();
			c.target = Bounties.randomName();
			c.x = SPOTS[i][0];
			c.z = SPOTS[i][1];
			Bounties.CONTRACTS.put(c.id, c);
			check(Sites.loaded(level, c), site.what + ": the area is loaded");
			Sites.Built built = Sites.build(level, c);
			c.y = built.y();
			c.boss = built.boss() == null ? "" : built.boss().getUUID().toString();
			c.state = Contract.ACTIVE;
			Mob boss = built.boss();
			check(boss != null && boss.isAlive(), site.what + ": the boss is there");
			check(c.target.equals(boss.getCustomName().getString()) && boss.isCustomNameVisible(), site.what + ": named " + c.target);
			check(Math.abs(boss.getMaxHealth() - c.tier().bossHealth) < 0.01 && Math.abs(boss.getHealth() - c.tier().bossHealth) < 0.01,
				site.what + ": " + boss.getMaxHealth() + " health");
			check(c.id.equals(boss.getAttachedOrElse(FuckIllagersMod.BOSS, "")), site.what + ": tagged with its contract");
			check(!built.chests().isEmpty(), site.what + ": has loot");
			for (BlockPos chest : built.chests()) {
				check(level.getBlockEntity(chest) instanceof RandomizableContainer r && r.getLootTable() != null,
					site.what + ": loot container at " + chest.toShortString() + " has a loot table");
			}
			int guards = level.getEntitiesOfClass(Mob.class, new AABB(new BlockPos(c.x, c.y, c.z)).inflate(Sites.radius(site) + 2, 24, Sites.radius(site) + 2),
				m -> m != boss && Bounties.fingersFor(m) > 0).size();
			check(guards >= 2, site.what + ": " + guards + " illager guards");
			if (site == Tier.Site.WAGON) {
				wagon = c;
			}
		}

		// kill the wagon's boss: a skull, the contract is done, and the skull pays the reward
		Contract c = wagon;
		Mob boss = (Mob) level.getEntity(UUID.fromString(c.boss));
		Bounties.onDeath(boss, level.damageSources().playerAttack(player));
		check(Contract.DONE.equals(c.state), "killing the boss fulfils the contract");
		List<ItemEntity> skulls = level.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(3), e -> Trophies.isSkull(e.getItem()));
		check(skulls.size() == 1 && c.id.equals(Trophies.contractOf(skulls.get(0).getItem())), "and drops its skull");
		player.getInventory().clearContent();
		player.getInventory().add(skulls.get(0).getItem().copy());
		skulls.get(0).discard();
		long before = MarketData.balance(player);
		long paid = Bounties.sellAll(player);
		check(paid == Bounties.reward(Tier.EASY) && MarketData.balance(player) == before + paid, "the skull sells for the reward (" + paid + " cents)");
		Bounties.onDeath(boss, level.damageSources().playerAttack(player));
		check(level.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(3), e -> Trophies.isSkull(e.getItem())).isEmpty(), "a dead contract gives no second skull");

		// a far contract isn't built (or loaded) just because it exists
		Contract far = new Contract();
		far.id = "smokefar";
		far.site = Tier.Site.CASTLE.name();
		far.tier = Tier.HARD.name();
		far.x = 50000;
		far.z = 50000;
		check(!Sites.loaded(level, far), "a far-away hideout waits until someone comes");

		// saving and loading
		Bounties.save();
		Bounties.load(level.getServer());
		check(Contract.DONE.equals(Bounties.get(c.id).state) && Bounties.get("smoke5") != null, "contracts survive a save and load");

		FuckIllagersMod.LOG.info("FUCK ILLAGERS SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static boolean hasPoster(FakePlayer player, Contract c) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (Trophies.isPoster(stack) && c.id.equals(Trophies.contractOf(stack))) {
				return true;
			}
		}
		return false;
	}

	private static LivingEntity summon(ServerLevel level, String type, BlockPos pos) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:" + type + " " + Cmd.pos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) + " {" + Cmd.uuidNbt(id) + ",NoAI:1b}");
		Entity entity = level.getEntity(id);
		check(entity instanceof LivingEntity, "summoned a " + type);
		return (LivingEntity) entity;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		FuckIllagersMod.LOG.info("[smoke] ok: {}", what);
	}
}
