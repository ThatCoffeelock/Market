package com.thatcoffeelock.tulpengekte;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Only runs with -Dtulpengekte.smokeTest=true (CI). Boots a real server and checks: bulbs and their prices, the
 * breeding odds (over a few hundred thousand offsets), planting, flowering, lifting, bone meal from a dispenser,
 * a bed washed away, the Bloemist (who can become one, what he wants, his daily books), and a save and load.
 */
final class SmokeTest {
	private static final BlockPos FLOWER = new BlockPos(0, 101, 0);
	private static final BlockPos EARLY = new BlockPos(2, 101, 0);
	private static final BlockPos MEALED = new BlockPos(4, 101, 0);
	private static final BlockPos WASHED = new BlockPos(6, 101, 0);
	private static final BlockPos KEPT = new BlockPos(8, 101, 0);
	private static final UUID BLOEMIST = UUID.fromString("16370203-0000-4000-8000-000000000001");
	private static final UUID JOBLESS = UUID.fromString("16370203-0000-4000-8000-000000000002");
	private static final UUID BABY = UUID.fromString("16370203-0000-4000-8000-000000000003");
	private static final UUID FARMER = UUID.fromString("16370203-0000-4000-8000-000000000004");

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -16 -16 16 16");
			TulpMod.later(100, () -> step(server, () -> start(level)));
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void start(ServerLevel level) {
		Cmd.run(level, "fill -12 99 -12 12 99 12 minecraft:stone");
		Cmd.run(level, "fill -12 100 -12 12 110 12 minecraft:air");
		Cmd.run(level, "fill -2 100 -2 10 100 2 minecraft:grass_block");
		items();
		prices();
		breeding();
		wants();
		beds(level);
	}

	// ---------------------------------------------------------------- items

