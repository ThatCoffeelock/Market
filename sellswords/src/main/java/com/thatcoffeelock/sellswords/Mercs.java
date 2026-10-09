package com.thatcoffeelock.sellswords;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thatcoffeelock.sellswords.mixin.MobAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Every hired sellsword. Each one is two entities walking as one:
 * <ul>
 * <li>a <b>brain</b>: an invisible, silent wandering trader with its own goals taken out. It does the pathfinding,
 * takes the hits (monsters go for wandering traders, so they fight back), and dies when the mercenary does;</li>
 * <li>a <b>body</b>: a mannequin (a player model with a vanilla skin, a name, armour and weapons) that we put where
 * the brain is every tick. Hits on the body are passed on to the brain.</li>
 * </ul>
 * Both carry the tags {@code sellswords}, {@code sellswords_brain}/{@code sellswords_body} and {@code sellswords_id_<id>},
 * so they're found again when their chunk loads, after a restart or a trip through a portal. Saved in sellswords.json.
 */
final class Mercs {
	static final String TAG = "sellswords";
	static final String BRAIN_TAG = "sellswords_brain";
	static final String BODY_TAG = "sellswords_body";
	static final String ID_TAG = "sellswords_id_";
	static final String TEAM = "sellswords";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	static final Random RANDOM = new Random();

	private static final class Snapshot {
		List<Merc> mercs = new ArrayList<>();
		List<Station> stations = new ArrayList<>();
	}

	static final Map<String, Merc> ALL = new LinkedHashMap<>();
	/** Loaded brains and bodies by mercenary id. */
	static final Map<String, LivingEntity> BRAINS = new HashMap<>();
	static final Map<String, LivingEntity> BODIES = new HashMap<>();
	private static @Nullable Path file;
	private static boolean dirty;
	private static @Nullable MinecraftServer server;
	/** Bodies that couldn't be summoned (no mannequins in this game version): the brain shows itself instead. */
	static int bodyFailures;

	private Mercs() {
	}

	// ---------------------------------------------------------------- persistence

	static void load(MinecraftServer srv) {
		server = srv;
		ALL.clear();
		BRAINS.clear();
		BODIES.clear();
		Stations.ALL.clear();
		file = srv.getWorldPath(LevelResource.ROOT).resolve("sellswords.json");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Snapshot snap = GSON.fromJson(reader, Snapshot.class);
				if (snap != null) {
					for (Merc m : snap.mercs) {
						ALL.put(m.id, m);
					}
					for (Station s : snap.stations) {
						Stations.ALL.put(s.key(), s);
					}
				}
			} catch (Exception e) {
				SellswordsMod.LOG.error("Could not read sellswords.json; starting fresh (the old file is kept as .broken)", e);
				try {
					Files.copy(file, file.resolveSibling("sellswords.json.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {
				}
			}
		}
		ServerLevel overworld = srv.overworld();
		Cmd.run(overworld, "team add " + TEAM);
		Cmd.run(overworld, "team modify " + TEAM + " collisionRule never");
		Cmd.run(overworld, "team modify " + TEAM + " color gold");
	}

