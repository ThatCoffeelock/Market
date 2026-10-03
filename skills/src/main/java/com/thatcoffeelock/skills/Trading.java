package com.thatcoffeelock.skills;

import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Mercantile. XP from villager trades comes from the stats (see Tracker); this class does the prices:
 * Silver Tongue's villager discount, and the Market's sell bonus and buy discount.
 *
 * The Market mod doesn't depend on this one (or the other way round). They meet through Fabric's ObjectShare:
 * Skills publishes two functions there, and Market calls them if they exist.
 */
final class Trading {
	/** (player, total in cents) -> new total. Market calls this when a player sells. */
	static final String MARKET_SELL = "skills:market_sell";
	/** (player, cost in cents) -> new cost. Market calls this when a player buys. */
	static final String MARKET_BUY = "skills:market_buy";

	private Trading() {
	}

	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(MARKET_SELL, (BiFunction<ServerPlayer, Long, Long>) Trading::marketSell);
		share.put(MARKET_BUY, (BiFunction<ServerPlayer, Long, Long>) Trading::marketBuy);
	}

	/** Selling: +0.1% per level, + Haggler. XP: 4 per Mark. */
	static Long marketSell(ServerPlayer player, Long cents) {
		if (cents == null || cents <= 0) {
			return cents;
		}
		double bonus = Skills.passive(player, Skill.MERCANTILE) + Skills.perk(player, Perk.HAGGLER);
		Skills.award(player, Skill.MERCANTILE, cents / 25.0);
		return Math.round(cents * (1.0 + bonus));
	}

	/** Buying: Bulk Buyer's discount. XP: 1 per Mark spent. Market makes sure buying stays dearer than selling. */
	static Long marketBuy(ServerPlayer player, Long cents) {
		if (cents == null || cents <= 0) {
			return cents;
		}
		double discount = Skills.perk(player, Perk.BULK_BUYER);
		long cost = Math.max(1L, Math.round(cents * (1.0 - discount)));
		Skills.award(player, Skill.MERCANTILE, cost / 100.0);
		return cost;
	}

	/**
	 * Silver Tongue: knocks a percentage off a villager's prices for this player, just before the trade screen
	 * opens. It uses the same per-offer price adjustment as Hero of the Village and gossip, which vanilla resets
	 * when the trade screen closes, so the discount never sticks to the villager.
	 */
	static void beforeTrade(ServerPlayer player, Entity entity) {
		if (!(entity instanceof Merchant merchant) || !(entity instanceof LivingEntity living) || living.isBaby()) {
			return;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		// wandering traders never reset their prices, so they don't get haggled
		if (!type.equals("villager") || merchant.getTradingPlayer() != null) {
			return;
		}
		double discount = Skills.perk(player, Perk.SILVER_TONGUE);
		if (discount <= 0) {
			return;
		}
		for (MerchantOffer offer : merchant.getOffers()) {
			int base = offer.getBaseCostA().getCount();
			int off = (int) Math.floor(base * discount);
			if (off > 0) {
				offer.addToSpecialPriceDiff(-off);
			}
		}
	}
}
