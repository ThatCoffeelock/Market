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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Every colony on the server, and everything that happens to them. */
public final class Colonies {
	/** Wage per worker per day, in ₥. */
	public static final double WAGE = 3;
	/** Price in ₥ to replace a worker who died. */
	public static final double REPLACE_PRICE = 100;
	/** Share of what you spent on a building that you get back when you demolish it. */
	public static final double REFUND = 0.5;
	/** One Minecraft day. */
	public static final int DAY = 24000;

	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	private static final String[] NAMES = {"Jan", "Piet", "Klaas", "Marieke", "Anouk", "Sem", "Fenna", "Joost", "Willem", "Grietje",
		"Bram", "Lotte", "Henk", "Truus", "Kees", "Femke", "Daan", "Saskia", "Gijs", "Mies"};
	private static final String[] COLONY_NAMES = {"New Amsterdam", "Port Greed", "Fort Moneybags", "Profitville", "Goldhaven",
		"Nieuw Rijk", "Coinsworth", "Taxmoor", "Dividend Bay", "Compound Interest"};

	private static final List<Colony> COLONIES = new ArrayList<>();
	private static final Map<UUID, Colony.Building> VILLAGERS = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final List<BuildJob> JOBS = new ArrayList<>();
	private static final List<Entity> ORPHANS = new ArrayList<>();
	private static final Random RANDOM = new Random();
	private static @Nullable MinecraftServer server;
	private static long clock;
	private static long ticks;
	private static boolean dirty;

	private record Pending(BuildingType type, BlockPos origin, int quarter, long until) {
	}

	private static final class BuildJob {
		final ServerLevel level;
		final List<BlockPos> positions = new ArrayList<>();
		final List<BlockState> states = new ArrayList<>();
		final Runnable done;
		int next;

		BuildJob(ServerLevel level, Runnable done) {
			this.level = level;
			this.done = done;
		}
	}

	private Colonies() {
	}

	// ---------------------------------------------------------------- lifecycle

	static void load(MinecraftServer s) {
		server = s;
		COLONIES.clear();
		VILLAGERS.clear();
		COLONIES.addAll(ColonyStore.load(s));
		clock = ColonyStore.clock;
		for (Colony c : COLONIES) {
			for (Colony.Building b : c.buildings) {
				for (UUID v : b.villagers) {
					if (v != null) {
						VILLAGERS.put(v, b);
					}
				}
			}
		}
		ColonycraftMod.LOG.info("Loaded {} colonies", COLONIES.size());
	}

	static void save() {
		if (server != null) {
			ColonyStore.save(server, COLONIES, clock);
			dirty = false;
		}
	}

	static void stop() {
		for (BuildJob job : new ArrayList<>(JOBS)) {
			finish(job); // don't leave half-built houses behind
		}
		JOBS.clear();
		save();
		server = null;
		PENDING.clear();
		ORPHANS.clear();
	}

	static void markDirty() {
		dirty = true;
	}

	static List<Colony> all() {
		return COLONIES;
	}

	static void tick(MinecraftServer s) {
		ticks++;
		if (!JOBS.isEmpty()) {
			BuildJob job = JOBS.get(0);
			int end = Math.min(job.positions.size(), job.next + 48);
			for (; job.next < end; job.next++) {
				job.level.setBlock(job.positions.get(job.next), job.states.get(job.next), QUIET);
			}
			if (job.next % 96 == 0) {
				BlockPos at = job.positions.get(Math.max(0, job.next - 1));
				Cmd.sound(job.level, "minecraft:block.wood.place", at.getX(), at.getY(), at.getZ(), 0.8f, 0.9f);
			}
			if (job.next >= job.positions.size()) {
				JOBS.remove(0);
				finish(job);
			}
		}
		if (!ORPHANS.isEmpty()) {
			for (Entity orphan : ORPHANS) {
				if (!orphan.isRemoved() && !VILLAGERS.containsKey(orphan.getUUID())) {
					orphan.discard();
				}
			}
			ORPHANS.clear();
		}
		if (++clock >= DAY) {
			clock = 0;
			payday();
		}
		if (ticks % 100 == 0) {
			keepVillagersHome();
		}
		if (ticks % 6000 == 0 && dirty) {
			save();
		}
	}

