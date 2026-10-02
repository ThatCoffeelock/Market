package com.thatcoffeelock.havana;

import java.util.List;
import java.util.function.BooleanSupplier;

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
 * Only runs with -Dhavana.smokeTest=true (CI). Boots a real server and takes tobacco all the way: plants it on
 * farmland, lets it ripen and shoot up two blocks, harvests it, tramples a young plant (it must drop a tobacco
 * seed, not a potato), cures leaves in a Curing Barrel (with topping up and a long absence), rolls cigars with and
 * without flavors, lights one, and checks the field and barrel survive a save and load.
 */
final class SmokeTest {
	private static final BlockPos RIPE = new BlockPos(0, 101, 0);
	private static final BlockPos YOUNG = new BlockPos(2, 101, 0);
	private static final BlockPos TRAMPLED = new BlockPos(4, 101, 0);
	private static final BlockPos WASHED = new BlockPos(6, 101, 0);
	private static final BlockPos KEPT = new BlockPos(8, 101, 0);
	private static final BlockPos BARREL = new BlockPos(0, 101, 5);

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -16 -16 16 16");
			HavanaMod.later(100, () -> step(server, () -> start(level)));
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

	/** Checks every 5 ticks until the condition holds, then carries on. Fails after maxTicks. */
	private static void waitFor(MinecraftServer server, String what, int maxTicks, BooleanSupplier condition, Step then) {
		poll(server, what, maxTicks, 0, condition, then);
	}

	private static void poll(MinecraftServer server, String what, int maxTicks, int waited, BooleanSupplier condition, Step then) {
		HavanaMod.later(5, () -> step(server, () -> {
			if (condition.getAsBoolean()) {
				HavanaMod.LOG.info("[smoke] ok: {} (after {} ticks)", what, waited + 5);
				then.run();
			} else if (waited + 5 >= maxTicks) {
				throw new IllegalStateException("Smoke check failed: timed out waiting for " + what);
			} else {
				poll(server, what, maxTicks, waited + 5, condition, then);
			}
		}));
	}

	private static void start(ServerLevel level) {
		Cmd.run(level, "fill -12 99 -12 12 99 12 minecraft:stone");
		Cmd.run(level, "fill -12 100 -12 12 110 12 minecraft:air");
		Cmd.run(level, "fill -2 100 -2 10 100 2 minecraft:farmland[moisture=7]");
		Cmd.run(level, "fill -2 99 3 10 99 3 minecraft:water");
		items();
		rolling();
		cigars();
		crops(level);
	}

	// ---------------------------------------------------------------- items

	private static void items() {
		check(HavanaItems.isSeeds(HavanaItems.seeds(1)), "tobacco seeds are recognised");
		check(!HavanaItems.isSeeds(new ItemStack(Items.BEETROOT_SEEDS)), "plain beetroot seeds aren't tobacco seeds");
		check(HavanaItems.isTobacco(HavanaItems.leaf(1)) && HavanaItems.isTobacco(HavanaItems.cured(1)) && HavanaItems.isTobacco(HavanaItems.aged(1)),
			"leaves, cured and aged tobacco are tobacco");
		check(!HavanaItems.isTobacco(new ItemStack(Items.PAPER)), "plain paper isn't tobacco");
		check(HavanaItems.leaf(1).get(DataComponents.ITEM_MODEL) != null, "leaves look like something other than paper");
		ItemStack cigar = HavanaItems.cigar(HavanaItems.Grade.CURED, HavanaItems.Flavor.NONE, 1);
		check(HavanaItems.isCigar(cigar) && !HavanaItems.isLit(cigar), "a fresh cigar is a cigar and isn't lit");
		check(!HavanaItems.isCigar(new ItemStack(Items.STICK)), "a plain stick isn't a cigar");
		check(cigar.getMaxStackSize() == HavanaItems.CIGAR_STACK, "unlit cigars stack to " + HavanaItems.CIGAR_STACK);
		check(HavanaItems.cigarName(HavanaItems.Grade.AGED, HavanaItems.Flavor.HONEY).equals("Gran Reserva Honey Cigar"), "cigar names");
		check(HavanaItems.Flavor.of(new ItemStack(Items.COCOA_BEANS)) == HavanaItems.Flavor.CHOCOLATE, "cocoa beans are the chocolate flavor");
		check(HavanaItems.Flavor.of(new ItemStack(Items.DIRT)) == null, "dirt isn't a flavor (sorry)");
		check(HavanaItems.Flavor.of(HavanaItems.cured(1)) == null, "tobacco isn't a flavor");
		check(Curing.isCuringName("Curing Barrel") && Curing.isCuringName("my HUMIDOR!") && !Curing.isCuringName("Barrel of Fun"), "curing barrel names");
	}