	private static void items() {
		for (Variety v : Variety.values()) {
			ItemStack bulb = TulpItems.bulb(v, 3);
			check(TulpItems.variety(bulb) == v && bulb.getCount() == 3, v.title + " bulbs are recognised");
		}
		check(TulpItems.variety(new ItemStack(Items.PAPER)) == null, "plain paper isn't a bulb");
		check(TulpItems.bulb(Variety.GOUDA, 1).get(DataComponents.ITEM_MODEL) != null, "a bulb looks like its tulip, not paper");
		check(Boolean.TRUE.equals(TulpItems.bulb(Variety.VICEROY, 1).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
			&& TulpItems.bulb(Variety.GOUDA, 1).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, "broken bulbs glint, common ones don't");
		check(TulpItems.bulb(Variety.GOUDA, 1).getMaxStackSize() == 64, "bulbs stack to 64");
		check(ItemStack.isSameItemSameComponents(TulpItems.bulb(Variety.GOUDA, 1), TulpItems.bulb(Variety.GOUDA, 5)),
			"bulbs of one variety stack together");
		check(!ItemStack.isSameItemSameComponents(TulpItems.bulb(Variety.GOUDA, 1), TulpItems.bulb(Variety.ROSEN, 1)),
			"bulbs of different varieties don't");
		check(FabricLoader.getInstance().getObjectShare().get(TulpMod.ITEM_HOOK) instanceof BiFunction<?, ?, ?>, "the bulb hook for Colonycraft is published");
		check(TulpItems.variety(TulpItems.byKind("semper_augustus", 1)) == Variety.SEMPER_AUGUSTUS, "the hook makes a bulb by variety");
		Variety common = TulpItems.variety(TulpItems.byKind("common", 12));
		check(common != null && common.tier == Variety.Tier.COMMON && TulpItems.byKind("common", 12).getCount() == 12, "the hook makes a random common bulb");
		check(TulpItems.byKind("nonsense", 1).isEmpty() && TulpItems.byKind(null, 1).isEmpty(), "the hook makes nothing out of nonsense");
		check(Variety.common("pink") == Variety.ROSEN && Variety.common("red") == Variety.GOUDA, "each color has its common tulip");
		for (Variety.Tier tier : Variety.Tier.values()) {
			check(!Variety.of(tier).isEmpty(), "there are " + tier.title + " tulips");
		}
	}

	private static void prices() {
		check(Prices.price(new ItemStack(Items.PAPER)) == null, "plain paper isn't ours to price");
		long common = Prices.price(TulpItems.bulb(Variety.GOUDA, 1));
		long fine = Prices.price(TulpItems.bulb(Variety.GENERALISSIMO, 1));
		long rare = Prices.price(TulpItems.bulb(Variety.VICEROY, 1));
		long legend = Prices.price(TulpItems.bulb(Variety.SEMPER_AUGUSTUS, 1));
		check(common > 0 && fine > 5 * common && rare > 2 * fine && legend > 2 * rare,
			"rarer bulbs sell for much more (" + common + " / " + fine + " / " + rare + " / " + legend + " cents)");
		check(Bloemist.bulbPrice() > common * 2, "the Bloemist sells common bulbs for well over what they fetch (no loops)");
		check(Bloemist.price(Variety.VICEROY, BLOEMIST) > rare, "the Bloemist pays more than the Market for a want");
	}

	// ---------------------------------------------------------------- breeding

	private static void breeding() {
		TulpConfig c = TulpConfig.get();
		Random random = new Random(1637);
		int n = 200_000;
		int fine = 0;
		int rare = 0;
		int legend = 0;
		for (int i = 0; i < n; i++) {
			Variety child = Beds.child(Variety.GOUDA, c, random);
			switch (child.tier) {
				case FINE -> fine++;
				case RARE -> rare++;
				case LEGENDARY -> legend++;
				default -> check(child == Variety.GOUDA, "a common offset that doesn't break is the same variety");
			}
		}
		check(between(fine, n, 0.04, 0.06), "about 1 common offset in 20 breaks into a Fine tulip (" + fine + " of " + n + ")");
		check(between(rare, n, 0.007, 0.013), "about 1 in 100 into a Broken one (" + rare + ")");
		check(between(legend, n, 0.0005, 0.0016), "about 1 in 1,000 into the Semper Augustus (" + legend + ")");

		int same = 0;
		for (int i = 0; i < 20_000; i++) {
			Variety child = Beds.child(Variety.VICEROY, c, random);
			check(child == Variety.VICEROY || child == Variety.ROSEN || child == Variety.SEMPER_AUGUSTUS, "a Viceroy offset is a Viceroy, or reverts to Rosen");
			if (child == Variety.VICEROY) {
				same++;
			}
		}
		check(between(same, 20_000, 0.45, 0.55), "about half of a broken bulb's offsets revert (" + same + " kept of 20000)");
		for (int i = 0; i < 2_000; i++) {
			Variety child = Beds.child(Variety.SEMPER_AUGUSTUS, c, random);
			check(child == Variety.SEMPER_AUGUSTUS || child == Variety.GOUDA, "a Semper Augustus offset is one, or reverts to Gouda");
		}

		int commonOffsets = 0;
		int rareWithOffset = 0;
		int legendWithOffset = 0;
		for (int i = 0; i < 20_000; i++) {
			commonOffsets += Beds.offsets(Variety.GOUDA, false, c, random).size();
			rareWithOffset += Beds.offsets(Variety.VICEROY, false, c, random).isEmpty() ? 0 : 1;
			legendWithOffset += Beds.offsets(Variety.SEMPER_AUGUSTUS, false, c, random).isEmpty() ? 0 : 1;
		}
		check(between(commonOffsets, 20_000, 1.1, 1.3), "a common bulb gives about 1.2 offsets (" + commonOffsets + " from 20000)");
		check(between(rareWithOffset, 20_000, 0.22, 0.28), "a broken bulb gives an offset a quarter of the time (" + rareWithOffset + ")");
		check(between(legendWithOffset, 20_000, 0.08, 0.12), "the Semper Augustus one time in ten (" + legendWithOffset + ")");
		check(Beds.offsets(Variety.SEMPER_AUGUSTUS, true, c, new Random(1)).size() <= 1, "Green Thumb doesn't multiply legendary bulbs");
		int withThumb = 0;
		for (int i = 0; i < 2_000; i++) {
			withThumb += Beds.offsets(Variety.GOUDA, true, c, random).size();
		}
		check(between(withThumb, 2_000, 2.1, 2.3), "Green Thumb adds an offset to a common bulb (" + withThumb + " from 2000)");
	}

	private static boolean between(int count, int of, double low, double high) {
		double share = count / (double) of;
		return share >= low && share <= high;
	}

	private static void wants() {
		check(Bloemist.wants(42).equals(Bloemist.wants(42)), "every Bloemist wants the same things on the same day");
		Set<Variety> tops = new HashSet<>();
		boolean shaped = true;
		for (long day = 0; day < 200; day++) {
			List<Variety> w = Bloemist.wants(day);
			shaped &= w.size() == 3 && w.get(0).tier == Variety.Tier.COMMON && w.get(1).tier == Variety.Tier.FINE
				&& (w.get(2).tier == Variety.Tier.RARE || w.get(2).tier == Variety.Tier.LEGENDARY);
			tops.add(w.get(2));
		}
		check(shaped, "every day's wants are a common, a fine, and a broken or legendary tulip");
		check(tops.contains(Variety.SEMPER_AUGUSTUS) && tops.size() == 4, "over 200 days he asks for every broken tulip and the Semper Augustus");

		TulpStore.addSold(7, "someone", Variety.GOUDA, 30);
		check(TulpStore.sold(7, "someone", Variety.GOUDA) == 30 && TulpStore.sold(7, "someone", Variety.ROSEN) == 0, "the Bloemist keeps a book of today's sales");
		check(Bloemist.left(7, UUID.fromString("00000000-0000-0000-0000-000000000000"), Variety.GOUDA) == TulpConfig.get().bloemistDailyCommon,
			"another player's sales don't count against you");
		TulpStore.addSold(8, "someone", Variety.ROSEN, 1);
		check(TulpStore.sold(8, "someone", Variety.GOUDA) == 0 && TulpStore.sold(7, "someone", Variety.GOUDA) == 0, "a new day wipes the book");
	}

	// ---------------------------------------------------------------- beds

	private static void beds(ServerLevel level) {
		MinecraftServer server = level.getServer();
		int grow = TulpConfig.get().growTicks;

		check(Beds.plant(level, FLOWER, Variety.LACK_VAN_RIJN), "planted a Lack van Rijn");
		check(Beds.blockId(level.getBlockState(FLOWER)).equals("short_grass") && TulpStore.plant(level, FLOWER) != null, "a planted bulb is a remembered sprout");
		Beds.scan(server);
		check(Beds.blockId(level.getBlockState(FLOWER)).equals("short_grass"), "it doesn't flower right away");
		TulpStore.plant(level, FLOWER).planted -= grow;
		Beds.scan(server);
		check(Beds.blockId(level.getBlockState(FLOWER)).equals("red_tulip"), "a day later it flowers as a red tulip");
		check(FLOWER.equals(Beds.plantAt(level, FLOWER, level.getBlockState(FLOWER))), "the tulip is ours");
		Beds.Lift lift = Beds.lift(level, FLOWER, null, false);
		long kept = lift.loot().stream().filter(s -> TulpItems.variety(s) == Variety.LACK_VAN_RIJN).count();
		check(kept >= 1 && lift.loot().size() <= 2 && lift.loot().stream().allMatch(TulpItems::isBulb),
			"lifting a flowering Fine tulip gives its bulb back and at most one offset (" + lift.loot().size() + " bulbs)");
		check(level.getBlockState(FLOWER).isAir() && TulpStore.plant(level, FLOWER) == null, "lifting removes the tulip and forgets it");

		check(Beds.plant(level, EARLY, Variety.GOUDA), "planted a Gouda");
		Beds.Lift early = Beds.lift(level, EARLY, null, false);
		check(early.loot().size() == 1 && TulpItems.variety(early.loot().get(0)) == Variety.GOUDA, "lifting a sprout just gives the bulb back");

		check(Beds.plant(level, MEALED, Variety.ROSEN), "planted a Rosen");
		Cmd.setblock(level, MEALED.above(), "minecraft:tall_grass[half=upper]");
		Cmd.setblock(level, MEALED, "minecraft:tall_grass[half=lower]");
		check(MEALED.equals(Beds.plantAt(level, MEALED.above(), level.getBlockState(MEALED.above()))), "bone-mealed into tall grass, both halves are still ours");
		Beds.scan(server);
		check(Beds.blockId(level.getBlockState(MEALED)).equals("short_grass") && level.getBlockState(MEALED.above()).isAir()
			&& TulpStore.plant(level, MEALED) != null, "a dispenser's bone meal is undone: it's a sprout again");

		check(Beds.plant(level, WASHED, Variety.VICEROY), "planted a Viceroy");
		check(Beds.plant(level, KEPT, Variety.SEMPER_AUGUSTUS), "planted a Semper Augustus");
		TulpStore.plant(level, KEPT).planted -= grow;
		Beds.scan(server);
		check(Beds.blockId(level.getBlockState(KEPT)).equals("red_tulip"), "the Semper Augustus flowers red");
		Cmd.setblock(level, WASHED, "minecraft:air");
		Beds.scan(server);
		check(TulpStore.plant(level, WASHED) == null && itemsAt(level, WASHED, s -> TulpItems.variety(s) == Variety.VICEROY) == 1,
			"a bed that's knocked over leaves its bulb behind (and no offsets)");
		Cmd.run(level, "kill @e[type=minecraft:item]");

		bloemist(level);
	}

	private static int itemsAt(ServerLevel level, BlockPos pos, Predicate<ItemStack> what) {
		AABB box = new AABB(pos.getX() - 1, pos.getY() - 2, pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
		return level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && what.test(e.getItem())).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	// ---------------------------------------------------------------- the Bloemist

	private static void bloemist(ServerLevel level) {
		MinecraftServer server = level.getServer();
		summon(level, BLOEMIST, 0, "CustomName:\"Bloemist Joost\"");
		summon(level, JOBLESS, 3, "");
		summon(level, BABY, 6, "Age:-24000");
		summon(level, FARMER, 9, "VillagerData:{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"}");
		TulpMod.later(5, () -> step(server, () -> {
			Entity joost = level.getEntity(BLOEMIST);
			Entity jobless = level.getEntity(JOBLESS);
			Entity baby = level.getEntity(BABY);
			Entity farmer = level.getEntity(FARMER);
			check(joost != null && jobless != null && baby != null && farmer != null, "the villagers are here");
			check(Bloemist.is(joost), "a villager named Bloemist is one");
			check(!Bloemist.is(jobless), "a plain villager isn't");
			check(Bloemist.whyNot(jobless) == null, "a jobless villager can become a Bloemist");
			check(Bloemist.whyNot(baby) != null, "a baby can't");
			check(Bloemist.whyNot(farmer) != null, "a farmer already has a job");
			Bloemist.hire(jobless);
			check(Bloemist.is(jobless) && jobless.getCustomName().getString().startsWith("Bloemist "), "a hired villager is a Bloemist, with a name");
			Cmd.run(level, "kill @e[type=minecraft:villager]");
			persistence(level);
		}));
	}

	private static void summon(ServerLevel level, UUID id, int x, String extra) {
		Cmd.run(level, "summon minecraft:villager " + Cmd.pos(x + 0.5, 101, -6.5) + " {" + Cmd.uuidNbt(id) + ",NoAI:1b,PersistenceRequired:1b"
			+ (extra.isEmpty() ? "" : "," + extra) + "}");
	}

	// ---------------------------------------------------------------- save and load

	private static void persistence(ServerLevel level) {
		MinecraftServer server = level.getServer();
		TulpStore.addSold(Bloemist.day(server), BLOEMIST.toString(), Variety.BRABANSON, 2);
		TulpStore.save();
		TulpStore.load(server);
		TulpStore.Plant kept = TulpStore.plant(level, KEPT);
		check(kept != null && kept.variety() == Variety.SEMPER_AUGUSTUS, "the tulip bed survives a save and load");
		check(TulpStore.plant(level, MEALED) != null && TulpStore.plant(level, MEALED).variety() == Variety.ROSEN, "and so does the sprout");
		check(TulpStore.sold(Bloemist.day(server), BLOEMIST.toString(), Variety.BRABANSON) == 2, "the Bloemist's book survives too");
		check(Bloemist.left(Bloemist.day(server), BLOEMIST, Variety.BRABANSON) == TulpConfig.get().bloemistDailyRare - 2, "and counts against his daily wants");

		Beds.Lift lift = Beds.lift(level, KEPT, null, false);
		check(lift.loot().stream().anyMatch(s -> TulpItems.variety(s) == Variety.SEMPER_AUGUSTUS), "lifting the Semper Augustus gives it back");

		TulpMod.later(20, () -> step(server, () -> {
			TulpMod.LOG.info("TULPENGEKTE SMOKE TEST PASSED");
			server.halt(false);
		}));
	}

	private static void fail(MinecraftServer server, Throwable t) {
		TulpMod.LOG.error("TULPENGEKTE SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		TulpMod.LOG.info("[smoke] ok: {}", what);
	}
}