	static @Nullable ServerLevel level(Colony colony) {
		if (server == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (dim(level).equals(colony.dimension)) {
				return level;
			}
		}
		return null;
	}

	static String dim(Level level) {
		return level.dimension().toString();
	}

	// ---------------------------------------------------------------- geometry

	static BlockPos toWorld(BlockPos origin, int quarter, int lx, int ly, int lz) {
		int x;
		int z;
		switch (Math.floorMod(quarter, 4)) {
			case 1 -> { x = -lz; z = lx; }
			case 2 -> { x = -lx; z = -lz; }
			case 3 -> { x = lz; z = -lx; }
			default -> { x = lx; z = lz; }
		}
		return origin.offset(x, ly, z);
	}

	static BlockPos toLocal(BlockPos origin, int quarter, BlockPos world) {
		int dx = world.getX() - origin.getX();
		int dy = world.getY() - origin.getY();
		int dz = world.getZ() - origin.getZ();
		return switch (Math.floorMod(quarter, 4)) {
			case 1 -> new BlockPos(dz, dy, -dx);
			case 2 -> new BlockPos(-dx, dy, -dz);
			case 3 -> new BlockPos(-dz, dy, dx);
			default -> new BlockPos(dx, dy, dz);
		};
	}

	static Rotation rotation(int quarter) {
		return switch (Math.floorMod(quarter, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	// ---------------------------------------------------------------- finding things

	static @Nullable Colony colonyAt(Level level, BlockPos pos) {
		String dim = dim(level);
		for (Colony c : COLONIES) {
			if (c.dimension.equals(dim) && c.claims(pos)) {
				return c;
			}
		}
		return null;
	}

	static @Nullable Colony.Building buildingAt(Level level, BlockPos pos) {
		String dim = dim(level);
		for (Colony c : COLONIES) {
			if (!c.dimension.equals(dim) || !c.claims(pos)) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				if (b.contains(pos)) {
					return b;
				}
			}
		}
		return null;
	}

	static List<Colony> ownedBy(UUID player) {
		List<Colony> list = new ArrayList<>();
		for (Colony c : COLONIES) {
			if (c.owner.equals(player)) {
				list.add(c);
			}
		}
		return list;
	}

	static @Nullable Colony.Building ofVillager(UUID villager) {
		return VILLAGERS.get(villager);
	}

	// ---------------------------------------------------------------- placing

	/** Why this building can't go here, or null if it can. */
	static @Nullable String whyNot(ServerPlayer player, ServerLevel level, BuildingType type, BlockPos origin, int quarter) {
		int h = type.half;
		BlockPos[] corners = {toWorld(origin, quarter, -h, 0, -h), toWorld(origin, quarter, h, 0, h),
			toWorld(origin, quarter, -h, 0, h), toWorld(origin, quarter, h, 0, -h)};
		String dim = dim(level);
		Colony target = null;
		if (type == BuildingType.TOWN_HALL) {
			for (Colony c : COLONIES) {
				BlockPos centre = c.centre();
				if (c.dimension.equals(dim) && Math.abs(centre.getX() - origin.getX()) <= c.radius() + 40
					&& Math.abs(centre.getZ() - origin.getZ()) <= c.radius() + 40) {
					return "Too close to " + c.ownerName + "'s colony " + c.name + ". Colonies need room to grow.";
				}
			}
		} else {
			for (Colony c : ownedBy(player.getUUID())) {
				if (c.dimension.equals(dim) && c.claims(corners[0]) && c.claims(corners[1]) && c.claims(corners[2]) && c.claims(corners[3])) {
					target = c;
					break;
				}
			}
			if (target == null) {
				return "Build it on your own colony's land, near one of your Town Halls.";
			}
			if (target.buildings.size() - 1 >= target.maxBuildings()) {
				return target.name + " is full (" + target.maxBuildings() + " buildings). Upgrade the Town Hall for more room.";
			}
			if (target.housing() + type.housing(1) < target.jobs() + type.workers(1)) {
				return "Not enough beds for " + type.workers(1) + " more workers. Build a Residence first.";
			}
		}
		// no overlapping other buildings (leaving a one-block path between them)
		for (Colony c : COLONIES) {
			if (!c.dimension.equals(dim)) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				int reach = b.type.half + h + 1;
				if (Math.abs(b.origin.getX() - origin.getX()) <= reach && Math.abs(b.origin.getZ() - origin.getZ()) <= reach) {
					return "Too close to the " + b.type.displayName + ". Leave a path between buildings.";
				}
			}
		}
		// on land, not in a lake
		int wet = 0;
		int total = 0;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				BlockPos floor = toWorld(origin, quarter, x, 0, z);
				if (!level.isLoaded(floor)) {
					return "Part of that spot isn't loaded. Get closer.";
				}
				total++;
				if (!level.getFluidState(floor).isEmpty() || !level.getFluidState(floor.above()).isEmpty()) {
					wet++;
				}
			}
		}
		if (wet * 3 > total) {
			return "Too much water there. Build on dry land.";
		}
		return null;
	}

	/** Right-clicking the ground with a blueprint: first click previews, second click builds. */
	static InteractionResult useBlueprint(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		BuildingType type = Blueprints.type(held);
		if (type == null) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		BlockPos origin = hit.getBlockPos();
		if (level.getBlockState(origin).canBeReplaced()) {
			origin = origin.below();
		}
		int quarter = Math.floorMod(Math.round(player.getYRot() / 90f), 4);
		String why = whyNot(player, level, type, origin, quarter);
		if (why != null) {
			player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
			outline(level, type, origin, quarter, "minecraft:angry_villager");
			return InteractionResult.SUCCESS;
		}
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.type != type || !pending.origin.equals(origin) || pending.quarter != quarter || pending.until < ticks) {
			PENDING.put(player.getUUID(), new Pending(type, origin, quarter, ticks + 200));
			outline(level, type, origin, quarter, "minecraft:happy_villager");
			player.sendSystemMessage(Component.literal("That's where the " + type.displayName + " will go (the door faces you). ")
				.withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("Right-click the same spot again to build.").withStyle(ChatFormatting.GOLD)));
			return InteractionResult.SUCCESS;
		}
		PENDING.remove(player.getUUID());

		Colony colony;
		if (type == BuildingType.TOWN_HALL) {
			String name = Blueprints.customName(held);
			colony = new Colony(UUID.randomUUID().toString().substring(0, 8),
				name != null && !name.isBlank() ? name : COLONY_NAMES[RANDOM.nextInt(COLONY_NAMES.length)],
				player.getUUID(), player.getName().getString(), dim(level));
			COLONIES.add(colony);
		} else {
			colony = colonyAt(level, origin);
			if (colony == null) {
				return InteractionResult.SUCCESS;
			}
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		construct(level, colony, type, origin, quarter, Bank.cents(type.price), false);
		player.sendSystemMessage(Component.literal(type == BuildingType.TOWN_HALL
			? "Founded " + colony.name + "! The Town Hall is going up. Its counter (the lectern) is where you buy everything else."
			: "Construction of the " + type.displayName + " has started. Workers move in when it's done.").withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS;
	}

	private static void outline(ServerLevel level, BuildingType type, BlockPos origin, int quarter, String particle) {
		int h = type.half;
		for (int i = -h; i <= h; i++) {
			for (int[] p : new int[][] {{i, -h}, {i, h}, {-h, i}, {h, i}}) {
				BlockPos at = toWorld(origin, quarter, p[0], 1, p[1]);
				Cmd.particles(level, particle, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 0.1, 0, 2);
			}
		}
		BlockPos door = toWorld(origin, quarter, 0, 1, -h - 1);
		Cmd.particles(level, "minecraft:flame", door.getX() + 0.5, door.getY() + 0.3, door.getZ() + 0.5, 0.1, 0, 6);
	}

	/** Builds a building (layer by layer, or all at once) and moves its workers in when it's done. */
	static Colony.Building construct(ServerLevel level, Colony colony, BuildingType type, BlockPos origin, int quarter, long paid, boolean instant) {
		Colony.Building b = new Colony.Building(UUID.randomUUID().toString().substring(0, 8), type, origin, quarter);
		b.spent = paid;
		b.colony = colony;
		b.resizeStorage();
		colony.buildings.add(b);
		markDirty();

		BuildJob job = new BuildJob(level, () -> movedIn(level, b));
		int h = type.half;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		for (int y = type.height + 1; y >= 1; y--) {
			for (int x = -h; x <= h; x++) {
				for (int z = -h; z <= h; z++) {
					BlockPos pos = toWorld(origin, quarter, x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						job.positions.add(pos);
						job.states.add(air);
					}
				}
			}
		}
		for (int y = -3; y <= -1; y++) {
			for (int x = -h; x <= h; x++) {
				for (int z = -h; z <= h; z++) {
					BlockPos pos = toWorld(origin, quarter, x, y, z);
					if (level.getBlockState(pos).canBeReplaced()) {
						job.positions.add(pos);
						job.states.add(dirt);
					}
				}
			}
		}
		Rotation rot = rotation(quarter);
		List<Map.Entry<BlockPos, BlockState>> plan = new ArrayList<>(type.plan().entrySet());
		plan.sort((a, c) -> Integer.compare(a.getKey().getY(), c.getKey().getY()));
		for (Map.Entry<BlockPos, BlockState> e : plan) {
			BlockPos l = e.getKey();
			job.positions.add(toWorld(origin, quarter, l.getX(), l.getY(), l.getZ()));
			job.states.add(e.getValue().rotate(rot));
		}
		if (instant) {
			for (int i = 0; i < job.positions.size(); i++) {
				level.setBlock(job.positions.get(i), job.states.get(i), QUIET);
			}
			finish(job);
		} else {
			JOBS.add(job);
		}
		return b;
	}

	private static void finish(BuildJob job) {
		for (int i = job.next; i < job.positions.size(); i++) {
			job.level.setBlock(job.positions.get(i), job.states.get(i), QUIET);
		}
		job.next = job.positions.size();
		// let fences, panes, beds and doors connect to their neighbours
		for (int i = 0; i < job.positions.size(); i++) {
			BlockState placed = job.level.getBlockState(job.positions.get(i));
			if (placed.isAir() || !placed.getFluidState().isEmpty()) {
				continue;
			}
			BlockState shaped = Block.updateFromNeighbourShapes(placed, job.level, job.positions.get(i));
			if (shaped != placed) {
				job.level.setBlock(job.positions.get(i), shaped, QUIET);
			}
		}
		job.done.run();
	}

	private static void movedIn(ServerLevel level, Colony.Building b) {
		while (b.villagers.size() < b.type.workers(b.tier)) {
			b.villagers.add(spawnVillager(level, b));
		}
		BlockPos at = b.world(b.type.home());
		Cmd.sound(level, "minecraft:entity.player.levelup", at.getX(), at.getY(), at.getZ(), 1f, 1.2f);
		Cmd.particles(level, "minecraft:happy_villager", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 2, 0, 30);
		markDirty();
	}

	// ---------------------------------------------------------------- villagers

	private static String job(BuildingType type) {
		return switch (type) {
			case TOWN_HALL -> "Mayor";
			case FARM -> "Farmer";
			case LUMBER_CAMP -> "Lumberjack";
			case MINE -> "Miner";
			case WORKSHOP -> "Smith";
			case STOREHOUSE -> "Storekeeper";
			case RESIDENCE -> "Resident";
		};
	}

	static @Nullable UUID spawnVillager(ServerLevel level, Colony.Building b) {
		BlockPos home = b.world(b.type.home());
		if (!level.isLoaded(home)) {
			return null;
		}
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon minecraft:villager " + Cmd.pos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5)
			+ " {" + Cmd.uuidNbt(id) + ",PersistenceRequired:1b,Tags:[\"colonycraft\"]}");
		Entity villager = level.getEntity(id);
		if (villager == null) {
			ColonycraftMod.LOG.error("Could not hire a villager for the {} in {}", b.type.id, b.colony.name);
			return null;
		}
		villager.setAttached(ColonycraftMod.WORKER, true);
		villager.setCustomName(Component.literal(NAMES[RANDOM.nextInt(NAMES.length)] + " the " + job(b.type)));
		VILLAGERS.put(id, b);
		return id;
	}

	/** Workers wander, but not too far from their building. */
	private static void keepVillagersHome() {
		for (Colony c : COLONIES) {
			ServerLevel level = level(c);
			if (level == null) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				BlockPos home = b.world(b.type.home());
				double max = (b.type.half + 8) * (b.type.half + 8);
				for (UUID v : b.villagers) {
					if (v == null) {
						continue;
					}
					Entity villager = level.getEntity(v);
					if (villager != null && villager.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > max) {
						Cmd.run(level, "tp " + v + " " + Cmd.pos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5));
					}
				}
			}
		}
	}

	static void onVillagerDeath(Entity entity) {
		Colony.Building b = VILLAGERS.remove(entity.getUUID());
		if (b == null) {
			return;
		}
		int i = b.villagers.indexOf(entity.getUUID());
		if (i >= 0) {
			b.villagers.set(i, null);
		}
		markDirty();
		ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(b.colony.owner);
		if (owner != null) {
			owner.sendSystemMessage(Component.literal(entity.getName().getString() + " of " + b.colony.name + " has died. ")
				.withStyle(ChatFormatting.RED)
				.append(Component.literal("You can hire a replacement at the Town Hall.").withStyle(ChatFormatting.GRAY)));
		}
	}

	static void onEntityLoad(Entity entity) {
		if (entity.hasAttached(ColonycraftMod.WORKER) && !VILLAGERS.containsKey(entity.getUUID())) {
			ORPHANS.add(entity); // e.g. the worker of a building that was demolished while they were out of range
		}
	}

	// ---------------------------------------------------------------- Town Hall actions

	/** Pays for and hires replacements for everyone who died in this building. Returns the number hired. */
	static int replaceDead(ServerLevel level, Colony.Building b) {
		int hired = 0;
		for (int i = 0; i < b.villagers.size(); i++) {
			if (b.villagers.get(i) == null) {
				UUID id = spawnVillager(level, b);
				if (id != null) {
					b.villagers.set(i, id);
					hired++;
				}
			}
		}
		markDirty();
		return hired;
	}

	/** Why this building can't be upgraded, or null if it can. */
	static @Nullable String whyNoUpgrade(Colony.Building b) {
		if (b.tier >= BuildingType.MAX_TIER) {
			return "Already at the top tier.";
		}
		int extraJobs = b.type.workers(b.tier + 1) - b.type.workers(b.tier);
		int extraBeds = b.type.housing(b.tier + 1) - b.type.housing(b.tier);
		if (b.colony.housing() + extraBeds < b.colony.jobs() + extraJobs) {
			return "Not enough beds for " + extraJobs + " more workers. Build or upgrade a Residence first.";
		}
		return null;
	}

	static void upgrade(ServerLevel level, Colony.Building b, long paid) {
		b.tier++;
		b.spent += paid;
		b.resizeStorage();
		while (b.villagers.size() < b.type.workers(b.tier)) {
			b.villagers.add(spawnVillager(level, b));
		}
		BlockPos at = b.world(b.type.home());
		Cmd.sound(level, "minecraft:block.anvil.use", at.getX(), at.getY(), at.getZ(), 1f, 1.2f);
		Cmd.particles(level, "minecraft:totem_of_undying", at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 2, 0.3, 40);
		markDirty();
	}

	/** Why this building can't be demolished, or null if it can. */
	static @Nullable String whyNoDemolish(Colony.Building b) {
		if (b.type == BuildingType.TOWN_HALL && b.colony.buildings.size() > 1) {
			return "Demolish every other building first. The Town Hall goes last (that disbands the colony).";
		}
		if (b.type == BuildingType.STOREHOUSE && !b.storage.isEmpty()) {
			return "Empty the storehouse first.";
		}
		if (b.type.housing(b.tier) > 0 && b.colony.housing() - b.type.housing(b.tier) < b.colony.jobs() - b.villagers.size()) {
			return "Your workers would have nowhere to sleep. Demolish some workplaces first.";
		}
		return null;
	}

	/** Knocks it down, sends the workers away and refunds half of what it cost. Returns the refund. */
	static long demolish(ServerLevel level, Colony.Building b) {
		Colony colony = b.colony;
		for (UUID v : b.villagers) {
			if (v != null) {
				VILLAGERS.remove(v);
				Entity villager = level.getEntity(v);
				if (villager != null) {
					villager.discard();
				}
			}
		}
		int h = b.type.half;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState ground = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int y = b.type.height + 1; y >= 0; y--) {
			for (int x = -h; x <= h; x++) {
				for (int z = -h; z <= h; z++) {
					BlockPos pos = toWorld(b.origin, b.quarter, x, y, z);
					level.removeBlockEntity(pos);
					level.setBlock(pos, y == 0 ? ground : air, QUIET);
				}
			}
		}
		BlockPos at = b.world(b.type.home());
		Cmd.particles(level, "minecraft:poof", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 3, 0.05, 60);
		Cmd.sound(level, "minecraft:entity.generic.explode", at.getX(), at.getY(), at.getZ(), 0.6f, 1.4f);
		colony.buildings.remove(b);
		if (colony.buildings.isEmpty()) {
			COLONIES.remove(colony);
		}
		long refund = Math.round(b.spent * REFUND);
		Bank.credit(colony.owner, colony.ownerName, refund);
		markDirty();
		return refund;
	}

	// ---------------------------------------------------------------- payday

	/** Once a day: pay wages, gather, process in workshops, auto-sell. */
	static void payday() {
		for (Colony c : new ArrayList<>(COLONIES)) {
			try {
				payday(c);
			} catch (RuntimeException e) {
				ColonycraftMod.LOG.error("Payday failed for colony {}", c.name, e);
			}
		}
		markDirty();
		save();
	}

	static void payday(Colony c) {
		long wages = c.dailyWages();
		if (wages > 0 && !Bank.charge(c.owner, wages)) {
			if (!c.striking && server != null) {
				ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
				if (owner != null) {
					owner.sendSystemMessage(Component.literal("The workers of " + c.name + " are on strike: you can't pay today's wages (")
						.withStyle(ChatFormatting.RED).append(Bank.text(wages)).append(Component.literal("). Nothing gets gathered until you can.")
							.withStyle(ChatFormatting.RED)));
				}
			}
			c.striking = true;
			return;
		}
		c.striking = false;

		List<Colony.Building> stores = new ArrayList<>();
		for (Colony.Building b : c.buildings) {
			if (b.type == BuildingType.STOREHOUSE) {
				stores.add(b);
			}
		}
		// gather
		for (Colony.Building b : c.buildings) {
			for (ItemStack stack : Production.gather(b.type, b.tier, b.alive(), RANDOM)) {
				stow(stores, stack);
			}
		}
		// workshops turn raw goods into better ones
		for (Colony.Building b : c.buildings) {
			if (b.type != BuildingType.WORKSHOP || b.alive() == 0) {
				continue;
			}
			int budget = (int) (b.alive() * Production.WORKSHOP_PER_WORKER * Production.tierBonus(b.tier));
			for (Production.Recipe r : Production.WORKSHOP) {
				int batches = Math.min(budget / r.in(), count(stores, r.input()) / r.in());
				if (batches <= 0) {
					continue;
				}
				take(stores, r.input(), batches * r.in());
				int out = batches * r.out();
				while (out > 0) {
					int n = Math.min(out, r.output().getDefaultMaxStackSize());
					stow(stores, new ItemStack(r.output(), n));
					out -= n;
				}
				budget -= batches * r.in();
			}
		}
		// storehouses with autosell on sell everything the Market will buy
		long earned = 0;
		for (Colony.Building s : stores) {
			if (!s.autosell) {
				continue;
			}
			double bonus = s.tier >= 3 ? 1.1 : 1.0;
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				long value = Bank.sellValue(stack);
				if (value > 0) {
					earned += Math.round(value * bonus);
					s.storage.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		if (earned > 0) {
			Bank.credit(c.owner, c.ownerName, earned);
		}
	}

	private static void stow(List<Colony.Building> stores, ItemStack stack) {
		ItemStack rest = stack;
		for (Colony.Building s : stores) {
			rest = s.storage.addItem(rest);
			if (rest.isEmpty()) {
				return;
			}
		}
		// no room anywhere: it rots in the field
	}

	private static int count(List<Colony.Building> stores, net.minecraft.world.item.Item item) {
		int n = 0;
		for (Colony.Building s : stores) {
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				if (stack.is(item)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	private static void take(List<Colony.Building> stores, net.minecraft.world.item.Item item, int amount) {
		for (Colony.Building s : stores) {
			for (int i = 0; i < s.storage.getContainerSize() && amount > 0; i++) {
				ItemStack stack = s.storage.getItem(i);
				if (stack.is(item)) {
					int n = Math.min(amount, stack.getCount());
					stack.shrink(n);
					amount -= n;
					if (stack.isEmpty()) {
						s.storage.setItem(i, ItemStack.EMPTY);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- protection and clicks

	static boolean allowBreak(Player player, Level level, BlockPos pos) {
		Colony.Building b = buildingAt(level, pos);
		if (b == null || b.colony.isOwner(player.getUUID()) || player.isCreative()) {
			return true;
		}
		player.sendSystemMessage(Component.literal("That's part of " + b.colony.ownerName + "'s " + b.type.displayName + ". Hands off.")
			.withStyle(ChatFormatting.RED));
		return false;
	}

	static InteractionResult useBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Colony.Building b = buildingAt(level, pos);
		if (b == null) {
			return InteractionResult.PASS;
		}
		boolean counter = b.type == BuildingType.TOWN_HALL && pos.equals(b.world(BuildingType.COUNTER));
		boolean store = b.type == BuildingType.STOREHOUSE && level.getBlockState(pos).is(Blocks.BARREL);
		if (!counter && !store) {
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			if (!b.colony.isOwner(player.getUUID())) {
				player.sendSystemMessage(Component.literal("This is " + b.colony.name + ", " + b.colony.ownerName + "'s colony.")
					.withStyle(ChatFormatting.YELLOW));
			} else if (counter) {
				TownHallMenu.open(player, b.colony);
			} else {
				TownHallMenu.openStorage(player, b);
			}
		}
		return InteractionResult.SUCCESS;
	}

	static InteractionResult useVillager(ServerPlayer player, InteractionHand hand, Entity entity) {
		Colony.Building b = VILLAGERS.get(entity.getUUID());
		if (b == null) {
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			player.sendSystemMessage(Component.literal(entity.getName().getString()).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" works at the " + b.title() + " of " + b.colony.name + ". No time to trade, sorry.")
					.withStyle(ChatFormatting.GRAY)));
		}
		return InteractionResult.SUCCESS;
	}

	/** Test hook: forget everything (after the smoke test). */
	static void forgetAll() {
		for (Iterator<Colony> it = COLONIES.iterator(); it.hasNext(); ) {
			it.next();
			it.remove();
		}
		VILLAGERS.clear();
	}
}