	// ---------------------------------------------------------------- rolling

	private static void rolling() {
		Rolling.Plan plain = Rolling.plan(HavanaItems.cured(7), ItemStack.EMPTY, 64);
		check(plain.ok() && plain.cigars().getCount() == 2 && plain.tobaccoUsed() == 6, "7 cured tobacco roll 2 cigars and leave 1");
		check(HavanaItems.grade(plain.cigars()) == HavanaItems.Grade.CURED && HavanaItems.flavor(plain.cigars()) == HavanaItems.Flavor.NONE,
			"they're plain cigars");
		check(Rolling.plan(HavanaItems.cured(7), ItemStack.EMPTY, 1).cigars().getCount() == 1, "a click rolls just one");

		Rolling.Plan honey = Rolling.plan(HavanaItems.aged(9), new ItemStack(Items.HONEY_BOTTLE, 2), 64);
		check(honey.ok() && honey.cigars().getCount() == 2 && honey.flavorUsed() == 2 && honey.tobaccoUsed() == 6,
			"the flavor limits the batch: 2 honey bottles, 2 cigars");
		check(HavanaItems.grade(honey.cigars()) == HavanaItems.Grade.AGED && HavanaItems.flavor(honey.cigars()) == HavanaItems.Flavor.HONEY,
			"aged tobacco + honey = Gran Reserva Honey");
		check(honey.remainder().is(Items.GLASS_BOTTLE) && honey.remainder().getCount() == 2, "you get the glass bottles back");

		check(!Rolling.plan(HavanaItems.leaf(9), ItemStack.EMPTY, 64).ok(), "green leaves can't be rolled");
		check(!Rolling.plan(HavanaItems.cured(2), ItemStack.EMPTY, 64).ok(), "2 tobacco isn't enough for a cigar");
		check(!Rolling.plan(new ItemStack(Items.PAPER, 9), ItemStack.EMPTY, 64).ok(), "paper isn't tobacco");
		check(!Rolling.plan(HavanaItems.cured(3), new ItemStack(Items.DIRT), 64).ok(), "dirt-flavored cigars are refused");
	}

	// ---------------------------------------------------------------- smoking

	private static void cigars() {
		ItemStack stack = HavanaItems.cigar(HavanaItems.Grade.AGED, HavanaItems.Flavor.GLOW, 5);
		ItemStack lit = HavanaItems.lit(stack);
		check(HavanaItems.isLit(lit) && lit.getCount() == 1 && lit.getMaxStackSize() == 1, "lighting gives one lit cigar");
		check(lit.getMaxDamage() == HavanaItems.PUFFS && lit.getDamageValue() == 0, "a lit cigar has " + HavanaItems.PUFFS + " puffs");
		check(HavanaItems.grade(lit) == HavanaItems.Grade.AGED && HavanaItems.flavor(lit) == HavanaItems.Flavor.GLOW, "lighting keeps grade and flavor");
		lit.setDamageValue(3);
		ItemStack out = HavanaItems.extinguished(lit);
		check(!HavanaItems.isLit(out) && HavanaItems.isCigar(out) && out.getDamageValue() == 3, "a cigar that goes out keeps its puffs");
		check(HavanaItems.lit(out).getDamageValue() == 3, "relighting it carries on where it was");

		List<Smoking.Effect> effects = Smoking.effects(lit);
		check(effects.stream().anyMatch(e -> e.id().equals("regeneration")) && effects.stream().anyMatch(e -> e.id().equals("resistance"))
			&& effects.stream().anyMatch(e -> e.id().equals("night_vision")), "a Gran Reserva Glow puff: regeneration, resistance, night vision");
		check(Smoking.effects(HavanaItems.cigar(HavanaItems.Grade.CURED, HavanaItems.Flavor.NONE, 1)).size() == 1, "a plain puff: just regeneration");
	}