	static void save() {
		if (file == null) {
			return;
		}
		Snapshot snap = new Snapshot();
		snap.mercs.addAll(ALL.values());
		snap.stations.addAll(Stations.ALL.values());
		try {
			Path tmp = file.resolveSibling("sellswords.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			SellswordsMod.LOG.error("Could not save sellswords.json", e);
		}
	}

	static void stop() {
		save();
		server = null;
		file = null;
	}

	static void changed() {
		dirty = true;
	}

	static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	// ---------------------------------------------------------------- finding them

	static boolean isMerc(@Nullable Entity e) {
		return e != null && e.getTags().contains(TAG);
	}

	static boolean isBrain(@Nullable Entity e) {
		return e != null && e.getTags().contains(BRAIN_TAG);
	}

	static boolean isBody(@Nullable Entity e) {
		return e != null && e.getTags().contains(BODY_TAG);
	}

	static @Nullable String idOf(Entity e) {
		for (String tag : e.getTags()) {
			if (tag.startsWith(ID_TAG)) {
				return tag.substring(ID_TAG.length());
			}
		}
		return null;
	}

	/** The mercenary this brain or body belongs to, or null. */
	static @Nullable Merc of(@Nullable Entity e) {
		if (!isMerc(e)) {
			return null;
		}
		String id = idOf(e);
		return id == null ? null : ALL.get(id);
	}

	static @Nullable LivingEntity brain(Merc m) {
		LivingEntity e = BRAINS.get(m.id);
		return e == null || e.isRemoved() ? null : e;
	}

	static @Nullable LivingEntity body(Merc m) {
		LivingEntity e = BODIES.get(m.id);
		return e == null || e.isRemoved() ? null : e;
	}

	static List<Merc> ownedBy(UUID owner) {
		String key = owner.toString();
		List<Merc> list = new ArrayList<>();
		for (Merc m : ALL.values()) {
			if (m.owner.equals(key)) {
				list.add(m);
			}
		}
		return list;
	}

	/** How many mercenaries this player may have at once: 3, plus one per 25 Leadership levels. */
	static int squadCap(UUID owner) {
		double passive = SkillsLink.bonus(owner, "leadership/passive");
		return Math.max(1, SellswordsConfig.get().squadSize) + (int) Math.floor(passive / 0.0625 + 1e-6);
	}

	static @Nullable ServerLevel level(String dim) {
		if (server == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (Cmd.dimId(level).equals(dim)) {
				return level;
			}
		}
		return null;
	}

	static @Nullable ServerPlayer owner(Merc m) {
		UUID id = m.ownerId();
		if (id == null || server == null) {
			return null;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		return player != null ? player : SmokeTest.owner(id);
	}

	// ---------------------------------------------------------------- loading and unloading

	static void onLoad(Entity entity, ServerLevel level) {
		if (!isMerc(entity) || !(entity instanceof LivingEntity living)) {
			return;
		}
		String id = idOf(entity);
		Merc m = id == null ? null : ALL.get(id);
		if (m == null) {
			// dismissed or dead while their chunk was unloaded: tidy up
			SellswordsMod.nextTick(entity::discard);
			return;
		}
		if (isBrain(entity)) {
			BRAINS.put(m.id, living);
			m.brain = entity.getUUID().toString();
			if (entity instanceof Mob mob) {
				lobotomise(mob);
			}
			entity.setInvisible(BODIES.containsKey(m.id) || bodyFailures == 0);
			Duty.arrived(m, living);
		} else if (isBody(entity)) {
			BODIES.put(m.id, living);
			m.body = entity.getUUID().toString();
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		if (!isMerc(entity)) {
			return;
		}
		String id = idOf(entity);
		if (id == null) {
			return;
		}
		if (BRAINS.get(id) == entity) {
			BRAINS.remove(id);
		}
		if (BODIES.get(id) == entity) {
			BODIES.remove(id);
		}
	}

	/** Takes out every goal the mob came with: no wandering, no panicking, no drinking invisibility potions at dusk. */
	static void lobotomise(Mob mob) {
		MobAccessor accessor = (MobAccessor) mob;
		accessor.sellswords$goals().removeAllGoals(goal -> true);
		accessor.sellswords$targets().removeAllGoals(goal -> true);
		mob.setSilent(true);
		mob.setPersistenceRequired();
	}

	// ---------------------------------------------------------------- hiring

	private static String newId() {
		String id;
		do {
			id = Integer.toHexString(RANDOM.nextInt() | 0x10000000);
		} while (ALL.containsKey(id));
		return id;
	}

	/**
	 * Hires a recruit standing next to {@code near}. No money changes hands here: the station menu takes the fee.
	 * Returns null if nothing could be summoned there.
	 */
	static @Nullable Merc hire(ServerLevel level, BlockPos near, UUID owner, String ownerName, @Nullable Station home) {
		Names.Person person = Names.roll(RANDOM);
		Merc m = new Merc();
		m.id = newId();
		m.owner = owner.toString();
		m.ownerName = ownerName;
		m.name = person.name();
		m.slim = person.slim();
		m.skin = person.skin();
		m.home = home == null ? "" : home.key();
		m.orders(home == null ? Merc.Orders.GUARD : Merc.Orders.STATION);
		m.post(Cmd.dimId(level), near);
		m.hired = System.currentTimeMillis();
		BlockPos at = Spots.near(level, near, 3);
		m.dim = Cmd.dimId(level);
		m.x = at.getX() + 0.5;
		m.y = at.getY();
		m.z = at.getZ() + 0.5;
		ALL.put(m.id, m); // before summoning, or the loading hook would take them for strays
		LivingEntity brain = spawnBrain(level, m, m.x, m.y, m.z);
		if (brain == null) {
			ALL.remove(m.id);
			SellswordsMod.LOG.error("Could not hire a mercenary at {} in {}", at, m.dim);
			return null;
		}
		spawnBody(level, m, m.x, m.y, m.z);
		changed();
		SkillsLink.xp(owner, "leadership", 10);
		Cmd.particles(level, "minecraft:poof", m.x, m.y + 1, m.z, 0.3, 0.02, 12);
		Cmd.sound(level, "minecraft:item.armor.equip_chain", m.x, m.y + 1, m.z, 1f, 0.9f);
		return m;
	}

	private static String tags(Merc m, String kind) {
		return "Tags:[\"" + TAG + "\",\"" + kind + "\",\"" + ID_TAG + m.id + "\"]";
	}

	static @Nullable LivingEntity spawnBrain(ServerLevel level, Merc m, double x, double y, double z) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:wandering_trader " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id)
			+ ",PersistenceRequired:1b,Silent:1b,DespawnDelay:0," + tags(m, BRAIN_TAG) + "}");
		if (!(level.getEntity(id) instanceof LivingEntity brain)) {
			return null;
		}
		m.brain = id.toString();
		BRAINS.put(m.id, brain);
		if (brain instanceof Mob mob) {
			lobotomise(mob);
		}
		brain.setInvisible(true);
		Cmd.run(level, "team join " + TEAM + " " + id);
		applyRank(m, brain, true);
		return brain;
	}

	/** Summons the mannequin. Without mannequins (an older game), the brain shows itself instead. */
	static @Nullable LivingEntity spawnBody(ServerLevel level, Merc m, double x, double y, double z) {
		UUID id = UUID.randomUUID();
		Rank rank = m.rank();
		String base = "summon minecraft:mannequin " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id) + "," + tags(m, BODY_TAG)
			+ ",CustomName:\"" + m.name + "\",CustomNameVisible:1b,NoGravity:1b,Silent:1b";
		Cmd.run(level, base + ",immovable:1b,description:" + description(rank)
			+ ",profile:{texture:\"" + Names.texture(new Names.Person(m.name, m.slim, m.skin)) + "\",model:\"" + (m.slim ? "slim" : "wide") + "\"}}");
		if (!(level.getEntity(id) instanceof LivingEntity)) {
			SellswordsMod.LOG.warn("A mannequin with a skin wouldn't summon; trying a plain one");
			Cmd.run(level, base + "}");
		}
		LivingEntity brain = brain(m);
		if (!(level.getEntity(id) instanceof LivingEntity body)) {
			bodyFailures++;
			SellswordsMod.LOG.error("Could not summon a mannequin for {}: the brain will show itself", m.name);
			if (brain != null) {
				brain.setInvisible(false);
				brain.setCustomName(Component.literal(m.name));
			}
			return null;
		}
		m.body = id.toString();
		BODIES.put(m.id, body);
		Cmd.run(level, "team join " + TEAM + " " + id);
		dress(level, m, body);
		return body;
	}

