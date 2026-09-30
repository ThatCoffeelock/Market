package com.thatcoffeelock.market;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** Bank accounts, Market block locations and placed vanity items. Saved to <world>/market-data.json. */
public final class MarketData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final class Account {
		public String name = "?";
		public long cents;
	}

	public static final class VanityRecord {
		public String type;
		public String owner;
		public String ownerName;
		public String tag;
	}

	private static final class Snapshot {
		Map<String, Account> accounts = new HashMap<>();
		List<String> markets = new ArrayList<>();
		Map<String, VanityRecord> vanity = new HashMap<>();
	}

	private static final Map<UUID, Account> ACCOUNTS = new HashMap<>();
	private static final Set<String> MARKETS = new HashSet<>();
	private static final Map<String, VanityRecord> VANITY = new HashMap<>();
	private static @Nullable Path file;
	private static boolean dirty;

	private MarketData() {
	}

	// ---------------------------------------------------------------- persistence

	public static void load(MinecraftServer server) {
		ACCOUNTS.clear();
		MARKETS.clear();
		VANITY.clear();
		file = server.getWorldPath(LevelResource.ROOT).resolve("market-data.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap == null) {
				return;
			}
			if (snap.accounts != null) {
				snap.accounts.forEach((uuid, account) -> ACCOUNTS.put(UUID.fromString(uuid), account));
			}
			if (snap.markets != null) {
				MARKETS.addAll(snap.markets);
			}
			if (snap.vanity != null) {
				VANITY.putAll(snap.vanity);
			}
		} catch (Exception e) {
			MarketMod.LOG.error("Could not read market-data.json! Balances will start fresh; the old file is kept as market-data.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("market-data.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	public static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		Snapshot snap = new Snapshot();
		ACCOUNTS.forEach((uuid, account) -> snap.accounts.put(uuid.toString(), account));
		snap.markets.addAll(MARKETS);
		snap.vanity.putAll(VANITY);
		try {
			Path tmp = file.resolveSibling("market-data.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			MarketMod.LOG.error("Could not save market-data.json", e);
		}
	}

	private static void changed() {
		dirty = true;
	}

	// ---------------------------------------------------------------- bank

	public static Account account(ServerPlayer player) {
		Account account = ACCOUNTS.get(player.getUUID());
		String name = player.getName().getString();
		if (account == null) {
			account = new Account();
			account.cents = Money.fromDecimal(MarketConfig.get().startingBalance);
			ACCOUNTS.put(player.getUUID(), account);
			changed();
		}
		if (!name.equals(account.name)) {
			account.name = name;
			changed();
		}
		return account;
	}

	public static long balance(ServerPlayer player) {
		return account(player).cents;
	}

	public static void deposit(ServerPlayer player, long cents) {
		account(player).cents += cents;
		changed();
	}

	/** @return false (and takes nothing) if the player can't afford it. */
	public static boolean withdraw(ServerPlayer player, long cents) {
		Account account = account(player);
		if (cents < 0 || account.cents < cents) {
			return false;
		}
		account.cents -= cents;
		changed();
		return true;
	}

	public static void setBalance(ServerPlayer player, long cents) {
		account(player).cents = cents;
		changed();
	}

	// ---------------------------------------------------------------- bank, by UUID (for other mods, e.g. Colonycraft wages while you're offline)

	/** Balance of any player who ever had an account, online or not. 0 if they never had one. */
	public static long balance(UUID player) {
		Account account = ACCOUNTS.get(player);
		return account == null ? 0 : account.cents;
	}

	/** @return false (and takes nothing) if the player can't afford it or has no account. */
	public static boolean withdraw(UUID player, long cents) {
		Account account = ACCOUNTS.get(player);
		if (account == null || cents < 0 || account.cents < cents) {
			return false;
		}
		account.cents -= cents;
		changed();
		return true;
	}

	/** Pays into any player's account, creating it if needed. */
	public static void deposit(UUID player, String name, long cents) {
		Account account = ACCOUNTS.computeIfAbsent(player, id -> {
			Account fresh = new Account();
			fresh.name = name;
			return fresh;
		});
		account.cents += cents;
		changed();
	}

	public static List<Account> richest(int limit) {
		return ACCOUNTS.values().stream()
			.sorted(Comparator.comparingLong((Account a) -> a.cents).reversed())
			.limit(limit)
			.toList();
	}

	// ---------------------------------------------------------------- market blocks & vanity

	public static String key(Level level, BlockPos pos) {
		return level.dimension() + "@" + pos.asLong();
	}

	public static boolean isMarket(Level level, BlockPos pos) {
		return MARKETS.contains(key(level, pos));
	}

	public static void addMarket(Level level, BlockPos pos) {
		if (MARKETS.add(key(level, pos))) {
			changed();
		}
	}

	public static void removeMarket(Level level, BlockPos pos) {
		if (MARKETS.remove(key(level, pos))) {
			changed();
		}
	}

	public static @Nullable VanityRecord vanityAt(Level level, BlockPos pos) {
		return VANITY.get(key(level, pos));
	}

	public static void putVanity(Level level, BlockPos pos, VanityRecord record) {
		VANITY.put(key(level, pos), record);
		changed();
	}

	public static void removeVanity(Level level, BlockPos pos) {
		if (VANITY.remove(key(level, pos)) != null) {
			changed();
		}
	}
}