	// ---------------------------------------------------------------- crops

	private static void crops(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(Crops.plant(level, RIPE), "planted tobacco");
		check(Crops.blockId(level.getBlockState(RIPE)).equals("potatoes") && HavanaStore.hasCrop(level, RIPE), "a young plant is a remembered potato crop");
		check(!Crops.isRipe(level.getBlockState(RIPE)), "it isn't ripe yet");
		Cmd.setblock(level, RIPE, "minecraft:potatoes[age=7]");
		check(Crops.isRipe(level.getBlockState(RIPE)), "fully grown it's ripe");
		Crops.scan(server);
		check(Crops.blockId(level.getBlockState(RIPE)).equals("large_fern") && Crops.blockId(level.getBlockState(RIPE.above())).equals("large_fern"),
			"a ripe plant shoots up into a two-block large fern");
		check(RIPE.equals(Crops.plantAt(level, RIPE.above(), level.getBlockState(RIPE.above()))), "the top half belongs to the plant below");
		Crops.scan(server);
		check(HavanaStore.hasCrop(level, RIPE), "the grown plant is still remembered");

		List<ItemStack> loot = Crops.harvest(level, RIPE, false);
		int leaves = loot.stream().filter(s -> HavanaItems.kind(s).equals(HavanaItems.LEAF)).mapToInt(ItemStack::getCount).sum();
		int seeds = loot.stream().filter(HavanaItems::isSeeds).mapToInt(ItemStack::getCount).sum();
		check(leaves >= 3 && leaves <= 5 && seeds >= 1 && seeds <= 2, "a ripe plant gives 3-5 leaves and 1-2 seeds (" + leaves + ", " + seeds + ")");
		check(level.getBlockState(RIPE).isAir() && level.getBlockState(RIPE.above()).isAir() && !HavanaStore.hasCrop(level, RIPE),
			"harvesting removes both halves and forgets the plant");

		check(Crops.plant(level, YOUNG), "planted a second one");
		List<ItemStack> early = Crops.harvest(level, YOUNG, false);
		check(early.size() == 1 && HavanaItems.isSeeds(early.get(0)) && early.get(0).getCount() == 1, "an unripe plant just gives its seed back");

		check(Crops.plant(level, TRAMPLED), "planted a third one");
		check(Crops.plant(level, WASHED), "planted a fourth one");
		check(Crops.plant(level, KEPT), "planted a fifth one");
		level.destroyBlock(TRAMPLED.below(), false); // the farmland goes, the plant pops off and drops its vanilla loot
		Cmd.setblock(level, WASHED, "minecraft:air");
		Cmd.run(level, "summon minecraft:item " + Cmd.pos(WASHED.getX() + 0.5, WASHED.getY() + 0.2, WASHED.getZ() + 0.5)
			+ " {Item:{id:\"minecraft:potato\",count:1}}");
		HavanaMod.later(5, () -> step(server, () -> {
			check(itemsAt(level, WASHED, s -> s.is(Items.POTATO)) == 0 && itemsAt(level, WASHED, HavanaItems::isSeeds) == 1,
				"a potato dropped where a tobacco plant was turns into a tobacco seed");
			check(itemsAt(level, TRAMPLED, s -> s.is(Items.POTATO) || s.is(Items.POISONOUS_POTATO)) == 0,
				"a trampled tobacco plant doesn't drop potatoes");
			check(itemsAt(level, TRAMPLED, HavanaItems::isSeeds) >= 1, "a trampled tobacco plant drops a tobacco seed");
			Crops.scan(server);
			check(!HavanaStore.hasCrop(level, TRAMPLED) && !HavanaStore.hasCrop(level, WASHED), "plants that are gone are forgotten");
			check(HavanaStore.hasCrop(level, KEPT), "the one still standing is remembered");
			Cmd.run(level, "kill @e[type=minecraft:item]");
			curing(level);
		}));
	}

