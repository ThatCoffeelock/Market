package com.thatcoffeelock.market;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only runs with -Dmarket.smokeTest=true (CI). Boots a real server, checks the price list, item
 * helpers and vanity builders against the live game, then shuts the server down.
 */
final class SmokeTest {
	private SmokeTest() {
	}

	/** Another mod (Fossil Fool, say) prices its own custom items through the shared hook list. */
	@SuppressWarnings("unchecked")
	private static void priceHooks() {
		Object hooks = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get(PriceHooks.KEY);
		check(hooks instanceof java.util.List<?>, "price hook list published");
		java.util.function.Function<ItemStack, Long> hook = stack -> stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
			&& stack.is(Items.PAPER) ? 2_500L : null;
		var list = (java.util.List<java.util.function.Function<ItemStack, Long>>) hooks;
		list.add(hook);
		try {
			ItemStack custom = new ItemStack(Items.PAPER, 3);
			net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
			tag.putString("smoke", "oil");
			custom.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
			check(PriceBook.stackSellValue(custom) == 7_500, "a price hook prices another mod's item (3 x 25)");
			check(PriceBook.stackSellValue(new ItemStack(Items.PAPER)) == PriceBook.unitSell(Items.PAPER), "plain paper keeps its own price");
		} finally {
			list.remove(hook);
		}
	}

	static void run(MinecraftServer server) {
		try {
			check(PriceBook.listedCount() > 300, "price list loaded (" + PriceBook.listedCount() + " items)");
			check(PriceBook.unitSell(Items.DIAMOND) == 10_000, "diamond sells for 100");
			check(PriceBook.unitBuy(Items.DIAMOND) >= 20_000, "diamond costs at least 200");
			check(PriceBook.unitBuy(Items.EMERALD) < 0, "emeralds are not buyable");
			check(PriceBook.unitSell(Items.BEDROCK) < 0, "bedrock is not sellable");
			check(PriceBook.unitSell(Items.OAK_STAIRS) > 0, "unlisted items still sell for something");
			check(PriceBook.stackSellValue(new ItemStack(Items.WHEAT, 64)) == 64 * 20, "64 wheat = 12.80");
			check(MarketItems.isMarketBlock(MarketItems.marketBlock()), "market block item round-trips");
			check(MarketItems.banknoteValue(MarketItems.banknote(12_345)) == 12_345, "banknote round-trips");
			check(MarketItems.vanityType(MarketItems.vanity(Vanity.Type.GOLD_PALLET)) == Vanity.Type.GOLD_PALLET, "vanity item round-trips");
			priceHooks();

			// every buyable item must cost more than the market pays for it
			int buyable = 0;
			for (PriceBook.Category category : PriceBook.categories()) {
				for (var item : category.items()) {
					long buy = PriceBook.unitBuy(item);
					if (buy > 0) {
						buyable++;
						if (buy <= PriceBook.unitSell(item)) {
							check(false, "no buy/sell loop for " + PriceBook.id(item));
						}
					}
				}
			}
			check(buyable > 300, buyable + " buyable items, none can be flipped for profit");

			ServerLevel level = server.overworld();
			Vanity.run(level, "forceload add 0 0");
			// give the forced chunk a few seconds to load its entity section
			MarketMod.later(100, () -> vanityChecks(server, level));
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void vanityChecks(MinecraftServer server, ServerLevel level) {
		try {
			BlockPos base = new BlockPos(1, 250, 1);
			int before = countDisplays(level);
			int x = 0;
			for (Vanity.Type type : Vanity.Type.values()) {
				BlockPos pos = base.east(x);
				x += 2;
				Vanity.place(level, pos, type, 0f, "00000000-0000-0000-0000-000000000000", "SmokeTest");
				int now = countDisplays(level);
				check(now > before, "vanity " + type.id + " spawned display entities (" + (now - before) + ")");
				MarketData.VanityRecord record = MarketData.vanityAt(level, pos);
				check(record != null, "vanity " + type.id + " recorded");
				Vanity.remove(level, pos, record);
				check(countDisplays(level) == before, "vanity " + type.id + " removed cleanly");
				check(level.getBlockState(pos).isAir(), "vanity " + type.id + " barrier removed");
			}
			MarketMod.LOG.info("MARKET SMOKE TEST PASSED");
		} catch (Throwable t) {
			fail(server, t);
			return;
		}
		server.halt(false);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		MarketMod.LOG.error("MARKET SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static int countDisplays(ServerLevel level) {
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
		MarketMod.LOG.info("[smoke] ok: {}", what);
	}
}
