package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The cellblock's business. Beat an illager down, shackle them (they go into the shackles, like a villager
 * into a burlap sack), and put the shackles into a cell's holding block: the prisoner appears in the cell
 * and stands there, harmless, until you take them out again or execute them for a bounty.
 *
 * Instead of executing them in the cell you can ransom them back to the illagers (the longer you've held them,
 * the more they pay) or execute them publicly on the colony's scaffold, which pays double or more.
 *
 * Prisoners are the real mob with NoAI: they don't move, fight, cast spells or join raids, and they're
 * invulnerable so the colony's guards can't do the executioner's job for free.
 */
public final class Prison {
	/** Illagers have to be beaten down to this share of their health before they can be shackled. */
	public static final double WEAK = 0.4;
	/** Price in ₥ of a pair of shackles at the Town Hall. */
	public static final double SHACKLES_PRICE = 50;
	/** Bread and water, in ₥ per prisoner per day, paid with the wages. */
	public static final double UPKEEP = 2;
	/** Who fits in shackles, and the bounty in ₥ for executing them. */
	public static final Map<String, Double> BOUNTY = Map.of(
		"minecraft:pillager", 25.0,
		"minecraft:vindicator", 40.0,
		"minecraft:illusioner", 80.0,
		"minecraft:evoker", 100.0);

	/** Each full day in a cell adds this share of the bounty to the ransom the illagers pay... */
	public static final double RANSOM_PER_DAY = 0.25;
	/** ...up to this many times the bounty. */
	public static final double RANSOM_CAP = 2.5;
	/** How long the crowd gets to gather before a public execution, in ticks. */
	public static final int SHOW_TICKS = 120;

	/** One prisoner: who they are and since when (game time) they've been inside. */
	public record Prisoner(UUID id, String name, String type, long since) {
	}

	static final String KEY = "colonycraft_shackles";
	static final String EMPTY = "empty";
	static final String FULL = "full";
	static final String TYPE = "type";
	static final String NAME = "name";
	static final String CAPTIVE = "captive";

	/** Saved data that must not travel with the captive: identity, position, memories, raid membership, and our own cell settings. */
	private static final List<String> STRIP = List.of("UUID", "Pos", "Motion", "Rotation", "FallDistance", "fall_distance",
		"Fire", "OnGround", "PortalCooldown", "Brain", "leash", "RaidId", "Wave", "NoAI", "Invulnerable", "CustomNameVisible",
		"DeathLootTable");

	private static final String[] NAMES = {"Gary", "Dirk", "Barend", "Gerrit", "Sjaak", "Rutger", "Brutus", "Kevin", "Hendrik",
		"Snotje", "Lars", "Mitch", "Bas", "Kobus", "Ronnie"};
	private static final String[] SHACKLED = {
		" is in irons. Should've stayed in the mansion.",
		" has the right to remain silent. They've never said anything useful anyway.",
		" is under arrest for crimes against villagers, and against good taste.",
		" is shackled. The axe-swinging era is over.",
	};
	private static final String[] LOCKED = {
		" is locked up. Visiting hours: never.",
		" settles into their cell and glares at the bucket.",
		" is behind bars. The cobweb is included at no extra charge.",
	};

	/** A public execution in progress: the condemned stands on the scaffold while the bell counts down. */
	private static final class Show {
		final ServerLevel level;
		final Colony.Building scaffold;
		final UUID id;
		final String name;
		final String type;
		int left = SHOW_TICKS;

		Show(ServerLevel level, Colony.Building scaffold, UUID id, String name, String type) {
			this.level = level;
			this.scaffold = scaffold;
			this.id = id;
			this.name = name;
			this.type = type;
		}
	}

	private static final List<Show> SHOWS = new ArrayList<>();

	/** Prisoners by their UUID, to the cellblock they're held in. */
	private static final Map<UUID, Colony.Building> HELD = new HashMap<>();
	private static final Random RANDOM = new Random();

	private Prison() {
	}

	// ---------------------------------------------------------------- bookkeeping

