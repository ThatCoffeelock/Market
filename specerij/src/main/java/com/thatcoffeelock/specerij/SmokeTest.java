package com.thatcoffeelock.specerij;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Only runs with -Dspecerij.smokeTest=true (CI). Boots a real server, paints a strip of jungle next to a strip of
 * plains, and checks: the items and prices, which biome grows what, a Hortus (glass roof and a fire) and one that's
 * gone cold, growing, picking and regrowing, a dispenser's bone meal, digging up, a bed knocked over, the Drying Rack
 * (black tea included), and a save and load.
 */
final class SmokeTest {
	/** In the jungle strip (x -16..-1). */
	private static final BlockPos PEPPER = new BlockPos(-4, 101, 0);
	/** In the plains strip (x 0..15). */
	private static final BlockPos TEA = new BlockPos(4, 101, 0);
	private static final BlockPos COLD = new BlockPos(12, 101, 0);
	private static final BlockPos FIRE = new BlockPos(6, 101, 0);
	private static final BlockPos RACK = new BlockPos(0, 101, 6);

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -32 -16 32 16");
			SpiceMod.later(100, () -> step(server, () -> start(level)));
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
		Cmd.run(level, "fill -20 99 -12 20 99 12 minecraft:stone");
		Cmd.run(level, "fill -20 100 -12 20 115 12 minecraft:air");
		Cmd.run(level, "fill -16 100 -2 15 100 2 minecraft:grass_block");
		Cmd.run(level, "fillbiome -16 96 -16 -1 120 15 minecraft:jungle");
		Cmd.run(level, "fillbiome 0 96 -16 15 120 15 minecraft:plains");
		items();
		prices();
		plantation();
		climate(level);
		gardens(level);
		drying(level);
		persistence(level);
	}

	// ---------------------------------------------------------------- items

	@SuppressWarnings("unchecked")
	private static void items() {
		for (Spice s : Spice.values()) {
			check(SpiceItems.cuttingOf(SpiceItems.cutting(s, 2)) == s, s.title + " cuttings are recognised");
			check(SpiceItems.spice(SpiceItems.fresh(s, 1)) == s && SpiceItems.kind(SpiceItems.fresh(s, 1)).equals(SpiceItems.FRESH), "fresh " + s.title);
			check(SpiceItems.spice(SpiceItems.dried(s, 1)) == s && SpiceItems.kind(SpiceItems.dried(s, 1)).equals(SpiceItems.DRIED), "dried " + s.title);
			check(SpiceItems.dried(s, 1).get(DataComponents.ITEM_MODEL) != null, s.title + " looks like something other than paper");
			check(!s.homes.isEmpty() && Spice.forBiome("minecraft:" + s.homes.iterator().next()) == s, s.title + " grows wild in " + s.homeNames());
		}
		check(SpiceItems.spice(new ItemStack(Items.PAPER)) == null, "plain paper isn't a spice");
		check(SpiceItems.kind(SpiceItems.blackTea(1)).equals(SpiceItems.AGED), "black tea is aged tea");
		check(Spice.forBiome("minecraft:plains") == null, "nothing grows wild in the plains");
		Object item = FabricLoader.getInstance().getObjectShare().get(SpiceMod.ITEM_HOOK);
		Object biome = FabricLoader.getInstance().getObjectShare().get(SpiceMod.BIOME_HOOK);
		check(item instanceof BiFunction<?, ?, ?> && biome instanceof Function<?, ?>, "the hooks for Colonycraft are published");
		ItemStack pepper = ((BiFunction<String, Integer, ItemStack>) item).apply("dried:pepper", 12);
		check(SpiceItems.spice(pepper) == Spice.PEPPER && pepper.getCount() == 12, "the item hook makes real spice");
		check(((BiFunction<String, Integer, ItemStack>) item).apply("nonsense", 1).isEmpty(), "and nothing out of nonsense");
		check("nutmeg".equals(((Function<String, String>) biome).apply("minecraft:mangrove_swamp"))
			&& ((Function<String, String>) biome).apply("minecraft:desert") == null, "the biome hook knows where spices grow");
	}

	@SuppressWarnings("unchecked")
	private static void plantation() {
		java.util.Random random = new java.util.Random(1602);
		for (Spice s : Spice.values()) {
			double near = 0;
			double far = 0;
			for (int i = 0; i < 200; i++) {
				near += value(Plantation.day(s, 1, 2, 1, random));
				far += value(Plantation.day(s, 1, 2, 2, random));
			}
			check(near / 200 > 600 && near / 200 < 1100, s.title + ": two planters at home bring in about ₥8 a day (" + Math.round(near / 200) + " cents)");
			check(far > near * 1.7, s.title + ": twice that far from home");
		}
		List<ItemStack> tier3 = Plantation.day(Spice.TEA, 3, 4, 1, random);
		check(tier3.stream().anyMatch(i -> SpiceItems.kind(i).equals(SpiceItems.AGED)) && tier3.stream().noneMatch(i -> SpiceItems.kind(i).equals(SpiceItems.FRESH)),
			"a tier 3 tea plantation dries everything and ages some of it black");
		Object hook = FabricLoader.getInstance().getObjectShare().get(SpiceMod.PLANTATION_HOOK);
		check(hook instanceof Function<?, ?>, "the plantation hook is published");
		List<ItemStack> day = ((Function<java.util.Map<String, Object>, List<ItemStack>>) hook)
			.apply(java.util.Map.of("spice", "cloves", "tier", 2, "workers", 3, "distance", 1.5));
		check(!day.isEmpty() && day.stream().allMatch(i -> SpiceItems.spice(i) == Spice.CLOVES), "and it grows the spice it's asked for");
	}

	private static long value(List<ItemStack> stacks) {
		long cents = 0;
		for (ItemStack stack : stacks) {
			if (!SpiceItems.kind(stack).equals(SpiceItems.CUTTING)) {
				cents += Prices.price(stack) * stack.getCount();
			}
		}
		return cents;
	}

	private static void prices() {
		check(Prices.price(new ItemStack(Items.PAPER)) == null, "plain paper isn't ours to price");
		for (Spice s : Spice.values()) {
			long fresh = Prices.price(SpiceItems.fresh(s, 1));
			long dried = Prices.price(SpiceItems.dried(s, 1));
			check(fresh > 0 && dried >= 3 * fresh, s.title + ": dried sells for well over fresh (" + fresh + " / " + dried + " cents)");
		}
		check(Prices.price(SpiceItems.blackTea(1)) > Prices.price(SpiceItems.dried(Spice.TEA, 1)), "black tea beats green");
		check(Prices.price(SpiceItems.cutting(Spice.SAFFRON, 1)) > 0, "cuttings sell too");
	}

	// ---------------------------------------------------------------- climate

	private static void climate(ServerLevel level) {
		check(Hortus.biome(level, PEPPER).endsWith("jungle"), "the jungle strip is jungle (" + Hortus.biome(level, PEPPER) + ")");
		check(Hortus.biome(level, TEA).endsWith("plains"), "the plains strip is plains (" + Hortus.biome(level, TEA) + ")");
		check(Hortus.rate(level, PEPPER, Spice.PEPPER) == 1, "pepper grows at full speed in the jungle");
		check(Hortus.rate(level, PEPPER, Spice.TEA) == 0, "tea doesn't grow in the jungle");
		check(Hortus.rate(level, TEA, Spice.TEA) == 0, "or in the plains, out in the open");
		Cmd.setblock(level, TEA.above(4), "minecraft:glass");
		check(Hortus.underGlass(level, TEA) && !Hortus.isHortus(level, TEA), "a glass roof alone isn't a Hortus");
		Cmd.setblock(level, FIRE, "minecraft:campfire[lit=true]");
		check(Hortus.isHortus(level, TEA) && Hortus.rate(level, TEA, Spice.TEA) == 0.5, "glass and a fire: a Hortus, at half speed");
		Cmd.setblock(level, FIRE, "minecraft:campfire[lit=false]");
		check(!Hortus.isHortus(level, TEA), "put the fire out and it's just a shed");
		Cmd.setblock(level, FIRE, "minecraft:campfire[lit=true]");
	}

	// ---------------------------------------------------------------- gardens

	private static void gardens(ServerLevel level) {
		int grow = SpiceConfig.get().growTicks;
		check(Gardens.plant(level, PEPPER, Spice.PEPPER), "planted pepper in the jungle");
		SpiceStore.Plant pepper = SpiceStore.plant(level, PEPPER);
		check(pepper != null && Gardens.blockId(level.getBlockState(PEPPER)).equals("short_grass"), "a planted cutting is a remembered sprout");
		pepper.last -= grow / 2;
		Gardens.update(level, PEPPER, pepper);
		check(Gardens.blockId(level.getBlockState(PEPPER)).equals("short_grass"), "half a day in, it isn't ripe");
		pepper.last -= grow / 2;
		Gardens.update(level, PEPPER, pepper);
		check(Gardens.blockId(level.getBlockState(PEPPER)).equals("sweet_berry_bush"), "a day in, it's a ripe pepper bush");
		List<ItemStack> crop = Gardens.pick(level, PEPPER, pepper, null);
		int peppercorns = crop.stream().filter(s -> SpiceItems.spice(s) == Spice.PEPPER).mapToInt(ItemStack::getCount).sum();
		check(peppercorns >= 3 && peppercorns <= 5 && SpiceItems.kind(crop.get(0)).equals(SpiceItems.FRESH), "picking gives 3-5 fresh peppercorns (" + peppercorns + ")");
		check(Gardens.blockId(level.getBlockState(PEPPER)).equals("short_grass") && pepper.progress == 0 && SpiceStore.plant(level, PEPPER) != null,
			"and the plant grows back from a sprout");

		Cmd.setblock(level, PEPPER.above(), "minecraft:tall_grass[half=upper]");
		Cmd.setblock(level, PEPPER, "minecraft:tall_grass[half=lower]");
		check(PEPPER.equals(Gardens.plantAt(level, PEPPER.above(), level.getBlockState(PEPPER.above()))), "bone-mealed into tall grass, it's still ours");
		Gardens.update(level, PEPPER, pepper);
		check(Gardens.blockId(level.getBlockState(PEPPER)).equals("short_grass") && level.getBlockState(PEPPER.above()).isAir(),
			"a dispenser's bone meal is undone");

		check(Gardens.plant(level, TEA, Spice.TEA), "planted tea in the Hortus");
		SpiceStore.Plant tea = SpiceStore.plant(level, TEA);
		tea.last -= grow;
		Gardens.update(level, TEA, tea);
		check(Gardens.blockId(level.getBlockState(TEA)).equals("short_grass") && Math.abs(tea.progress - grow / 2.0) < 1,
			"a day in the Hortus is half a day's growing (" + tea.progress + ")");
		tea.last -= grow;
		Gardens.update(level, TEA, tea);
		check(Gardens.blockId(level.getBlockState(TEA)).equals("fern"), "two days in the Hortus and the tea is ripe");
		List<ItemStack> dug = Gardens.dig(level, TEA, tea, null);
		check(dug.stream().anyMatch(s -> SpiceItems.cuttingOf(s) == Spice.TEA) && dug.stream().anyMatch(s -> SpiceItems.kind(s).equals(SpiceItems.FRESH)),
			"digging up a ripe plant gives the harvest and the cutting");
		check(level.getBlockState(TEA).isAir() && SpiceStore.plant(level, TEA) == null, "and it's gone");

		check(Gardens.plant(level, COLD, Spice.CINNAMON), "planted cinnamon in the cold plains");
		SpiceStore.Plant cold = SpiceStore.plant(level, COLD);
		cold.last -= 3L * grow;
		Gardens.update(level, COLD, cold);
		check(cold.progress == 0 && Gardens.blockId(level.getBlockState(COLD)).equals("short_grass"), "out of its climate it doesn't grow at all");
		Cmd.setblock(level, COLD, "minecraft:air");
		Gardens.update(level, COLD, cold);
		check(SpiceStore.plant(level, COLD) == null && itemsAt(level, COLD, s -> SpiceItems.cuttingOf(s) == Spice.CINNAMON) == 1,
			"a plant that's knocked over leaves its cutting");
		Cmd.run(level, "kill @e[type=minecraft:item]");
	}

	private static int itemsAt(ServerLevel level, BlockPos pos, Predicate<ItemStack> what) {
		AABB box = new AABB(pos.getX() - 1, pos.getY() - 2, pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
		return level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && what.test(e.getItem())).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	// ---------------------------------------------------------------- drying

	private static void drying(ServerLevel level) {
		SpiceConfig c = SpiceConfig.get();
		Cmd.setblock(level, RACK, "minecraft:barrel");
		check(Drying.at(level, RACK) == null, "a plain barrel isn't a drying rack");
		Cmd.run(level, "data merge block " + Cmd.block(RACK) + " {CustomName:{text:\"Drying Rack\",color:\"gold\",italic:false}}");
		Container box = Drying.at(level, RACK);
		check(box != null, "a barrel named Drying Rack is one");
		check(Drying.isRackName("my SPICE RACK") && !Drying.isRackName("Barrel of Fun"), "drying rack names");
		SpiceStore.Rack rack = SpiceStore.addRack(level, RACK);
		box.setItem(0, SpiceItems.fresh(Spice.PEPPER, 10));
		box.setItem(1, SpiceItems.dried(Spice.TEA, 4));
		box.setItem(2, SpiceItems.dried(Spice.SAFFRON, 3));
		box.setItem(3, new ItemStack(Items.COBBLESTONE, 5));
		check(Drying.age(box, rack, 1000) == 0 && rack.slots.size() == 2, "two batches start (dried saffron and cobblestone don't dry)");
		Drying.age(box, rack, c.dryTicks);
		check(SpiceItems.kind(box.getItem(0)).equals(SpiceItems.DRIED) && SpiceItems.spice(box.getItem(0)) == Spice.PEPPER && box.getItem(0).getCount() == 10,
			"a day later: 10 Black Pepper");
		check(SpiceItems.kind(box.getItem(1)).equals(SpiceItems.DRIED), "the green tea is still turning");
		Drying.age(box, rack, c.blackTeaTicks);
		check(SpiceItems.kind(box.getItem(1)).equals(SpiceItems.AGED) && box.getItem(1).getCount() == 4, "two more days: 4 Black Tea");
		check(SpiceItems.kind(box.getItem(2)).equals(SpiceItems.DRIED) && box.getItem(3).is(Items.COBBLESTONE), "the saffron and the cobblestone are untouched");
		box.setItem(4, SpiceItems.fresh(Spice.TEA, 6));
		Drying.age(box, rack, 1);
		Drying.age(box, rack, (long) c.dryTicks + c.blackTeaTicks);
		check(SpiceItems.kind(box.getItem(4)).equals(SpiceItems.AGED), "after a long absence fresh tea comes back black");
		box.setItem(5, SpiceItems.fresh(Spice.CLOVES, 2));
		Drying.update(level, RACK, box, rack);
		check(rack.slots.containsKey("5"), "the rack picks up new spice when it's updated");
	}

	// ---------------------------------------------------------------- save and load

	private static void persistence(ServerLevel level) {
		MinecraftServer server = level.getServer();
		SpiceStore.save();
		SpiceStore.load(server);
		SpiceStore.Plant pepper = SpiceStore.plant(level, PEPPER);
		check(pepper != null && pepper.spice() == Spice.PEPPER, "the pepper garden survives a save and load");
		SpiceStore.Rack rack = SpiceStore.rack(level, RACK);
		check(rack != null && rack.slots.containsKey("5"), "the drying rack survives too");
		Cmd.setblock(level, RACK, "minecraft:air");
		Drying.scan(server);
		check(SpiceStore.rack(level, RACK) == null, "a broken rack is forgotten");

		SpiceMod.later(20, () -> step(server, () -> {
			SpiceMod.LOG.info("SPECERIJ SMOKE TEST PASSED");
			server.halt(false);
		}));
	}

	private static void fail(MinecraftServer server, Throwable t) {
		SpiceMod.LOG.error("SPECERIJ SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		SpiceMod.LOG.info("[smoke] ok: {}", what);
	}
}
