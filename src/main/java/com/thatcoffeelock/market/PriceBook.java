package com.thatcoffeelock.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Knows what everything is worth. Prices are stored in cents.
 * <p>
 * Sell price comes from config/market-prices.json (exact items first, then item tags, then a small
 * rarity-based fallback so literally anything can be sold). Buy price = sell price x markup x rarity.
 */
public final class PriceBook {
	public record Category(String name, Item icon, List<Item> items) {
	}

	private static List<Category> categories = List.of();
	private static Map<Item, Long> listed = Map.of();
	private static Map<String, Long> tagPrices = Map.of();
	private static Set<Item> notSellable = Set.of();
	private static Set<Item> notBuyable = Set.of();
	private static Map<Item, Long> sellOverrides = Map.of();
	private static Map<Item, Long> buyOverrides = Map.of();
	private static final Map<Item, Long> UNIT_CACHE = new ConcurrentHashMap<>();
	private static int unknownIds;

	private PriceBook() {
	}

	public static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static void reload() {
		MarketConfig cfg = MarketConfig.get();
		Map<String, Item> byId = new HashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			byId.put(id(item), item);
		}
		unknownIds = 0;

		JsonObject root = MarketConfig.loadPrices();
		List<Category> cats = new ArrayList<>();
		Map<Item, Long> prices = new HashMap<>();
		if (root.has("categories")) {
			for (JsonElement el : root.getAsJsonArray("categories")) {
				JsonObject obj = el.getAsJsonObject();
				String name = obj.get("name").getAsString();
				List<Item> items = new ArrayList<>();
				for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("items").entrySet()) {
					Item item = lookup(byId, entry.getKey());
					if (item == null) {
						continue;
					}
					prices.put(item, Money.fromDecimal(entry.getValue().getAsDouble()));
					items.add(item);
				}
				Item icon = obj.has("icon") ? lookup(byId, obj.get("icon").getAsString()) : null;
				if (icon == null) {
					icon = items.isEmpty() ? Items.CHEST : items.getFirst();
				}
				if (!items.isEmpty()) {
					cats.add(new Category(name, icon, List.copyOf(items)));
				}
			}
		}
		Map<String, Long> tags = new HashMap<>();
		if (root.has("tags")) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("tags").entrySet()) {
				String tag = entry.getKey().startsWith("#") ? entry.getKey().substring(1) : entry.getKey();
				tags.put(tag, Money.fromDecimal(entry.getValue().getAsDouble()));
			}
		}

		categories = List.copyOf(cats);
		listed = prices;
		tagPrices = tags;
		notSellable = itemSet(byId, cfg.notSellable);
		notBuyable = itemSet(byId, cfg.notBuyable);
		sellOverrides = itemMap(byId, cfg.sellPriceOverrides);
		buyOverrides = itemMap(byId, cfg.buyPriceOverrides);
		UNIT_CACHE.clear();
		MarketMod.LOG.info("Price list loaded: {} items in {} categories, {} tag prices{}", listed.size(), categories.size(),
			tagPrices.size(), unknownIds > 0 ? " (" + unknownIds + " unknown ids skipped)" : "");
	}

	public static List<Category> categories() {
		return categories;
	}

	public static int listedCount() {
		return listed.size();
	}

	/** Sell price for a single plain item, in cents. -1 when the market won't take it. */
	public static long unitSell(Item item) {
		return UNIT_CACHE.computeIfAbsent(item, PriceBook::computeUnitSell);
	}

	private static long computeUnitSell(Item item) {
		String id = id(item);
		if (item == Items.AIR || notSellable.contains(item) || id.endsWith("_spawn_egg")) {
			return -1L;
		}
		Long price = sellOverrides.get(item);
		if (price == null) {
			price = listed.get(item);
		}
		if (price == null && !tagPrices.isEmpty()) {
			long best = -1;
			for (var tag : new ItemStack(item).getTags().toList()) {
				Long tagPrice = tagPrices.get(tag.location().toString());
				if (tagPrice != null && tagPrice > best) {
					best = tagPrice;
				}
			}
			if (best >= 0) {
				price = best;
			}
		}
		if (price == null) {
			MarketConfig cfg = MarketConfig.get();
			if (!cfg.sellUnlistedItems) {
				return -1L;
			}
			String rarity = item.getDefaultInstance().getRarity().name();
			price = Money.fromDecimal(cfg.unlistedSellPrice.getOrDefault(rarity, 0.01));
		}
		return Math.max(0L, price);
	}

	/**
	 * What the market pays for this whole stack, in cents. Accounts for damage and enchantments.
	 * -1 when the market won't take it (unsellable, or a shulker box / bundle that still has items inside).
	 */
	public static long stackSellValue(ItemStack stack) {
		if (stack.isEmpty()) {
			return -1;
		}
		long note = MarketItems.banknoteValue(stack);
		if (note > 0) {
			return note * stack.getCount();
		}
		Vanity.Type vanity = MarketItems.vanityType(stack);
		if (vanity != null) {
			return Math.round(vanity.price() * MarketConfig.get().vanityResaleFactor) * stack.getCount();
		}
		if (MarketItems.isMarketBlock(stack)) {
			long value = 4 * Math.max(0, unitSell(Items.OBSIDIAN)) + 4 * Math.max(0, unitSell(Items.GOLD_INGOT))
				+ Math.max(0, unitSell(Items.EMERALD));
			return value * stack.getCount();
		}
		if (hasContents(stack)) {
			return -1;
		}
		long unit = unitSell(stack.getItem());
		if (unit < 0) {
			return -1;
		}
		if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			unit = unit * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
		}
		int levels = enchantmentLevels(stack.get(DataComponents.ENCHANTMENTS))
			+ enchantmentLevels(stack.get(DataComponents.STORED_ENCHANTMENTS));
		unit += levels * Money.fromDecimal(MarketConfig.get().enchantmentValuePerLevel);
		return unit * stack.getCount();
	}

	/** Buy price for one item, in cents. -1 when the market doesn't sell it. */
	public static long unitBuy(Item item) {
		if (notBuyable.contains(item)) {
			return -1;
		}
		Long override = buyOverrides.get(item);
		if (override != null) {
			return override;
		}
		Long sell = listed.get(item);
		if (sell == null) {
			return -1;
		}
		Long sellOverride = sellOverrides.get(item);
		if (sellOverride != null) {
			sell = sellOverride;
		}
		MarketConfig cfg = MarketConfig.get();
		double multiplier = cfg.buyMarkup * cfg.rarityMultiplier(item.getDefaultInstance().getRarity().name());
		return Math.max(1L, Math.round(sell * multiplier));
	}

	private static boolean hasContents(ItemStack stack) {
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container != null && !container.equals(ItemContainerContents.EMPTY)) {
			return true;
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		return bundle != null && !bundle.isEmpty();
	}

	private static int enchantmentLevels(ItemEnchantments enchantments) {
		if (enchantments == null) {
			return 0;
		}
		int total = 0;
		for (var entry : enchantments.entrySet()) {
			total += entry.getIntValue();
		}
		return total;
	}

	private static Item lookup(Map<String, Item> byId, String id) {
		String full = id.contains(":") ? id : "minecraft:" + id;
		Item item = byId.get(full);
		if (item == null || item == Items.AIR) {
			unknownIds++;
			MarketMod.LOG.debug("Unknown item id in price list: {}", id);
			return null;
		}
		return item;
	}

	private static Set<Item> itemSet(Map<String, Item> byId, List<String> ids) {
		Set<Item> set = new HashSet<>();
		for (String id : ids) {
			Item item = lookup(byId, id);
			if (item != null) {
				set.add(item);
			}
		}
		return set;
	}

	private static Map<Item, Long> itemMap(Map<String, Item> byId, Map<String, Double> prices) {
		Map<Item, Long> map = new HashMap<>();
		prices.forEach((id, price) -> {
			Item item = lookup(byId, id);
			if (item != null) {
				map.put(item, Money.fromDecimal(price));
			}
		});
		return map;
	}
}
