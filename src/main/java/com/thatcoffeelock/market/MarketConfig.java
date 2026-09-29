package com.thatcoffeelock.market;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/market.json – general settings. Written with defaults on first launch.
 * config/market-prices.json – the price list. Copied from the built-in list on first launch.
 */
public final class MarketConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String PRICES_FILE = "market-prices.json";
	private static MarketConfig instance = new MarketConfig();

	public String currencyName = "Marks";
	public String currencySymbol = "₥";
	public double startingBalance = 100.0;

	/** Buy price = sell price x buyMarkup x rarity multiplier. Keep this at 2 or above to prevent buy/sell loops. */
	public double buyMarkup = 2.0;
	public Map<String, Double> rarityBuyMultiplier = ordered("COMMON", 1.0, "UNCOMMON", 1.5, "RARE", 2.5, "EPIC", 4.0);

	/** Items that are not in the price list can still be sold for these (tiny) amounts. */
	public boolean sellUnlistedItems = true;
	public Map<String, Double> unlistedSellPrice = ordered("COMMON", 0.01, "UNCOMMON", 2.0, "RARE", 10.0, "EPIC", 50.0);
	public double enchantmentValuePerLevel = 3.0;

	public boolean allowCreativeSelling = false;
	/** When true, /market opens the market from anywhere. When false you need a Market block. */
	public boolean marketCommandEnabled = false;

	public Map<String, Double> vanityPrices = defaultVanityPrices();
	/** Share of the purchase price you get back when selling a vanity item to the market. */
	public double vanityResaleFactor = 0.5;

	public Map<String, Double> sellPriceOverrides = new LinkedHashMap<>();
	public Map<String, Double> buyPriceOverrides = new LinkedHashMap<>();
	public List<String> notSellable = new ArrayList<>(List.of(
		"minecraft:bedrock", "minecraft:barrier", "minecraft:command_block", "minecraft:chain_command_block",
		"minecraft:repeating_command_block", "minecraft:command_block_minecart", "minecraft:structure_block",
		"minecraft:structure_void", "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:light",
		"minecraft:knowledge_book", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
		"minecraft:reinforced_deepslate", "minecraft:end_portal_frame", "minecraft:budding_amethyst",
		"minecraft:petrified_oak_slab", "minecraft:test_block", "minecraft:test_instance_block"));
	/** Emeralds are not buyable by default: villagers would turn them into free money. */
	public List<String> notBuyable = new ArrayList<>(List.of("minecraft:emerald", "minecraft:emerald_block"));

	public static MarketConfig get() {
		return instance;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static void load() {
		Path file = dir().resolve("market.json");
		MarketConfig loaded = null;
		boolean broken = false;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, MarketConfig.class);
			} catch (Exception e) {
				broken = true;
				MarketMod.LOG.error("config/market.json is invalid, using defaults until it is fixed", e);
			}
		}
		MarketConfig cfg = loaded != null ? loaded : new MarketConfig();
		cfg.fillMissing();
		instance = cfg;
		if (!broken) {
			// Re-write so new options show up after an update.
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			} catch (IOException e) {
				MarketMod.LOG.error("Could not write config/market.json", e);
			}
		}
	}

	/** Reads config/market-prices.json, creating it from the built-in list if needed. */
	public static JsonObject loadPrices() {
		Path file = dir().resolve(PRICES_FILE);
		try {
			if (!Files.exists(file)) {
				try (InputStream in = MarketConfig.class.getResourceAsStream("/market/default_prices.json")) {
					if (in == null) {
						throw new IOException("built-in price list missing from the jar");
					}
					Files.copy(in, file);
				}
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (Exception e) {
			MarketMod.LOG.error("Could not read config/" + PRICES_FILE + ", falling back to the built-in list", e);
			try (InputStream in = MarketConfig.class.getResourceAsStream("/market/default_prices.json");
				 Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Exception fatal) {
				MarketMod.LOG.error("Built-in price list is unreadable", fatal);
				return new JsonObject();
			}
		}
	}

	public double rarityMultiplier(String rarity) {
		return rarityBuyMultiplier.getOrDefault(rarity, 1.0);
	}

	public double vanityPrice(String id, double fallback) {
		return vanityPrices.getOrDefault(id, fallback);
	}

	private void fillMissing() {
		MarketConfig d = new MarketConfig();
		if (currencyName == null) currencyName = d.currencyName;
		if (currencySymbol == null) currencySymbol = d.currencySymbol;
		if (rarityBuyMultiplier == null) rarityBuyMultiplier = d.rarityBuyMultiplier;
		if (unlistedSellPrice == null) unlistedSellPrice = d.unlistedSellPrice;
		if (vanityPrices == null) vanityPrices = d.vanityPrices;
		d.vanityPrices.forEach(vanityPrices::putIfAbsent);
		if (sellPriceOverrides == null) sellPriceOverrides = d.sellPriceOverrides;
		if (buyPriceOverrides == null) buyPriceOverrides = d.buyPriceOverrides;
		if (notSellable == null) notSellable = d.notSellable;
		if (notBuyable == null) notBuyable = d.notBuyable;
		if (buyMarkup < 1.0) {
			MarketMod.LOG.warn("buyMarkup below 1.0 lets players print money by buying and re-selling. Clamping to 1.0.");
			buyMarkup = 1.0;
		}
	}

	private static Map<String, Double> defaultVanityPrices() {
		Map<String, Double> map = new LinkedHashMap<>();
		for (Vanity.Type type : Vanity.Type.values()) {
			map.put(type.id, type.defaultPrice);
		}
		return map;
	}

	private static Map<String, Double> ordered(Object... kv) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put((String) kv[i], (Double) kv[i + 1]);
		}
		return map;
	}
}