	private static int itemsAt(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> what) {
		AABB box = new AABB(pos.getX() - 1, pos.getY() - 2, pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
		return level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && what.test(e.getItem())).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	// ---------------------------------------------------------------- curing

	private static void curing(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Cmd.setblock(level, BARREL, "minecraft:barrel");
		check(Curing.at(level, BARREL) == null, "a plain barrel isn't a curing barrel");
		Cmd.run(level, "data merge block " + Cmd.block(BARREL) + " {CustomName:{text:\"Curing Barrel\",color:\"gold\",italic:false}}");
		Container box = Curing.at(level, BARREL);
		check(box != null, "a barrel named Curing Barrel is one");
		HavanaStore.Barrel barrel = HavanaStore.addBarrel(level, BARREL);

		box.setItem(0, HavanaItems.leaf(10));
		box.setItem(1, HavanaItems.cured(4));
		box.setItem(2, new ItemStack(Items.COBBLESTONE, 5));
		check(Curing.age(box, barrel, 1000) == 0 && barrel.slots.size() == 2, "two batches start (and the cobblestone is ignored)");
		check(Curing.age(box, barrel, Curing.CURE_TICKS / 2) == 0, "half a day: nothing's done yet");
		box.setItem(0, HavanaItems.leaf(20));
		Curing.age(box, barrel, Curing.CURE_TICKS / 4);
		check(HavanaItems.kind(box.getItem(0)).equals(HavanaItems.LEAF),
			"doubling the leaves halves their progress: not done after three quarters of a day (" + barrel.slots.get("0").progress + ")");
		Curing.age(box, barrel, Curing.CURE_TICKS / 2);
		check(HavanaItems.kind(box.getItem(0)).equals(HavanaItems.CURED) && box.getItem(0).getCount() == 20, "20 leaves cured");
		check(HavanaItems.kind(box.getItem(1)).equals(HavanaItems.CURED), "the cured tobacco is still aging");
		Curing.age(box, barrel, Curing.AGE_TICKS);
		check(HavanaItems.kind(box.getItem(1)).equals(HavanaItems.AGED) && box.getItem(1).getCount() == 4, "4 cured tobacco aged");
		check(box.getItem(2).is(Items.COBBLESTONE) && box.getItem(2).getCount() == 5, "the cobblestone is untouched");

		box.setItem(3, HavanaItems.leaf(6));
		Curing.age(box, barrel, 1);
		Curing.age(box, barrel, Curing.CURE_TICKS + Curing.AGE_TICKS);
		check(HavanaItems.kind(box.getItem(3)).equals(HavanaItems.AGED), "after a long absence fresh leaves come back aged");

		box.setItem(4, HavanaItems.leaf(2));
		Curing.update(level, BARREL, box, barrel);
		check(barrel.slots.containsKey("4"), "the barrel picks up new leaves when it's updated");

		HavanaStore.save();
		HavanaStore.load(server);
		check(HavanaStore.hasCrop(level, KEPT), "the tobacco field survives a save and load");
		HavanaStore.Barrel loaded = HavanaStore.barrel(level, BARREL);
		check(loaded != null && loaded.slots.containsKey("4") && HavanaItems.LEAF.equals(loaded.slots.get("4").kind),
			"the curing barrel survives a save and load");
		Cmd.setblock(level, BARREL, "minecraft:air");
		Curing.scan(server);
		check(HavanaStore.barrel(level, BARREL) == null, "a broken barrel is forgotten");

		waitFor(server, "the server to keep ticking with Havana loaded", 40, () -> true, () -> {
			HavanaMod.LOG.info("HAVANA SMOKE TEST PASSED");
			server.halt(false);
		});
	}

	private static void fail(MinecraftServer server, Throwable t) {
		HavanaMod.LOG.error("HAVANA SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		HavanaMod.LOG.info("[smoke] ok: {}", what);
	}
}