	/** The line under their name: "Marksman ★★☆☆", in the colour of their path. */
	static String description(Rank rank) {
		return "{text:\"" + rank.title + " " + rank.stars() + "\",color:\"" + rank.path.color.getName() + "\"}";
	}

	// ---------------------------------------------------------------- ranks and gear

	/** Sets the brain's health, armour, speed and knockback resistance for its rank (and the owner's Leadership). */
	static void applyRank(Merc m, LivingEntity brain, boolean heal) {
		Rank r = m.rank();
		UUID owner = m.ownerId();
		double health = r.health * (1 + SkillsLink.bonus(owner, "leadership/passive"));
		set(brain, Attributes.MAX_HEALTH, health);
		set(brain, Attributes.ARMOR, r.armor);
		set(brain, Attributes.ARMOR_TOUGHNESS, r.toughness);
		set(brain, Attributes.KNOCKBACK_RESISTANCE, r.knockbackResistance);
		set(brain, Attributes.MOVEMENT_SPEED, r.speed);
		set(brain, Attributes.FOLLOW_RANGE, 48);
		if (heal || brain.getHealth() > brain.getMaxHealth()) {
			brain.setHealth(brain.getMaxHealth());
		}
	}

	private static void set(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = e.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static EquipmentSlot slot(String name) {
		return switch (name) {
			case "armor.head" -> EquipmentSlot.HEAD;
			case "armor.chest" -> EquipmentSlot.CHEST;
			case "armor.legs" -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
	}

	/** Puts the rank's armour on the body: the plain piece first, then the dyed or trimmed one over it. */
	static void dress(ServerLevel level, Merc m, LivingEntity body) {
		Rank r = m.rank();
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			body.setItemSlot(s, ItemStack.EMPTY);
		}
		for (Rank.Piece piece : r.armour()) {
			body.setItemSlot(slot(piece.slot()), new ItemStack(piece.item()));
			if (!piece.components().isEmpty()) {
				Cmd.run(level, "item replace entity " + body.getUUID() + " " + piece.slot() + " with "
					+ BuiltInRegistries.ITEM.getKey(piece.item()) + "[" + piece.components() + "]");
			}
		}
		Duty.state(m).shown = "";
		wield(level, m, body, !r.path.equals(Rank.Path.MELEE));
	}

	/** Crossbow (or musket) in hand and sword at the ready, or the other way round. Shield-bearers always have the shield. */
	static void wield(ServerLevel level, Merc m, LivingEntity body, boolean ranged) {
		Rank r = m.rank();
		boolean shoot = ranged && r.shoots();
		String mode = shoot ? "ranged" : "melee";
		Duty.State s = Duty.state(m);
		if (mode.equals(s.shown)) {
			return;
		}
		s.shown = mode;
		ItemStack sword = new ItemStack(r.sword());
		ItemStack bow = new ItemStack(Items.CROSSBOW);
		if (r.tier >= 3 && r.path == Rank.Path.RANGED) {
			bow.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		if (r.musket()) {
			bow.set(DataComponents.ITEM_NAME, Component.literal("Musket"));
		}
		if (r.shield) {
			body.setItemSlot(EquipmentSlot.MAINHAND, sword);
			body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			Cmd.run(level, "item replace entity " + body.getUUID() + " weapon.offhand with minecraft:shield[base_color=orange]");
		} else if (shoot) {
			body.setItemSlot(EquipmentSlot.MAINHAND, bow);
			body.setItemSlot(EquipmentSlot.OFFHAND, sword);
		} else {
			body.setItemSlot(EquipmentSlot.MAINHAND, sword);
			body.setItemSlot(EquipmentSlot.OFFHAND, r.shoots() ? bow : ItemStack.EMPTY);
		}
	}

	/** Promotes a mercenary. The caller has checked it's allowed and taken the gold. */
	static void promote(Merc m, Rank to) {
		m.rank = to.name();
		changed();
		LivingEntity brain = brain(m);
		LivingEntity body = body(m);
		if (brain != null) {
			applyRank(m, brain, true);
		}
		if (body != null && body.level() instanceof ServerLevel level) {
			dress(level, m, body);
			Cmd.run(level, "data merge entity " + body.getUUID() + " {description:" + description(to) + "}");
			Cmd.particles(level, "minecraft:totem_of_undying", body.getX(), body.getY() + 1.2, body.getZ(), 0.4, 0.4, 40);
			Cmd.sound(level, "minecraft:entity.player.levelup", body.getX(), body.getY() + 1, body.getZ(), 1f, 1f);
			Cmd.sound(level, "minecraft:item.armor.equip_netherite", body.getX(), body.getY() + 1, body.getZ(), 1f, 1f);
		}
		UUID owner = m.ownerId();
		SkillsLink.xp(owner, "leadership", 20 * to.tier);
	}

	/** Gold ingots a promotion costs this player: Quartermaster takes some off, never below 1. */
	static int promotionCost(Rank to, @Nullable UUID owner) {
		double cost = to.gold * SellswordsConfig.get().promotionCostMultiplier * (1 - Math.min(0.9, SkillsLink.bonus(owner, "leadership/quartermaster")));
		return Math.max(1, (int) Math.ceil(cost - 1e-9));
	}

	/** Sends a mercenary away for good. */
	static void dismiss(Merc m) {
		ALL.remove(m.id);
		changed();
		LivingEntity brain = brain(m);
		LivingEntity body = body(m);
		if (body != null && body.level() instanceof ServerLevel level) {
			Cmd.particles(level, "minecraft:poof", body.getX(), body.getY() + 1, body.getZ(), 0.3, 0.02, 16);
			Cmd.sound(level, "minecraft:entity.villager.celebrate", body.getX(), body.getY() + 1, body.getZ(), 0.6f, 0.8f);
		}
		if (brain != null) {
			brain.discard();
		}
		if (body != null) {
			body.discard();
		}
		BRAINS.remove(m.id);
		BODIES.remove(m.id);
		Duty.forget(m);
	}

	// ---------------------------------------------------------------- death

	static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Entity killer = source.getEntity();
		if (isBrain(killer)) {
			Merc k = of(killer);
			if (k != null) {
				k.kills++;
				changed();
				UUID owner = k.ownerId();
				SkillsLink.xp(owner, "leadership", 3 + entity.getMaxHealth() / 10.0);
				if (Combat.isIllager(entity)) {
					SkillsLink.xp(owner, "bounty_hunting", 4);
				}
			}
		}
		if (entity instanceof ServerPlayer player) {
			Duty.ownerDied(player);
			return;
		}
		if (!isBrain(entity)) {
			return;
		}
		Merc m = of(entity);
		if (m == null) {
			return;
		}
		ALL.remove(m.id);
		changed();
		Duty.forget(m);
		BRAINS.remove(m.id);
		LivingEntity body = BODIES.remove(m.id);
		if (body != null && !body.isRemoved()) {
			for (EquipmentSlot s : EquipmentSlot.values()) {
				body.setItemSlot(s, ItemStack.EMPTY); // their gear goes with them: nothing to loot
			}
			Cmd.sound(level, "minecraft:entity.player.death", body.getX(), body.getY() + 1, body.getZ(), 1f, 1f);
			Cmd.run(level, "kill " + body.getUUID());
		}
		ServerPlayer owner = owner(m);
		if (owner != null) {
			String by = killer == null ? "" : " to " + killer.getName().getString();
			owner.sendSystemMessage(Component.literal("☠ " + m.title() + " has fallen" + by + " (" + m.kills + (m.kills == 1 ? " kill" : " kills")
				+ ") at " + entity.getBlockX() + " " + entity.getBlockY() + " " + entity.getBlockZ() + ". Rest well, " + m.firstName() + ".")
				.withStyle(ChatFormatting.RED));
		}
		SellswordsMod.LOG.info("{} ({}) died at {} in {}", m.name, m.rank().title, entity.blockPosition(), m.dim);
	}

	/** The world a level belongs to, as stored ("minecraft:overworld"). */
	static String dim(Level level) {
		return Cmd.dimId(level);
	}
}