	static void index(List<Colony> colonies) {
		HELD.clear();
		for (Colony c : colonies) {
			for (Colony.Building b : c.buildings) {
				for (Prisoner p : b.prisoners.values()) {
					HELD.put(p.id(), b);
				}
			}
		}
	}

	static void forget() {
		HELD.clear();
		SHOWS.clear();
	}

	static boolean isHeld(UUID id) {
		return HELD.containsKey(id);
	}

	static boolean isPrisoner(@Nullable Entity entity) {
		return entity != null && (HELD.containsKey(entity.getUUID()) || entity.hasAttached(ColonycraftMod.PRISONER));
	}

	/** Which cell of this cellblock has its holding block here, or -1. */
	static int cellAt(Colony.Building b, BlockPos pos) {
		if (b.type != BuildingType.CELLBLOCK) {
			return -1;
		}
		for (int cell = 0; cell < BuildingType.cells(b.tier); cell++) {
			if (b.world(BuildingType.holding(cell)).equals(pos)) {
				return cell;
			}
		}
		return -1;
	}

	private static void free(Colony.Building b, int cell) {
		Prisoner p = b.prisoners.remove(cell);
		if (p != null) {
			HELD.remove(p.id());
		}
		Colonies.markDirty();
	}

	/**
	 * The prisoner in this cell, or null if it's empty. A prisoner who isn't there although the cell is loaded
	 * (e.g. the server went to Peaceful, which clears out every monster) has escaped, and the cell is freed.
	 */
	static @Nullable Prisoner inmate(ServerLevel level, Colony.Building b, int cell) {
		Prisoner p = b.prisoners.get(cell);
		if (p == null) {
			return null;
		}
		BlockPos spot = b.world(BuildingType.cellSpot(cell));
		if (level.isPositionEntityTicking(spot) && level.getEntity(p.id()) == null) {
			free(b, cell);
			tellOwner(b, Component.literal(p.name() + " is gone from cell " + (cell + 1) + " of " + b.colony.name + ". Escaped, somehow.")
				.withStyle(ChatFormatting.RED));
			return null;
		}
		return p;
	}

	// ---------------------------------------------------------------- the shackles

	static ItemStack emptyShackles() {
		ItemStack stack = base();
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, EMPTY);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Shackles").withStyle(ChatFormatting.GRAY));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Blueprints.text("Beat an illager below " + Math.round(WEAK * 100) + "% health,", ChatFormatting.GRAY),
			Blueprints.text("then right-click them to put them in irons.", ChatFormatting.GRAY),
			Blueprints.text("Pillagers, vindicators, evokers and illusioners.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	static ItemStack fullShackles(String type, String name, CompoundTag captive) {
		ItemStack stack = base();
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, FULL);
		tag.putString(TYPE, type);
		tag.putString(NAME, name);
		tag.put(CAPTIVE, captive);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Shackles (" + name + ")").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Blueprints.text("Holds: " + name, ChatFormatting.WHITE),
			Blueprints.text("Put them in a cellblock's holding block", ChatFormatting.GRAY),
			Blueprints.text("(the vault by each cell) to lock them up.", ChatFormatting.GRAY),
			Blueprints.text("Rattling. And muttering.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A bundle that holds no items (like the burlap sack), drawn as an iron chain. */
	private static ItemStack base() {
		ItemStack stack = new ItemStack(Items.BUNDLE);
		stack.remove(DataComponents.BUNDLE_CONTENTS);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_MODEL, chainModel());
		return stack;
	}

	/** The chain was renamed iron_chain in the copper update; use whichever this version has. */
	static Identifier chainModel() {
		Identifier iron = Identifier.fromNamespaceAndPath("minecraft", "iron_chain");
		return BuiltInRegistries.ITEM.containsKey(iron) ? iron : Identifier.fromNamespaceAndPath("minecraft", "chain");
	}

