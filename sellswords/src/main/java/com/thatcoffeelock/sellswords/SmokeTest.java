package com.thatcoffeelock.sellswords;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import com.thatcoffeelock.sellswords.mixin.MobAccessor;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Only runs with -Dsellswords.smokeTest=true (CI). Boots a real server and puts a squad through it: a station placed
 * and broken, a recruit hired (an invisible brain with no goals of its own, a mannequin body in orange), the body
 * keeping up with the brain, hits on the body landing on the brain, no friendly fire, shooting a husk, both promotion
 * paths to the top, the Bulwark's shield, the horn, following, patrolling, dismissing, dying, strays tidied up, the
 * API, and saving and loading.
 */
final class SmokeTest {
	@FunctionalInterface
	private interface Step {
		void run();
	}

	/** Players the tests pretend are online (a FakePlayer isn't in the player list). */
	private static final Map<UUID, ServerPlayer> OWNERS = new HashMap<>();
	/** The tests run on a flat stone platform at this height, so hills and trees don't get in the way. */
	private static int baseY = 64;

	private SmokeTest() {
	}

	static @Nullable ServerPlayer owner(UUID id) {
		return OWNERS.get(id);
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -48 -48 48 48");
		Cmd.run(level, "time set noon");
		Cmd.run(level, "gamerule doMobSpawning false");
		Cmd.run(level, "gamerule spawn_mobs false");
		SellswordsMod.later(100, () -> step(server, () -> {
			baseY = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), level.getSeaLevel() + 1);
			Cmd.run(level, "fill -34 " + (baseY - 1) + " -34 34 " + (baseY - 1) + " 34 minecraft:stone");
			Cmd.run(level, "fill -34 " + baseY + " -34 34 " + (baseY + 3) + " 34 minecraft:air");
			Cmd.run(level, "fill -34 " + (baseY + 4) + " -34 34 " + (baseY + 7) + " 34 minecraft:air");
			Cmd.run(level, "kill @e[type=!minecraft:player,distance=..80,x=0,y=" + baseY + ",z=0]");
			later(server, 20, () -> basics(server, level));
		}));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			SellswordsMod.LOG.error("SELLSWORDS SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void later(MinecraftServer server, int ticks, Step step) {
		SellswordsMod.later(ticks, () -> step(server, step));
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, baseY, z);
	}

	private static String type(Entity e) {
		return Combat.type(e);
	}

	// ---------------------------------------------------------------- 1: the station and a recruit

	private static void basics(MinecraftServer server, ServerLevel level) {
		check(Stations.isItem(Stations.item()) && !Stations.isItem(new ItemStack(Items.TARGET)), "a Mercenary Station is a tagged target block");
		check(Rank.RECRUIT.canBecome(Rank.CROSSBOWMAN) && Rank.RECRUIT.canBecome(Rank.SWORDSMAN) && !Rank.RECRUIT.canBecome(Rank.MARKSMAN)
			&& !Rank.CROSSBOWMAN.canBecome(Rank.MAN_AT_ARMS) && Rank.MUSKETEER.next() == null && Rank.of(Rank.Path.RANGED).size() == 4, "the rank ladder");
		check(Mercs.promotionCost(Rank.MUSKETEER, null) == 40 && Mercs.promotionCost(Rank.CROSSBOWMAN, null) == 5, "promotions cost 5, 10, 20, 40 gold");
		Names.Person person = Names.roll(Mercs.RANDOM);
		check(person.name().contains(" ") && Names.texture(person).startsWith("entity/player/"), "a Dutch name and a vanilla skin: " + person.name());

		FakePlayer player = FakePlayer.get(level);
		OWNERS.put(player.getUUID(), player);
		check(Mercs.squadCap(player.getUUID()) == 3, "a squad of three without Leadership");

		// placing and breaking a station
		BlockPos pos = ground(level, 0, 0);
		level.setBlockAndUpdate(pos, Blocks.TARGET.defaultBlockState());
		Stations.add(level, pos, 0, "Mercenary Station", false);
		check(Stations.at(level, pos) != null, "a placed station is remembered");
		check(!Stations.allowBreak(player, level, pos) && level.getBlockState(pos).isAir() && Stations.at(level, pos) == null, "breaking it removes it");
		check(!level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), e -> Stations.isItem(e.getItem())).isEmpty(),
			"and drops a Mercenary Station, not a plain target");
		level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), e -> true).forEach(Entity::discard);
		level.setBlockAndUpdate(pos, Blocks.TARGET.defaultBlockState());
		Station station = Stations.add(level, pos, 0, "Mercenary Station", false);

		// hiring
		Merc m = Mercs.hire(level, pos, player.getUUID(), "Smokey", station);
		check(m != null, "hired a recruit");
		LivingEntity brain = Mercs.brain(m);
		LivingEntity body = Mercs.body(m);
		check(brain != null && type(brain).equals("wandering_trader") && brain.isInvisible() && brain.isSilent(), "the brain is an invisible, silent wandering trader");
		check(((MobAccessor) brain).sellswords$goals().getAvailableGoals().isEmpty() && ((MobAccessor) brain).sellswords$targets().getAvailableGoals().isEmpty(),
			"the brain has no goals of its own");
		check(body != null && type(body).equals("mannequin"), "the body is a mannequin");
		check(body.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.CROSSBOW) && body.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.IRON_SWORD),
			"a recruit holds a crossbow, with a sword in the other hand");
		check(body.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "and wears an orange leather tunic");
		check(body.getCustomName() != null && body.getCustomName().getString().equals(m.name), "the body wears their name: " + m.name);
		check(Math.abs(brain.getMaxHealth() - Rank.RECRUIT.health) < 0.01, "a recruit has " + (int) Rank.RECRUIT.health + " health");
		check(m.orders() == Merc.Orders.STATION && m.home.equals(station.key()), "a new hire idles at their station");

		// strays: a tagged entity nobody knows about is tidied up
		UUID stray = UUID.randomUUID();
		BlockPos at = ground(level, 20, 20);
		Cmd.run(level, "summon minecraft:wandering_trader " + Cmd.pos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) + " {" + Cmd.uuidNbt(stray) + ",NoAI:1b}");
		Entity strayEntity = level.getEntity(stray);
		check(strayEntity != null, "summoned a stray");
		strayEntity.setAttached(SellswordsMod.MERC, "brain:nobody");
		Mercs.onLoad(strayEntity, level); // as if its chunk had just loaded

		// the body keeps up with the brain
		BlockPos over = ground(level, 4, 6);
		Cmd.tp(level, brain.getUUID(), over.getX() + 0.5, over.getY(), over.getZ() + 0.5);
		later(server, 10, () -> {
			check(level.getEntity(stray) == null, "a stray mercenary with no record is tidied up");
			LivingEntity b = Mercs.brain(m);
			LivingEntity bd = Mercs.body(m);
			check(b != null && bd != null && bd.distanceTo(b) < 0.5, "the body stays where the brain is");
			hits(server, level, player, m, station);
		});
	}

	// ---------------------------------------------------------------- 2: taking hits, and not dealing them to friends

	private static void hits(MinecraftServer server, ServerLevel level, FakePlayer player, Merc m, Station station) {
		LivingEntity brain = Mercs.brain(m);
		LivingEntity body = Mercs.body(m);
		m.orders(Merc.Orders.GUARD);
		m.post(Cmd.dimId(level), brain.blockPosition());
		LivingEntity zombie = summon(level, "zombie", ground(level, -30, -30), true);
		float brainBefore = brain.getHealth();
		float bodyBefore = body.getHealth();
		Cmd.run(level, "damage " + body.getUUID() + " 4 minecraft:mob_attack by " + zombie.getUUID());
		check(brain.getHealth() < brainBefore && body.getHealth() == bodyBefore, "a hit on the body lands on the brain ("
			+ brainBefore + " -> " + brain.getHealth() + ")");
		zombie.discard();

		LivingEntity villager = summon(level, "villager", ground(level, -6, 6), true);
		float before = villager.getHealth();
		Combat.hit(level, m, brain, villager, 10, false);
		Cmd.run(level, "damage " + villager.getUUID() + " 5 minecraft:mob_attack by " + brain.getUUID());
		check(villager.getHealth() == before, "mercenaries can't hurt villagers, not even on purpose");
		check(Combat.friendly(villager) && Combat.friendly(player) && Combat.friendly(brain) && !Combat.hostile(villager), "who's a friend");
		villager.discard();
		brain.heal(100);

		// a husk that can't move, ten blocks off: only bolts can reach it
		BlockPos spot = brain.blockPosition();
		LivingEntity husk = summon(level, "husk", ground(level, spot.getX() + 10, spot.getZ()), true);
		check(Combat.hostile(husk), "a husk is a foe");
		int hitsBefore = Bolt.hits;
		later(server, 160, () -> {
			check(Bolt.hits > hitsBefore, "the recruit shot the husk (" + (Bolt.hits - hitsBefore) + " hits)");
			check(!husk.isAlive() || husk.getHealth() < husk.getMaxHealth(), "and hurt it");
			if (husk.isAlive()) {
				husk.discard();
			} else {
				check(m.kills >= 1, "the kill counts (" + m.kills + ")");
			}
			ranks(server, level, player, m, station);
		});
	}

	// ---------------------------------------------------------------- 3: both paths to the top

	private static void ranks(MinecraftServer server, ServerLevel level, FakePlayer player, Merc m, Station station) {
		for (Rank r : Rank.of(Rank.Path.RANGED)) {
			check(m.rank().canBecome(r), m.rank().title + " can become a " + r.title);
			Mercs.promote(m, r);
		}
		LivingEntity brain = Mercs.brain(m);
		LivingEntity body = Mercs.body(m);
		check(m.rank() == Rank.MUSKETEER && Math.abs(brain.getMaxHealth() - 40) < 0.01 && brain.getHealth() == brain.getMaxHealth(),
			"a Musketeer: 40 health, healed up on promotion");
		check(body.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.CROSSBOW) && body.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),
			"a Musketeer holds the musket and wears the hat");
		check(!m.rank().canBecome(Rank.BULWARK) && !Rank.MUSKETEER.canBecome(Rank.SWORDSMAN), "no switching paths");

		Merc wall = Mercs.hire(level, station.pos(), player.getUUID(), "Smokey", station);
		check(wall != null, "hired a second recruit");
		for (Rank r : Rank.of(Rank.Path.MELEE)) {
			Mercs.promote(wall, r);
		}
		LivingEntity wb = Mercs.brain(wall);
		LivingEntity wbody = Mercs.body(wall);
		check(wall.rank() == Rank.BULWARK && Math.abs(wb.getMaxHealth() - 72) < 0.01
			&& Math.abs(wb.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 1.0) < 0.01, "a Foestopper Bulwark: 72 health, can't be knocked back");
		check(wbody.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD) && wbody.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_SWORD)
			&& wbody.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE), "in netherite, with a netherite sword and a shield");

		// the shield: hits from the front are partly blocked, from behind they aren't
		wb.setYRot(0);
		wb.setYHeadRot(0);
		LivingEntity front = summon(level, "zombie", wb.blockPosition().offset(0, 0, 3), true);
		LivingEntity back = summon(level, "zombie", wb.blockPosition().offset(0, 0, -3), true);
		float fromFront = Combat.incoming(wb, level.damageSources().mobAttack(front), 10);
		float fromBack = Combat.incoming(wb, level.damageSources().mobAttack(back), 10);
		check(fromFront < 5 && fromBack == 10, "the Bulwark's shield takes 60% off a hit from the front (" + fromFront + "), none from behind (" + fromBack + ")");
		front.discard();
		back.discard();

		// dismissing
		UUID wbId = wb.getUUID();
		UUID wbodyId = wbody.getUUID();
		Mercs.dismiss(wall);
		check(!Mercs.ALL.containsKey(wall.id) && level.getEntity(wbId) == null && level.getEntity(wbodyId) == null, "a dismissed mercenary is gone");
		orders(server, level, player, m, station);
	}

	// ---------------------------------------------------------------- 4: orders, the horn, patrolling

	private static void orders(MinecraftServer server, ServerLevel level, FakePlayer player, Merc m, Station station) {
		LivingEntity brain = Mercs.brain(m);
		BlockPos near = ground(level, brain.getBlockX() + 2, brain.getBlockZ());
		player.setPos(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		m.orders(Merc.Orders.GUARD);
		check(Horn.blow(player, false) == 1 && m.orders() == Merc.Orders.FOLLOW, "the horn rallies a guard to follow");
		check(Horn.blow(player, false) == 1 && m.orders() == Merc.Orders.GUARD && m.post().closerThan(brain.blockPosition(), 2),
			"and blown again, the follower holds where they stand");

		// following: too far, and they catch up
		m.orders(Merc.Orders.FOLLOW);
		BlockPos far = ground(level, 30, -30);
		player.setPos(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		check(Duty.teleportNear(m, brain, player, true), "a follower left behind catches up");
		later(server, 5, () -> {
			LivingEntity b = Mercs.brain(m);
			check(b != null && b.distanceTo(player) < 5, "and is next to the owner (" + (b == null ? "?" : String.format("%.1f", b.distanceTo(player))) + " blocks)");

			// patrolling around the station
			m.orders(Merc.Orders.STATION);
			Duty.state(m).wander = null;
			later(server, 40, () -> {
				BlockPos w = Duty.wanderTarget(m);
				check(w != null && Math.sqrt(w.distSqr(station.pos())) <= SellswordsConfig.get().stationRadius + 2,
					"idle at the station, they patrol within " + SellswordsConfig.get().stationRadius + " blocks of it (" + w + ")");
				api(server, level, player, m);
			});
		});
	}

	// ---------------------------------------------------------------- 5: the API, death, saving

	@SuppressWarnings("unchecked")
	private static void api(MinecraftServer server, ServerLevel level, FakePlayer player, Merc m) {
		Object hook = FabricLoader.getInstance().getObjectShare().get(SellswordsApi.KEY);
		check(hook instanceof BiFunction<?, ?, ?>, "the API is published");
		var api = (BiFunction<String, Map<String, Object>, Object>) hook;
		BlockPos guild = ground(level, -20, 20);
		level.setBlockAndUpdate(guild, Blocks.TARGET.defaultBlockState());
		check(Boolean.TRUE.equals(api.apply("station", Map.of("level", level, "pos", guild, "discount", 0.5, "name", "Smoketown Guildhouse"))), "a Guildhouse registers its station");
		Object info = api.apply("info", Map.of("level", level, "pos", guild));
		check(info instanceof Map<?, ?> i && ((Number) i.get("hire_cents")).longValue() == Math.round(SellswordsConfig.get().hireCost * 50),
			"and hiring there is half price");
		check(Boolean.TRUE.equals(api.apply("is_mercenary", Map.of("entity", Mercs.brain(m)))), "the API knows a mercenary when it sees one");
		check(Boarding.hooks() != null, "the boarding hook list is there for ships and airships");

		// saving and loading
		Mercs.save();
		int count = Mercs.ALL.size();
		Mercs.load(server);
		check(Mercs.ALL.size() == count && Mercs.ALL.containsKey(m.id) && Stations.ALL.size() == 2, "mercenaries and stations survive a save and load");
		Merc again = Mercs.ALL.get(m.id);
		check(again.rank() == Rank.MUSKETEER && again.kills == m.kills, "with their rank and kills");
		// the loaded entities are found again by their tags
		for (Entity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(-64, -64, -64, 64, 320, 64), Mercs::isMerc)) {
			Mercs.onLoad(e, level);
		}
		LivingEntity brain = Mercs.brain(again);
		check(brain != null && Mercs.body(again) != null, "brain and body are found again by their tags");

		// death is for good
		UUID bodyId = Mercs.body(again).getUUID();
		Cmd.run(level, "kill " + brain.getUUID());
		later(server, 30, () -> {
			check(!Mercs.ALL.containsKey(again.id), "a dead mercenary is gone for good");
			Entity body = level.getEntity(bodyId);
			check(body == null || !body.isAlive(), "and their body falls");
			check(level.getEntitiesOfClass(ItemEntity.class, new AABB(brain.blockPosition()).inflate(4), e -> !e.getItem().isEmpty()).stream()
				.noneMatch(e -> e.getItem().is(Items.CROSSBOW) || e.getItem().is(Items.LEATHER_CHESTPLATE)), "and drops none of their gear");
			Mercs.save();
			SellswordsMod.LOG.info("SELLSWORDS SMOKE TEST PASSED");
			server.halt(false);
		});
	}

	private static LivingEntity summon(ServerLevel level, String type, BlockPos pos, boolean noAi) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:" + type + " " + Cmd.pos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) + " {" + Cmd.uuidNbt(id)
			+ (noAi ? ",NoAI:1b" : "") + ",PersistenceRequired:1b}");
		Entity entity = level.getEntity(id);
		check(entity instanceof LivingEntity, "summoned a " + type);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
		return (LivingEntity) entity;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		SellswordsMod.LOG.info("[smoke] ok: {}", what);
	}
}
