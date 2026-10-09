package com.thatcoffeelock.fuckillagers;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Contracts and trophies. Saved in fuckillagers.json in the world folder.
 *
 * <p>A contract's hideout isn't built when it's posted (the spot is 1000-2000 blocks away, in terrain that may not
 * even exist yet). It's built when someone gets within {@link #BUILD_RANGE} blocks and the area is loaded.
 */
final class Bounties {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Random RANDOM = new Random();
	static final int MIN_DISTANCE = 1000;
	static final int MAX_DISTANCE = 2000;
	/** Build a hideout once a player is this close. */
	static final int BUILD_RANGE = 160;

	private static final String[] FIRST = {"Grimbold", "Vex", "Morwen", "Thaddeus", "Ulric", "Brannoc", "Sela", "Corvin", "Drusk", "Malgor",
		"Ysolde", "Harrow", "Kesh", "Oswin", "Rook", "Varga", "Edric", "Murn", "Tavish", "Zora"};
	private static final String[] TITLE = {"the Vile", "the Grim", "Ear-Taker", "the Unwashed", "the Tax Collector", "Ninefingers",
		"the Merciless", "Raven-Eye", "the Loud", "the Pillager-King", "of the Long Axe", "the Smelly", "Goat-Botherer", "the Unforgiven",
		"Barn-Burner", "the Very Rude"};

	private static final class Snapshot {
		List<String> stations = new ArrayList<>();
		List<Contract> contracts = new ArrayList<>();
		int nextId = 1;
	}

	static final Set<String> STATIONS = new HashSet<>();
	static final Map<String, Contract> CONTRACTS = new LinkedHashMap<>();
	private static int nextId = 1;
	private static @Nullable Path file;
	private static boolean dirty;
	private static int ticks;

	private Bounties() {
	}

	// ---------------------------------------------------------------- prices

	static long fingerPrice() {
		return Money.fromDecimal(3.0);
	}

	static long reward(Tier tier) {
		return Money.fromDecimal(tier.reward);
	}