	private static CompoundTag data(ItemStack stack) {
		if (stack.isEmpty()) {
			return new CompoundTag();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	static boolean isShackles(ItemStack stack) {
		return stack.is(Items.BUNDLE) && !data(stack).getStringOr(KEY, "").isEmpty();
	}

	static boolean isFull(ItemStack stack) {
		return stack.is(Items.BUNDLE) && FULL.equals(data(stack).getStringOr(KEY, ""));
	}

	static String type(ItemStack stack) {
		return data(stack).getStringOr(TYPE, "");
	}

	static String name(ItemStack stack) {
		return data(stack).getStringOr(NAME, "Someone");
	}

	static CompoundTag captive(ItemStack stack) {
		return data(stack).getCompoundOrEmpty(CAPTIVE);
	}

	// ---------------------------------------------------------------- shackling

	static String typeId(Entity entity) {
		return EntityType.getKey(entity.getType()).toString();
	}

	static boolean fits(Entity entity) {
		return BOUNTY.containsKey(typeId(entity));
	}

	/** "a vindicator", "an evoker". */
	static String kind(String type) {
		String noun = type.substring(type.indexOf(':') + 1).replace('_', ' ');
		return ("aeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
	}

	/** Right-clicking a mob with shackles in hand. */
	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (entity.isRemoved()) {
			return InteractionResult.PASS; // the second packet of the click that just shackled them
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!fits(entity)) {
			if (entity instanceof LivingEntity) {
				actionBar(player, "Shackles are for illagers: pillagers, vindicators, evokers and illusioners.");
			}
			return InteractionResult.PASS;
		}
		if (isPrisoner(entity)) {
			actionBar(player, "Already locked up. Use the holding block by their cell.");
			return InteractionResult.SUCCESS;
		}
		if (isFull(stack)) {
			actionBar(player, "These shackles are taken. Lock " + name(stack) + " up first.");
			return InteractionResult.SUCCESS;
		}
		if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof LivingEntity mob) || !mob.isAlive()) {
			return InteractionResult.PASS;
		}
		if (!player.isCreative() && !weakEnough(mob)) {
			actionBar(player, entity.getName().getString() + " is still full of fight. Knock them below "
				+ Math.round(WEAK * 100) + "% health first.");
			return InteractionResult.SUCCESS;
		}
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		ItemStack banner = mob.getItemBySlot(EquipmentSlot.HEAD).copy();
		ItemStack shackles = capture(mob);
		player.setItemInHand(hand, shackles);
		if (!banner.isEmpty()) {
			Blueprints.give(player, banner); // a captain's banner: a trophy for the office wall
		}
		Cmd.sound(level, "minecraft:block.chain.place", x, y, z, 1f, 0.8f);
		Cmd.sound(level, "minecraft:entity.vindicator.hurt", x, y, z, 0.8f, 1.3f);
		Cmd.particles(level, "minecraft:crit", x, y + 1, z, 0.3, 0.1, 16);
		String name = name(shackles);
		player.sendSystemMessage(Component.literal(name).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(SHACKLED[RANDOM.nextInt(SHACKLED.length)]).withStyle(ChatFormatting.GRAY)));
		if (!banner.isEmpty()) {
			player.sendSystemMessage(Component.literal("You confiscated their captain's banner. No Bad Omen for you.").withStyle(ChatFormatting.YELLOW));
		}
		ColonycraftMod.LOG.info("{} shackled {} ({}) at {} {} {}", player.getName().getString(), name, typeId(entity), (int) x, (int) y, (int) z);
		return InteractionResult.SUCCESS;
	}

	static boolean weakEnough(LivingEntity mob) {
		return mob.getHealth() <= mob.getMaxHealth() * WEAK;
	}

	/**
	 * Takes an illager out of the world and into a pair of full shackles. Their weapons and a captain's banner
	 * are confiscated first (so executing them doesn't hand out Bad Omen), and they get a name if they had none.
	 */
	static ItemStack capture(LivingEntity mob) {
		String type = typeId(mob);
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		if (!mob.hasCustomName()) {
			String noun = type.substring(type.indexOf(':') + 1);
			mob.setCustomName(Component.literal(NAMES[RANDOM.nextInt(NAMES.length)] + " the " + Character.toUpperCase(noun.charAt(0)) + noun.substring(1)));
		}
		String name = mob.getName().getString();
		CompoundTag captive = save(mob);
		captive.putBoolean("PatrolLeader", false);
		captive.putBoolean("Patrolling", false);
		mob.stopRiding();
		mob.ejectPassengers();
		mob.discard();
		return fullShackles(type, name, captive);
	}

	/** The entity's full saved data, minus what must not come along. */
	private static CompoundTag save(Entity entity) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess());
		entity.saveWithoutId(out);
		CompoundTag tag = out.buildResult();
		for (String key : STRIP) {
			tag.remove(key);
		}
		tag.putBoolean("PersistenceRequired", true);
		return tag;
	}

	// ---------------------------------------------------------------- the cells

	/** Why these shackles can't go into this cell, or null if they can. */
	static @Nullable String whyNotLock(ServerLevel level, Colony.Building b, int cell, ItemStack shackles) {
		if (cell < 0 || cell >= BuildingType.cells(b.tier)) {
			return "That cell is still bricked up. Upgrade the cellblock.";
		}
		if (!isFull(shackles)) {
			return isShackles(shackles) ? "Those shackles are empty. Catch an illager first." : "Only shackles go in a holding block.";
		}
		if (inmate(level, b, cell) != null) {
			return "That cell is taken. One prisoner per cell; this isn't a party.";
		}
		if (!level.isLoaded(b.world(BuildingType.cellSpot(cell)))) {
			return "Go a bit closer to that cell first.";
		}
		return null;
	}

	/**
	 * Puts the prisoner from these shackles into the cell: the real mob, standing at the back of the cell facing the
	 * bars, without AI (no moving, no fighting, no spells, no raids), invulnerable and without loot. Null if it worked.
	 */
	static @Nullable String lockUp(ServerLevel level, Colony.Building b, int cell, ItemStack shackles) {
		String why = whyNotLock(level, b, cell, shackles);
		if (why != null) {
			return why;
		}
		String type = type(shackles);
		String name = name(shackles);
		CompoundTag tag = captive(shackles).copy();
		tag.putBoolean("NoAI", true);
		tag.putBoolean("PersistenceRequired", true);
		tag.putBoolean("Invulnerable", true);
		tag.putBoolean("CanJoinRaid", false);
		tag.putBoolean("PatrolLeader", false);
		tag.putBoolean("Patrolling", false);
		tag.putBoolean("CustomNameVisible", true);
		tag.putString("DeathLootTable", "minecraft:empty");
		String saved = tag.toString();
		String body = saved.length() > 2 ? "," + saved.substring(1, saved.length() - 1) : "";
		BlockPos spot = b.world(BuildingType.cellSpot(cell));
		float yaw = yaw(b, cell);
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon " + type + " " + Cmd.pos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5)
			+ " {" + Cmd.uuidNbt(id) + ",Rotation:[" + Cmd.f(yaw) + "f,0f]" + body + "}");
		Entity prisoner = level.getEntity(id);
		if (prisoner == null) {
			ColonycraftMod.LOG.error("Could not lock a {} up in cell {} of {}", type, cell, b.colony.name);
			return "The prisoner won't go in. Try again.";
		}
		prisoner.setAttached(ColonycraftMod.PRISONER, true);
		prisoner.setYHeadRot(yaw);
		b.prisoners.put(cell, new Prisoner(id, name, type, level.getGameTime()));
		HELD.put(id, b);
		Colonies.markDirty();
		Cmd.sound(level, "minecraft:block.iron_door.close", spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1f, 0.7f);
		Cmd.sound(level, "minecraft:block.chain.break", spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1f, 0.8f);
		tellOwner(b, Component.literal(name).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(LOCKED[RANDOM.nextInt(LOCKED.length)]).withStyle(ChatFormatting.GRAY)));
		return null;
	}

	/** The way the prisoner faces: straight out through the bars, across the corridor. */
	private static float yaw(Colony.Building b, int cell) {
		BlockPos spot = BuildingType.cellSpot(cell);
		BlockPos out = b.world(spot.offset(-Integer.signum(spot.getX()), 0, 0)).subtract(b.world(spot));
		return (float) Math.toDegrees(Math.atan2(-out.getX(), out.getZ()));
	}

	/** Takes the prisoner out of the cell and back into a pair of full shackles, or null if there's nobody there. */
	static @Nullable ItemStack takeOut(ServerLevel level, Colony.Building b, int cell) {
		Prisoner p = inmate(level, b, cell);
		if (p == null) {
			return null;
		}
		Entity prisoner = level.getEntity(p.id());
		if (prisoner == null) {
			return null; // not loaded: they stay put
		}
		free(b, cell);
		prisoner.removeAttached(ColonycraftMod.PRISONER);
		prisoner.setCustomName(Component.literal(p.name())); // without the day count
		CompoundTag captive = save(prisoner);
		prisoner.discard();
		BlockPos spot = b.world(BuildingType.cellSpot(cell));
		Cmd.sound(level, "minecraft:block.iron_door.open", spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1f, 0.8f);
		return fullShackles(p.type(), p.name(), captive);
	}

	/** Executes the prisoner in this cell and pays the bounty to the colony's owner. Returns the bounty, or -1 if the cell was empty. */
	static long execute(ServerLevel level, Colony.Building b, int cell) {
		Prisoner p = inmate(level, b, cell);
		if (p == null || level.getEntity(p.id()) == null) {
			return -1;
		}
		free(b, cell); // first, so the death isn't reported as a prisoner dying in their cell
		BlockPos spot = b.world(BuildingType.cellSpot(cell));
		double x = spot.getX() + 0.5;
		double z = spot.getZ() + 0.5;
		Cmd.sound(level, "minecraft:block.bell.use", x, spot.getY(), z, 1.5f, 0.5f);
		Cmd.sound(level, "minecraft:entity.player.attack.sweep", x, spot.getY(), z, 1f, 0.6f);
		Cmd.particles(level, "minecraft:soul", x, spot.getY() + 1, z, 0.3, 0.02, 20);
		Cmd.run(level, "kill " + p.id()); // gets through Invulnerable; no loot table, no weapons, so nothing drops
		long bounty = ironFist(b.colony, Bank.cents(BOUNTY.getOrDefault(p.type(), 0.0)));
		if (bounty > 0) {
			Bank.credit(b.colony.owner, b.colony.ownerName, bounty);
		}
		return bounty;
	}

	/** Governance (Skills mod): Iron Fist pays more for prisoners, and dealing with one is worth XP. */
	static long ironFist(Colony colony, long cents) {
		SkillsLink.xp(colony.owner, "governance", 25);
		return Math.round(cents * (1.0 + SkillsLink.bonus(colony.owner, "governance/iron_fist")));
	}

	// ---------------------------------------------------------------- ransom

	/** Full days this prisoner has been inside. */
	static long daysHeld(Prisoner p, long now) {
		return Math.max(0, now - p.since()) / Colonies.DAY;
	}

	/** What the illagers pay to get this prisoner back today: the bounty, plus a quarter of it per day held, up to 2.5 times. */
	static long ransomValue(String type, long days) {
		double factor = Math.min(RANSOM_CAP, 1 + RANSOM_PER_DAY * days);
		return Bank.cents(BOUNTY.getOrDefault(type, 0.0) * factor);
	}

	/**
	 * An illager envoy pays the ransom and takes the prisoner home (they leave the world, so the same illager can't
	 * be caught and ransomed over and over). Returns what was paid, or -1 if the cell was empty.
	 */
	static long ransom(ServerLevel level, Colony.Building b, int cell) {
		Prisoner p = inmate(level, b, cell);
		Entity prisoner = p == null ? null : level.getEntity(p.id());
		if (prisoner == null) {
			return -1;
		}
		long paid = ironFist(b.colony, ransomValue(p.type(), daysHeld(p, level.getGameTime())));
		free(b, cell);
		Cmd.sound(level, "minecraft:entity.evoker.celebrate", prisoner.getX(), prisoner.getY(), prisoner.getZ(), 1f, 1f);
		Cmd.sound(level, "minecraft:block.iron_door.open", prisoner.getX(), prisoner.getY(), prisoner.getZ(), 1f, 0.8f);
		Cmd.particles(level, "minecraft:poof", prisoner.getX(), prisoner.getY() + 1, prisoner.getZ(), 0.3, 0.02, 20);
		prisoner.discard();
		Bank.credit(b.colony.owner, b.colony.ownerName, paid);
		return paid;
	}

	// ---------------------------------------------------------------- public execution

	/** How much a public execution pays compared to the plain bounty, by scaffold tier: 2, 2.5, then 3 times. */
	static double publicFactor(int tier) {
		return 1.5 + 0.5 * Math.max(1, Math.min(BuildingType.MAX_TIER, tier));
	}

	static boolean busy(Colony.Building scaffold) {
		for (Show show : SHOWS) {
			if (show.scaffold == scaffold) {
				return true;
			}
		}
		return false;
	}

	/** The colony's best scaffold that's free right now, or null. */
	static @Nullable Colony.Building scaffold(Colony c) {
		Colony.Building best = null;
		for (Colony.Building b : c.buildings) {
			if (b.type == BuildingType.SCAFFOLD && !busy(b) && (best == null || b.tier > best.tier)) {
				best = b;
			}
		}
		return best;
	}

	/** Why this cellblock's prisoners can't be executed in public right now, or null if they can. */
	static @Nullable String whyNotPublic(ServerLevel level, Colony c) {
		boolean any = false;
		for (Colony.Building b : c.buildings) {
			any |= b.type == BuildingType.SCAFFOLD;
		}
		if (!any) {
			return "Build a Scaffold first. Justice wants an audience.";
		}
		Colony.Building scaffold = scaffold(c);
		if (scaffold == null) {
			return "The scaffold is busy. Wait your turn.";
		}
		if (!level.isPositionEntityTicking(scaffold.world(BuildingType.SCAFFOLD_SPOT))) {
			return "The scaffold is too far away. Get a bit closer to it.";
		}
		return null;
	}

	/**
	 * Marches the prisoner up the scaffold and announces it to the whole server. The bell counts down, and then
	 * the crowd gets what it came for. Pays the bounty times the scaffold's factor (bounty plus ticket sales)
	 * right away. Returns what was paid, or -1 if it can't happen.
	 */
	static long publicExecution(ServerLevel level, Colony.Building b, int cell) {
		Prisoner p = inmate(level, b, cell);
		Entity prisoner = p == null ? null : level.getEntity(p.id());
		if (prisoner == null || whyNotPublic(level, b.colony) != null) {
			return -1;
		}
		Colony.Building scaffold = scaffold(b.colony);
		free(b, cell);
		BlockPos spot = scaffold.world(BuildingType.SCAFFOLD_SPOT);
		BlockPos front = scaffold.world(BuildingType.SCAFFOLD_SPOT.offset(0, 0, -1)).subtract(spot);
		float yaw = (float) Math.toDegrees(Math.atan2(-front.getX(), front.getZ()));
		Cmd.run(level, "tp " + p.id() + " " + Cmd.pos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) + " " + Cmd.f(yaw) + " 0");
		prisoner.setYHeadRot(yaw);
		prisoner.setCustomName(Component.literal(p.name()).withStyle(ChatFormatting.DARK_RED));
		SHOWS.add(new Show(level, scaffold, p.id(), p.name(), p.type()));
		long paid = ironFist(b.colony, Bank.cents(BOUNTY.getOrDefault(p.type(), 0.0) * publicFactor(scaffold.tier)));
		Bank.credit(b.colony.owner, b.colony.ownerName, paid);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Hear ye! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(p.name() + " faces justice on the scaffold of " + b.colony.name + " ("
				+ spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "). Bring the kids.").withStyle(ChatFormatting.YELLOW)), false);
		Cmd.sound(level, "minecraft:block.bell.use", spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 2f, 0.7f);
		return paid;
	}

	/** Every tick: the bell tolls the countdown of every public execution, and the ones whose time is up happen. */
	static void tick() {
		for (Iterator<Show> it = SHOWS.iterator(); it.hasNext(); ) {
			Show show = it.next();
			show.left--;
			BlockPos spot = show.scaffold.world(BuildingType.SCAFFOLD_SPOT);
			if (show.left > 0 && show.left % 40 == 0) {
				Cmd.sound(show.level, "minecraft:block.bell.use", spot.getX() + 0.5, spot.getY() + 2, spot.getZ() + 0.5, 2f, 0.5f);
				Cmd.sound(show.level, "minecraft:entity.villager.ambient", spot.getX() + 0.5, spot.getY(), spot.getZ() - 2.5, 1f, 0.8f);
			}
			if (show.left <= 0) {
				it.remove();
				finish(show);
			}
		}
	}

	private static void finish(Show show) {
		BlockPos spot = show.scaffold.world(BuildingType.SCAFFOLD_SPOT);
		double x = spot.getX() + 0.5;
		double z = spot.getZ() + 0.5;
		Cmd.sound(show.level, "minecraft:entity.player.attack.sweep", x, spot.getY(), z, 1.5f, 0.6f);
		Cmd.particles(show.level, "minecraft:soul", x, spot.getY() + 1, z, 0.3, 0.02, 30);
		Cmd.run(show.level, "kill " + show.id);
		Cmd.sound(show.level, "minecraft:entity.villager.celebrate", x, spot.getY(), z, 2f, 1f);
		Cmd.particles(show.level, "minecraft:happy_villager", x, spot.getY(), z, 4, 0, 40);
		show.level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Justice is served in " + show.scaffold.colony.name + ". ")
			.withStyle(ChatFormatting.DARK_RED).append(Component.literal(show.name + epitaph(show.type) + " The crowd goes wild.")
				.withStyle(ChatFormatting.GRAY)), false);
	}

	/** Server stopping: the shows can't wait. */
	static void stop() {
		for (Show show : new ArrayList<>(SHOWS)) {
			finish(show);
		}
		SHOWS.clear();
	}

	/** The last words the town crier has for them. */
	static String epitaph(String type) {
		return switch (type) {
			case "minecraft:pillager" -> " won't be shooting at anyone's llamas again.";
			case "minecraft:vindicator" -> " has made their last swing.";
			case "minecraft:evoker" -> "'s fangs are permanently out of service.";
			case "minecraft:illusioner" -> " has done their final disappearing act.";
			default -> " has paid for their crimes.";
		};
	}

	/** A prisoner died in their cell some other way than by execution (/kill, the void...). */
	static void onDeath(Entity entity) {
		Colony.Building b = HELD.get(entity.getUUID());
		if (b == null) {
			return;
		}
		for (Map.Entry<Integer, Prisoner> e : new ArrayList<>(b.prisoners.entrySet())) {
			if (e.getValue().id().equals(entity.getUUID())) {
				free(b, e.getKey());
				tellOwner(b, Component.literal(e.getValue().name() + " died in cell " + (e.getKey() + 1) + " of " + b.colony.name + ". No bounty for that.")
					.withStyle(ChatFormatting.RED));
			}
		}
	}

	/**
	 * Every few seconds: prisoners who got shoved are put back where they stand, and their name tag counts the days
	 * they've done. Guards who have their eye on a prisoner look somewhere else.
	 */
	static void keep(ServerLevel level, Colony c) {
		long now = level.getGameTime();
		for (Colony.Building b : c.buildings) {
			if (b.prisoners.isEmpty()) {
				continue;
			}
			for (Map.Entry<Integer, Prisoner> e : b.prisoners.entrySet()) {
				Prisoner p = e.getValue();
				Entity prisoner = level.getEntity(p.id());
				if (prisoner == null) {
					continue;
				}
				BlockPos spot = b.world(BuildingType.cellSpot(e.getKey()));
				if (prisoner.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 0.5) {
					Cmd.run(level, "tp " + p.id() + " " + Cmd.pos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) + " "
						+ Cmd.f(yaw(b, e.getKey())) + " 0");
				}
				String tag = p.name() + " · day " + (Math.max(0, now - p.since()) / Colonies.DAY + 1);
				Component current = prisoner.getCustomName();
				if (current == null || !current.getString().equals(tag)) {
					prisoner.setCustomName(Component.literal(tag).withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	private static void tellOwner(Colony.Building b, Component message) {
		ServerPlayer owner = Colonies.owner(b.colony);
		if (owner != null) {
			owner.sendSystemMessage(message);
		}
	}

	static void actionBar(ServerPlayer player, String text) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
	}
}
