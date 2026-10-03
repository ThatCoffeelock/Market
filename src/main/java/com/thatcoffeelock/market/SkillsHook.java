package com.thatcoffeelock.market;

import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional link to the Skills mod's Mercantile skill: better sell prices, cheaper buying, and Mercantile XP.
 * Skills puts two functions in Fabric's ObjectShare; if it isn't installed, prices are left alone.
 * Neither mod needs the other to compile or run.
 */
public final class SkillsHook {
	private SkillsHook() {
	}

	@SuppressWarnings("unchecked")
	private static long apply(String key, ServerPlayer player, long cents) {
		Object hook = FabricLoader.getInstance().getObjectShare().get(key);
		if (!(hook instanceof BiFunction<?, ?, ?> function)) {
			return cents;
		}
		try {
			Long result = ((BiFunction<ServerPlayer, Long, Long>) function).apply(player, cents);
			return result == null ? cents : result;
		} catch (RuntimeException e) {
			MarketMod.LOG.warn("Skills hook {} failed, using the normal price", key, e);
			return cents;
		}
	}

	/** Total a sale pays out, after the seller's Mercantile bonus. */
	public static long sell(ServerPlayer player, long cents) {
		return Math.max(cents, apply("skills:market_sell", player, cents));
	}

	/**
	 * What a purchase costs after the buyer's Mercantile discount. Never below 1.25x what the market pays for the
	 * same items, so a maxed-out merchant still can't buy and re-sell for profit.
	 */
	public static long buy(ServerPlayer player, long cents, long sellValue) {
		long discounted = Math.min(cents, apply("skills:market_buy", player, cents));
		long floor = sellValue > 0 ? (long) Math.ceil(sellValue * 1.25) : 1L;
		return Math.max(discounted, Math.min(cents, floor));
	}
}