	/** Fingers an illager drops when a player kills it, by entity id. 0 for anything else. */
	static int fingersFor(Entity entity) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		return switch (id) {
			case "pillager", "vindicator" -> 1;
			case "evoker", "illusioner" -> 2;
			default -> 0;
		};
	}

	// ---------------------------------------------------------------- persistence

	static void load(MinecraftServer server) {
		STATIONS.clear();
		CONTRACTS.clear();
		nextId = 1;
		file = server.getWorldPath(LevelResource.ROOT).resolve("fuckillagers.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap != null) {
				STATIONS.addAll(snap.stations);
				for (Contract c : snap.contracts) {
					CONTRACTS.put(c.id, c);
				}
				nextId = Math.max(1, snap.nextId);
			}
		} catch (Exception e) {
			FuckIllagersMod.LOG.error("Could not read fuckillagers.json; starting fresh (the old file is kept as .broken)", e);
			try {
				Files.copy(file, file.resolveSibling("fuckillagers.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	static void save() {
		if (file == null) {
			return;
		}
		Snapshot snap = new Snapshot();
		snap.stations.addAll(STATIONS);
		snap.contracts.addAll(CONTRACTS.values());
		snap.nextId = nextId;
		try {
			Path tmp = file.resolveSibling("fuckillagers.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			FuckIllagersMod.LOG.error("Could not save fuckillagers.json", e);
		}
	}

	static void changed() {
		dirty = true;
	}

	// ---------------------------------------------------------------- stations

	static String key(Level level, BlockPos pos) {
		return level.dimension() + "@" + pos.asLong();
	}

	static boolean isStation(Level level, BlockPos pos) {
		return STATIONS.contains(key(level, pos));
	}

	static void addStation(Level level, BlockPos pos) {
		if (STATIONS.add(key(level, pos))) {
			changed();
		}
	}

	static void removeStation(Level level, BlockPos pos) {
		if (STATIONS.remove(key(level, pos))) {
			changed();
		}
	}

	// ---------------------------------------------------------------- contracts

	static @Nullable Contract openContract(UUID player) {
		for (Contract c : CONTRACTS.values()) {
			if (c.open() && c.ownedBy(player)) {
				return c;
			}
		}
		return null;
	}

	static @Nullable Contract get(String id) {
		return CONTRACTS.get(id);
	}

	static String randomName() {
		return FIRST[RANDOM.nextInt(FIRST.length)] + " " + TITLE[RANDOM.nextInt(TITLE.length)];
	}

	/** Posts a contract for this player, somewhere 1000-2000 blocks from the station. Null + a message if not allowed. */
	static @Nullable Contract accept(ServerPlayer player, ServerLevel level, BlockPos from, Tier tier) {
		if (level != level.getServer().overworld()) {
			player.sendSystemMessage(Component.literal("Contracts are only posted in the Overworld.").withStyle(ChatFormatting.RED));
			return null;
		}
		if (openContract(player.getUUID()) != null) {
			player.sendSystemMessage(Component.literal("Finish or abandon your current contract first.").withStyle(ChatFormatting.RED));
			return null;
		}
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double distance = MIN_DISTANCE + RANDOM.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
		Contract c = new Contract();
		c.id = "c" + (nextId++);
		c.owner = player.getUUID().toString();
		c.ownerName = player.getName().getString();
		c.tier = tier.name();
		c.site = tier.sites.get(RANDOM.nextInt(tier.sites.size())).name();
		c.target = randomName();
		c.x = (int) Math.round(from.getX() + Math.cos(angle) * distance);
		c.z = (int) Math.round(from.getZ() + Math.sin(angle) * distance);
		CONTRACTS.put(c.id, c);
		changed();
		Trophies.give(player, Trophies.poster(c));
		player.sendSystemMessage(Component.literal("Contract accepted: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(c.target).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
			.append(Component.literal(", hiding in " + c.site().what + " near X " + c.x + ", Z " + c.z
				+ " (" + Math.round(distance) + " blocks " + direction(c.x - from.getX(), c.z - from.getZ()) + "). ").withStyle(ChatFormatting.GOLD))
			.append(Component.literal("Bring back the skull: " + Money.format(reward(tier)) + ".").withStyle(ChatFormatting.YELLOW)));
		return c;
	}

	static void abandon(ServerPlayer player) {
		Contract c = openContract(player.getUUID());
		if (c != null) {
			c.state = Contract.ABANDONED;
			changed();
			player.sendSystemMessage(Component.literal("Contract on " + c.target + " abandoned. They'll be insufferable about it.").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Compass direction for a step in x/z (north is -z). */
	static String direction(double dx, double dz) {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		double deg = Math.toDegrees(Math.atan2(-dx, dz));
		int i = (int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8;
		return names[i];
	}

	// ---------------------------------------------------------------- selling

	/** Sells every finger and skull the player carries. Returns what it paid, in cents (0 if nothing to sell). */
	static long sellAll(ServerPlayer player) {
		Inventory inv = player.getInventory();
		long fingerTotal = 0;
		long skullTotal = 0;
		int fingers = 0;
		int skulls = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (Trophies.isFinger(stack)) {
				fingers += stack.getCount();
				fingerTotal += fingerPrice() * stack.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			} else if (Trophies.isSkull(stack)) {
				skulls += stack.getCount();
				skullTotal += Trophies.rewardOf(stack) * stack.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		// Bounty Hunting (Skills mod): Fence pays more for fingers, Dead or Alive more for skulls
		java.util.UUID id = player.getUUID();
		long total = Math.round(fingerTotal * (1.0 + SkillsLink.bonus(id, "bounty_hunting/fence")))
			+ Math.round(skullTotal * (1.0 + SkillsLink.bonus(id, "bounty_hunting/dead_or_alive")));
		if (total > 0) {
			SkillsLink.xp(id, "bounty_hunting", 2.0 * fingers + skullTotal / 500.0);
			MarketData.deposit(player, total);
			MutableComponent msg = Component.literal("Sold " + fingers + (fingers == 1 ? " finger" : " fingers")).withStyle(ChatFormatting.GOLD);
			if (skulls > 0) {
				msg.append(Component.literal(" and " + skulls + (skulls == 1 ? " skull" : " skulls")).withStyle(ChatFormatting.GOLD));
			}
			player.sendSystemMessage(msg.append(Component.literal(" for ").withStyle(ChatFormatting.GOLD)).append(Money.text(total)).append(Component.literal(".")));
		}
		return total;
	}

	static int count(ServerPlayer player, boolean skulls) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (skulls ? Trophies.isSkull(stack) : Trophies.isFinger(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static long skullValue(ServerPlayer player) {
		Inventory inv = player.getInventory();
		long n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (Trophies.isSkull(stack)) {
				n += Trophies.rewardOf(stack) * stack.getCount();
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- deaths

	static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		String contractId = entity.getAttachedOrElse(FuckIllagersMod.BOSS, "");
		if (!contractId.isEmpty()) {
			Contract c = CONTRACTS.get(contractId);
			if (c != null && c.open()) {
				c.state = Contract.DONE;
				changed();
				drop(level, entity, Trophies.skull(c));
				Cmd.sound(level, "minecraft:entity.wither.death", entity.getX(), entity.getY(), entity.getZ(), 0.6f, 1.4f);
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(UUID.fromString(c.owner));
				if (owner != null) {
					owner.sendSystemMessage(Component.literal(c.target + " is dead. Take the skull to a Bounty Station for "
						+ Money.format(reward(c.tier())) + ".").withStyle(ChatFormatting.GOLD));
				}
			}
		}
		if (source.getEntity() instanceof ServerPlayer || isMercenary(source.getEntity())) {
			int fingers = fingersFor(entity);
			if (fingers > 0) {
				drop(level, entity, Trophies.finger(fingers));
			}
		}
	}

	/**
	 * A Sellswords mercenary, asked through Sellswords' ObjectShare API ({@code sellswords:api}, "is_mercenary"), so
	 * neither mod needs the other. False without Sellswords.
	 */
	@SuppressWarnings("unchecked")
	static boolean isMercenary(@Nullable Entity entity) {
		if (entity == null) {
			return false;
		}
		Object api = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get("sellswords:api");
		if (!(api instanceof java.util.function.BiFunction<?, ?, ?> f)) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(((java.util.function.BiFunction<String, Map<String, Object>, Object>) f).apply("is_mercenary", Map.of("entity", entity)));
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void drop(ServerLevel level, Entity at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % 20 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		for (Contract c : new ArrayList<>(CONTRACTS.values())) {
			if (Contract.POSTED.equals(c.state) && someoneNear(overworld, c.x, c.z, BUILD_RANGE) && Sites.loaded(overworld, c)) {
				build(overworld, c);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			hud(player);
		}
		if (dirty && ticks % 200 == 0) {
			save();
		}
	}

	/** Builds the hideout and spawns the target. Public to the tests. */
	static void build(ServerLevel level, Contract c) {
		try {
			Sites.Built built = Sites.build(level, c);
			c.y = built.y();
			c.boss = built.boss() == null ? "" : built.boss().getUUID().toString();
			c.state = Contract.ACTIVE;
			changed();
			FuckIllagersMod.LOG.info("Built {} for contract {} ({}) at {} {} {}", c.site, c.id, c.target, c.x, c.y, c.z);
		} catch (RuntimeException e) {
			FuckIllagersMod.LOG.error("Could not build the hideout for contract {}", c.id, e);
			c.state = Contract.ABANDONED;
			changed();
		}
	}

	private static boolean someoneNear(ServerLevel level, int x, int z, int range) {
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			if (dx * dx + dz * dz < (double) range * range) {
				return true;
			}
		}
		return false;
	}

	/** Holding a Wanted Poster shows how far away the target is. */
	private static void hud(ServerPlayer player) {
		ItemStack poster = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!Trophies.isPoster(poster)) {
			poster = player.getItemInHand(InteractionHand.OFF_HAND);
			if (!Trophies.isPoster(poster)) {
				return;
			}
		}
		Contract c = CONTRACTS.get(Trophies.contractOf(poster));
		MutableComponent line;
		if (c == null || Contract.ABANDONED.equals(c.state)) {
			line = Component.literal("This contract is void.").withStyle(ChatFormatting.GRAY);
		} else if (Contract.DONE.equals(c.state)) {
			line = Component.literal("☠ " + c.target + " is dead. Collect at a Bounty Station.").withStyle(ChatFormatting.GOLD);
		} else if (player.level() != player.level().getServer().overworld()) {
			line = Component.literal("⚑ " + c.target + " is in the Overworld.").withStyle(ChatFormatting.GRAY);
		} else {
			double dx = c.x - player.getX();
			double dz = c.z - player.getZ();
			long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
			line = Component.literal("⚑ " + c.target + ": ").withStyle(ChatFormatting.RED)
				.append(Component.literal(dist + " blocks " + direction(dx, dz)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  (X " + c.x + ", Z " + c.z + ")").withStyle(ChatFormatting.GRAY));
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}
}
