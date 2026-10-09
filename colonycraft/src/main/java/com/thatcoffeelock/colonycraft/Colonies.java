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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
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
	/** How far a watchtower archer can see, in blocks. */
	public static final double ARCHER_RANGE = 24;
	/** Share of a building's price that a repair and renovation costs. */
	public static final double REBUILD_SHARE = 0.1;

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
	/** Arrows fired by watchtower archers since the server started (the smoke test checks they shoot). */
	static int shots;

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
		Prison.index(COLONIES);
		RichesLink.publishPools();
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
		Prison.stop();
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
		Prison.tick();
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
				if (!orphan.isRemoved() && !VILLAGERS.containsKey(orphan.getUUID()) && !Prison.isHeld(orphan.getUUID())) {
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
		if (ticks % 30 == 0) {
			guardDuty();
		}
		if (ticks % 40 == 0) {
			stations();
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

	static @Nullable ServerPlayer owner(Colony colony) {
		return server == null ? null : server.getPlayerList().getPlayer(colony.owner);
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
		int d = type.depth;
		BlockPos[] corners = {toWorld(origin, quarter, -h, 0, -d), toWorld(origin, quarter, h, 0, d),
			toWorld(origin, quarter, -h, 0, d), toWorld(origin, quarter, h, 0, -d)};
		String dim = dim(level);
		Colony target = null;
		if (!type.available()) {
			return "The " + type.displayName + " needs the Havana mod for its tobacco.";
		}
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
			if (!type.fortification && target.slotsUsed() >= target.maxBuildings()) {
				return target.name + " is full (" + target.maxBuildings() + " buildings). Upgrade the Town Hall for more room.";
			}
			int sleepers = type.needsBeds() ? type.workers(1) : 0;
			if (target.housing() + type.housing(1) < target.jobs() + sleepers) {
				return "Not enough beds for " + sleepers + " more workers. Build a Residence first.";
			}
		}
		// no overlapping other buildings (leaving a one-block path between them; fortifications may touch)
		int ex = type.extentX(quarter);
		int ez = type.extentZ(quarter);
		for (Colony c : COLONIES) {
			if (!c.dimension.equals(dim)) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				int gap = type.fortification && b.type.fortification ? 0 : 1;
				if (Math.abs(b.origin.getX() - origin.getX()) <= b.extentX() + ex + gap
					&& Math.abs(b.origin.getZ() - origin.getZ()) <= b.extentZ() + ez + gap) {
					return gap == 0 ? "That overlaps the " + b.type.displayName + ". Line it up with its end."
						: "Too close to the " + b.type.displayName + ". Leave a path between buildings.";
				}
			}
		}
		// on land, not in a lake
		int wet = 0;
		int total = 0;
		for (int x = -h; x <= h; x++) {
			for (int z = -d; z <= d; z++) {
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
		if (wet * 3 > total && !type.waterfront()) {
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
		BlockPos clicked = origin;
		origin = snap(level, type, origin, quarter);
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
			String where = switch (type) {
				case WALL -> " (the side facing you is the inside, with the arches)";
				case WALL_STAIRS -> " (the steps go up the side facing you)";
				case GATEHOUSE -> " (the ladder is on the side facing you)";
				case HARBOR_OFFICE -> " (the door faces you, the pier points away from you: face the water)";
				case TRAIN_STATION -> " (the platform faces you, the track runs left to right)";
				default -> " (the door faces you)";
			};
			String snapped = clicked.equals(origin) ? "" : " Lined up with the one next to it.";
			player.sendSystemMessage(Component.literal("That's where the " + type.displayName + " will go" + where + "." + snapped + " ")
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
		int d = type.depth;
		List<int[]> rim = new ArrayList<>();
		for (int x = -h; x <= h; x++) {
			rim.add(new int[] {x, -d});
			rim.add(new int[] {x, d});
		}
		for (int z = -d; z <= d; z++) {
			rim.add(new int[] {-h, z});
			rim.add(new int[] {h, z});
		}
		for (int[] p : rim) {
			BlockPos at = toWorld(origin, quarter, p[0], 1, p[1]);
			Cmd.particles(level, particle, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 0.1, 0, 2);
		}
		BlockPos door = toWorld(origin, quarter, 0, 1, -d - 1);
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
		run(buildJob(level, b, () -> movedIn(level, b)), instant);
		return b;
	}

	private static void run(BuildJob job, boolean instant) {
		if (instant) {
			for (int i = 0; i < job.positions.size(); i++) {
				job.level.setBlock(job.positions.get(i), job.states.get(i), QUIET);
			}
			finish(job);
		} else {
			JOBS.add(job);
		}
	}

	/**
	 * Clears the footprint, shores up the ground underneath (dirt for buildings, a deep stone footing for
	 * fortifications, so walls don't float over dips) and puts the design down for the building's tier.
	 */
	private static BuildJob buildJob(ServerLevel level, Colony.Building b, Runnable done) {
		BuildJob job = new BuildJob(level, done);
		BuildingType type = b.type;
		int h = type.half;
		int d = type.depth;
		Map<BlockPos, BlockState> design = type.plan(b.tier);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		Rotation rot = rotation(b.quarter);
		for (int y = type.height + 1; y >= 1; y--) {
			for (int x = -h; x <= h; x++) {
				for (int z = -d; z <= d; z++) {
					BlockPos pos = toWorld(b.origin, b.quarter, x, y, z);
					BlockState now = level.getBlockState(pos);
					BlockState wanted = design.get(new BlockPos(x, y, z));
					// a block that's already the right one stays put: a warehouse core, its racks, a dock or a
					// station chest must never vanish for a moment while the building goes back up
					if (!now.isAir() && (wanted == null || !now.is(wanted.getBlock()))) {
						job.positions.add(pos);
						job.states.add(air);
					}
				}
			}
		}
		int footing = type.fortification ? 8 : 3;
		for (int y = -footing; y <= -1; y++) {
			for (int x = -h; x <= h; x++) {
				for (int z = -d; z <= d; z++) {
					BlockPos pos = toWorld(b.origin, b.quarter, x, y, z);
					BlockState floor = design.get(new BlockPos(x, 0, z));
					if (floor == null && type.waterfront()) {
						continue; // the water beside the pier stays water
					}
					if (level.getBlockState(pos).canBeReplaced()) {
						job.positions.add(pos);
						job.states.add(type.fortification && floor != null ? floor.rotate(rot) : dirt);
					}
				}
			}
		}
		List<Map.Entry<BlockPos, BlockState>> plan = new ArrayList<>(design.entrySet());
		plan.sort((a, c) -> Integer.compare(a.getKey().getY(), c.getKey().getY()));
		for (Map.Entry<BlockPos, BlockState> e : plan) {
			BlockPos l = e.getKey();
			BlockPos pos = toWorld(b.origin, b.quarter, l.getX(), l.getY(), l.getZ());
			BlockState state = e.getValue().rotate(rot);
			if (level.getBlockState(pos) == state && state.hasBlockEntity()) {
				continue; // already there, with its contents
			}
			job.positions.add(pos);
			job.states.add(state);
		}
		return job;
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
		hireMissing(level, b);
		furnish(level, b);
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
			case BARRACKS -> "Iron Guard";
			case WATCHTOWER -> "Archer";
			case WALL, WALL_STAIRS, WALL_TOWER, GATEHOUSE -> "Sentry";
			case FISHERY -> "Fisher";
			case TOBACCO_FARM -> "Planter";
			case HARBOR_OFFICE -> "Harbor Master";
			case TRAIN_STATION -> "Station Master";
			case CELLBLOCK -> "Jailer";
			case SCAFFOLD -> "Executioner";
			case TRADING_POST -> "Trader";
			case BANK -> "Clerk";
			case MUSEUM -> "Curator";
			case CHAPEL -> "Priest";
			case LIBRARY -> "Librarian";
			case RANCH -> "Rancher";
			case APIARY -> "Beekeeper";
			case FUEL_DEPOT -> "Depot Hand";
			case GUILDHOUSE -> "Guildmaster";
		};
	}

	/** Hires crew until every job of the building's tier is taken. */
	private static void hireMissing(ServerLevel level, Colony.Building b) {
		while (b.villagers.size() < b.type.workers(b.tier)) {
			b.villagers.add(spawnVillager(level, b, b.villagers.size()));
		}
	}

	/**
	 * Hires crew member number {@code index}: a villager, an iron golem for the barracks, or an archer
	 * for a watchtower (a villager with a crossbow who stands at a post and doesn't move or run away).
	 */
	static @Nullable UUID spawnVillager(ServerLevel level, Colony.Building b, int index) {
		return spawnVillager(level, b, index, NAMES[RANDOM.nextInt(NAMES.length)]);
	}

	/** Hires crew member number {@code index} under this name ("Bob" becomes "Bob the Farmer"). */
	static @Nullable UUID spawnVillager(ServerLevel level, Colony.Building b, int index, String name) {
		BlockPos at = b.world(b.type.station(index));
		if (!level.isLoaded(at)) {
			return null;
		}
		UUID id = UUID.randomUUID();
		String mob = b.type == BuildingType.BARRACKS ? "minecraft:iron_golem" : "minecraft:villager";
		String extra = switch (b.type) {
			case BARRACKS -> ",PlayerCreated:1b";
			case WATCHTOWER -> ",NoAI:1b";
			case TRADING_POST -> Street.traderNbt(index);
			default -> "";
		};
		Cmd.run(level, "summon " + mob + " " + Cmd.pos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
			+ " {" + Cmd.uuidNbt(id) + ",PersistenceRequired:1b" + extra + ",Tags:[\"colonycraft\"]}");
		Entity villager = level.getEntity(id);
		if (villager == null && b.type == BuildingType.TRADING_POST) {
			// the trades didn't load (a game version that writes them differently): hire the master, stock up after
			ColonycraftMod.LOG.warn("A master trader's wares didn't load on hiring; restocking them separately");
			Cmd.run(level, "summon " + mob + " " + Cmd.pos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) + " {" + Cmd.uuidNbt(id)
				+ ",PersistenceRequired:1b" + Street.traderNbt(index).replaceAll(",Offers:.*$", "") + ",Tags:[\"colonycraft\"]}");
			villager = level.getEntity(id);
		}
		if (villager == null) {
			ColonycraftMod.LOG.error("Could not hire a villager for the {} in {}", b.type.id, b.colony.name);
			return null;
		}
		villager.setAttached(ColonycraftMod.WORKER, true);
		villager.setCustomName(Component.literal(name + " the " + (b.type == BuildingType.TRADING_POST ? Street.title(index) : job(b.type))));
		if (b.type == BuildingType.WATCHTOWER && villager instanceof LivingEntity archer) {
			archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		}
		VILLAGERS.put(id, b);
		if (b.type == BuildingType.TRADING_POST) {
			Street.stock(level, villager, index);
		}
		return id;
	}

	/** Sends everyone back to where they belong, e.g. after a rebuild. */
	private static void settle(ServerLevel level, Colony.Building b) {
		for (int i = 0; i < b.villagers.size(); i++) {
			UUID v = b.villagers.get(i);
			if (v == null) {
				continue;
			}
			BlockPos at = b.world(b.type.station(i));
			Cmd.run(level, "tp " + v + " " + Cmd.pos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
		}
	}

	/**
	 * Workers wander, but not too far from their building. Archers stay at their posts. Iron golems
	 * patrol the whole colony, and stand still while the colony is on strike.
	 */
	private static void keepVillagersHome() {
		for (Colony c : COLONIES) {
			ServerLevel level = level(c);
			if (level == null) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				int reach = Math.max(b.half, b.depth) + 8;
				double max = reach * reach;
				for (int i = 0; i < b.villagers.size(); i++) {
					UUID v = b.villagers.get(i);
					if (v == null) {
						continue;
					}
					Entity villager = level.getEntity(v);
					if (villager == null) {
						continue;
					}
					BlockPos at = b.world(b.type.station(i));
					boolean away = switch (b.type) {
						case WATCHTOWER, TRADING_POST -> villager.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > 1;
						case BARRACKS -> !c.claims(villager.blockPosition());
						default -> villager.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > max;
					};
					if (away) {
						Cmd.run(level, "tp " + v + " " + Cmd.pos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
					}
					if (b.type == BuildingType.BARRACKS && villager instanceof Mob golem && golem.isNoAi() != c.striking) {
						golem.setNoAi(c.striking);
					}
					if (villager instanceof Mob guard && Prison.isPrisoner(guard.getTarget())) {
						guard.setTarget(null); // they've done their fighting; now they do their time
					}
				}
			}
			Prison.keep(level, c);
			Street.chapels(level, c);
		}
	}

	// ---------------------------------------------------------------- defence

	/** Watchtower archers shoot the nearest monster they can see on their side of the tower. */
	private static void guardDuty() {
		for (Colony c : COLONIES) {
			if (c.striking) {
				continue;
			}
			ServerLevel level = level(c);
			if (level == null) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				if (b.type != BuildingType.WATCHTOWER) {
					continue;
				}
				for (int i = 0; i < b.villagers.size(); i++) {
					UUID v = b.villagers.get(i);
					if (v == null || !(level.getEntity(v) instanceof LivingEntity archer) || !archer.isAlive()) {
						continue;
					}
					BlockPos post = BuildingType.post(i);
					BlockPos out = b.world(post.offset(BuildingType.lookout(i))).subtract(b.world(post));
					Entity target = null;
					double best = ARCHER_RANGE * ARCHER_RANGE;
					for (Entity e : level.getEntities(archer, archer.getBoundingBox().inflate(ARCHER_RANGE),
						m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER && !Prison.isPrisoner(m))) {
						double dx = e.getX() - archer.getX();
						double dz = e.getZ() - archer.getZ();
						if (dx * out.getX() + dz * out.getZ() < 0) {
							continue; // behind this archer: that side is someone else's
						}
						double dist = archer.distanceToSqr(e);
						if (dist < best && archer.hasLineOfSight(e)) {
							best = dist;
							target = e;
						}
					}
					if (target != null) {
						shoot(level, archer, target);
					}
				}
			}
		}
	}

	/** Turns the archer to the target and looses a crossbow bolt at it (an arrow nobody can pick up). */
	private static void shoot(ServerLevel level, Entity archer, Entity target) {
		Cmd.run(level, "tp " + archer.getUUID() + " " + Cmd.pos(archer.getX(), archer.getY(), archer.getZ())
			+ " facing entity " + target.getUUID() + " eyes");
		double ex = archer.getX();
		double ey = archer.getEyeY();
		double ez = archer.getZ();
		double dx = target.getX() - ex;
		double dz = target.getZ() - ez;
		double flat = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getY(0.5) - ey + flat * 0.06; // aim a little high: arrows drop
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 0.5) {
			return;
		}
		double speed = 2.6;
		double sx = flat > 0.1 ? ex + dx / flat * 0.8 : ex;
		double sz = flat > 0.1 ? ez + dz / flat * 0.8 : ez;
		Cmd.run(level, "summon minecraft:arrow " + Cmd.pos(sx, ey - 0.1, sz) + " {Motion:[" + Cmd.f(dx / length * speed) + "d,"
			+ Cmd.f(dy / length * speed) + "d," + Cmd.f(dz / length * speed) + "d],pickup:0b,damage:2.0d}");
		Cmd.sound(level, "minecraft:item.crossbow.shoot", ex, ey, ez, 1f, 1.1f);
		shots++;
	}

	/** Where a fortification should go so it lines up end to end with one that's already there (or where it was clicked). */
	static BlockPos snap(ServerLevel level, BuildingType type, BlockPos origin, int quarter) {
		if (!type.fortification) {
			return origin;
		}
		String dim = dim(level);
		BlockPos best = origin;
		int bestDistance = 4;
		for (Colony c : COLONIES) {
			if (!c.dimension.equals(dim)) {
				continue;
			}
			for (Colony.Building b : c.buildings) {
				if (!b.type.fortification) {
					continue;
				}
				for (boolean alongX : new boolean[] {true, false}) {
					if (!b.type.attachesAlongX(b.quarter, alongX) || !type.attachesAlongX(quarter, alongX)) {
						continue;
					}
					int reach = alongX ? b.type.extentX(b.quarter) + type.extentX(quarter) + 1
						: b.type.extentZ(b.quarter) + type.extentZ(quarter) + 1;
					int y = Math.abs(origin.getY() - b.origin.getY()) <= 2 ? b.origin.getY() : origin.getY();
					for (int sign : new int[] {-1, 1}) {
						BlockPos candidate = alongX ? new BlockPos(b.origin.getX() + sign * reach, y, b.origin.getZ())
							: new BlockPos(b.origin.getX(), y, b.origin.getZ() + sign * reach);
						int distance = Math.max(Math.abs(candidate.getX() - origin.getX()), Math.abs(candidate.getZ() - origin.getZ()));
						if (distance < bestDistance) {
							bestDistance = distance;
							best = candidate;
						}
					}
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- repair and renovate

	/** How many blocks of the design are missing or replaced by something else. Crops don't count: harvesting isn't damage. */
	static int damaged(ServerLevel level, Colony.Building b) {
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : b.type.plan(b.tier).entrySet()) {
			if (e.getValue().isAir() || e.getValue().is(BlockTags.CROPS)) {
				continue;
			}
			BlockPos at = b.world(e.getKey());
			if (level.isLoaded(at) && !level.getBlockState(at).is(e.getValue().getBlock())) {
				n++;
			}
		}
		return n;
	}

	static long rebuildPrice(Colony.Building b) {
		return Bank.cents(b.type.price * REBUILD_SHARE);
	}

	/**
	 * Why it can't be rebuilt right now, or null if it can. Rebuilding clears the footprint of the current design
	 * (which may be bigger than the one it was built to), so nothing else may stand there and containers must be
	 * empty, except the building's own (a station's barrels, say), which stay where they are with what's in them.
	 */
	static @Nullable String whyNoRebuild(ServerLevel level, Colony.Building b) {
		int ex = b.type.extentX(b.quarter);
		int ez = b.type.extentZ(b.quarter);
		for (Colony c : COLONIES) {
			if (!c.dimension.equals(b.colony.dimension)) {
				continue;
			}
			for (Colony.Building other : c.buildings) {
				if (other != b && Math.abs(other.origin.getX() - b.origin.getX()) <= other.extentX() + ex
					&& Math.abs(other.origin.getZ() - b.origin.getZ()) <= other.extentZ() + ez) {
					return "The " + other.type.displayName + " next to it is in the way: the current " + b.type.displayName
						+ " is bigger than this old one. Move or demolish the " + other.type.displayName + " first.";
				}
			}
		}
		Map<BlockPos, BlockState> design = b.type.plan(b.tier);
		for (int y = 0; y <= b.type.height + 1; y++) {
			for (int x = -b.type.half; x <= b.type.half; x++) {
				for (int z = -b.type.depth; z <= b.type.depth; z++) {
					BlockPos pos = toWorld(b.origin, b.quarter, x, y, z);
					if (!level.isLoaded(pos)) {
						return "Go a bit closer to that building first.";
					}
					BlockEntity entity = level.getBlockEntity(pos);
					BlockState wanted = design.get(new BlockPos(x, y, z));
					if (wanted != null && level.getBlockState(pos).is(wanted.getBlock())) {
						continue; // part of the building: it stays, contents and all
					}
					if (entity instanceof Container box && !box.isEmpty()) {
						return "Empty the " + level.getBlockState(pos).getBlock().getName().getString() + " at " + pos.getX() + ", "
							+ pos.getY() + ", " + pos.getZ() + " first. Rebuilding clears everything that isn't part of the building.";
					}
				}
			}
		}
		return null;
	}

	/** Puts the building back exactly as designed (for its tier), then sends its crew back to their places. */
	static void rebuild(ServerLevel level, Colony.Building b, boolean instant) {
		BuildJob job = buildJob(level, b, () -> {
			if (b.colony.buildings.contains(b)) {
				hireMissing(level, b);
				furnish(level, b);
				settle(level, b);
			}
		});
		b.resize(); // it's the current design now, footprint and all
		run(job, instant);
		markDirty();
	}

	/**
	 * Finishing touches a block state can't carry: the cellblock's holding blocks are vaults, and an empty loot
	 * table keeps them from showing off trial chamber loot when someone walks past. With the Warehouse mod, a
	 * storehouse's core and racks become a real warehouse and the harbor's pier gets its Loading Dock. A train
	 * station's chests get their station names, so Cargo Trains stop at them.
	 */
	static void furnish(ServerLevel level, Colony.Building b) {
		switch (b.type) {
			case CELLBLOCK -> {
				for (int cell = 0; cell < BuildingType.cells(b.tier); cell++) {
					BlockPos at = b.world(BuildingType.holding(cell));
					Cmd.run(level, "data merge block " + at.getX() + " " + at.getY() + " " + at.getZ() + " {config:{loot_table:\"minecraft:empty\"}}");
				}
			}
			case STOREHOUSE -> openWarehouse(level, b);
			case HARBOR_OFFICE -> {
				BlockPos dock = b.world(BuildingType.HARBOR_DOCK);
				if (level.getBlockState(dock).is(Blocks.LANTERN)) {
					WarehouseLink.dock(level, dock);
				}
			}
			case TRAIN_STATION -> {
				for (int t = 0; t < BuildingType.tracks(b.tier); t++) {
					name(level, b.world(BuildingType.PICKUPS[t]), "Pickup Station", "aqua");
					name(level, b.world(BuildingType.DROP_OFFS[t]), "Drop-off Station", "gold");
				}
			}
			default -> Street.furnish(level, b);
		}
	}

	private static void name(ServerLevel level, BlockPos at, String name, String color) {
		Cmd.run(level, "data merge block " + at.getX() + " " + at.getY() + " " + at.getZ()
			+ " {CustomName:{text:\"" + name + "\",color:\"" + color + "\",italic:false}}");
	}

	/**
	 * Registers the storehouse's core and racks with the Warehouse mod (if it's installed and the core is there),
	 * then moves whatever was still in the storehouse's own slots onto the shelves.
	 */
	static void openWarehouse(ServerLevel level, Colony.Building b) {
		if (!WarehouseLink.present()) {
			return;
		}
		BlockPos core = b.world(BuildingType.STORE_CORE);
		if (!level.isLoaded(core) || !level.getBlockState(core).is(Blocks.CARTOGRAPHY_TABLE)) {
			return;
		}
		String id = WarehouseLink.create(level, core, b.colony.owner, b.colony.ownerName, b.colony.name + " Stores");
		if (id == null) {
			return;
		}
		b.warehouse = id;
		for (int i = 0; i < BuildingType.racks(b.tier); i++) {
			BlockPos rack = b.world(BuildingType.rack(i));
			if (level.getBlockState(rack).is(Blocks.BARREL)) {
				WarehouseLink.rack(level, rack);
			}
		}
		migrate(b);
		markDirty();
	}

	/** The storehouse's warehouse, if it has a working one. */
	static @Nullable String warehouseOf(Colony.Building b) {
		return b.type == BuildingType.STOREHOUSE && WarehouseLink.exists(b.warehouse) ? b.warehouse : null;
	}

	/** Moves what's left in a storehouse's own slots (from before it was a warehouse) onto its shelves. */
	static void migrate(Colony.Building b) {
		String id = warehouseOf(b);
		if (id == null || b.storage.isEmpty()) {
			return;
		}
		for (int i = 0; i < b.storage.getContainerSize(); i++) {
			ItemStack stack = b.storage.getItem(i);
			if (!stack.isEmpty()) {
				WarehouseLink.deposit(id, stack);
				if (stack.isEmpty()) {
					b.storage.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		markDirty();
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
		if (entity.hasAttached(ColonycraftMod.PRISONER) && !Prison.isHeld(entity.getUUID())) {
			ORPHANS.add(entity); // a prisoner whose cell is gone
		}
	}

	// ---------------------------------------------------------------- Town Hall actions

	/** Pays for and hires replacements for everyone who died in this building. Returns the number hired. */
	static int replaceDead(ServerLevel level, Colony.Building b) {
		int hired = 0;
		for (int i = 0; i < b.villagers.size(); i++) {
			if (b.villagers.get(i) == null) {
				UUID id = spawnVillager(level, b, i);
				if (id != null) {
					b.villagers.set(i, id);
					hired++;
				}
			}
		}
		markDirty();
		return hired;
	}

	/** Does this building take villagers (so a villager from a Burlap Sack can fill an empty job)? */
	static boolean hiresVillagers(BuildingType type) {
		return type != BuildingType.BARRACKS;
	}

	/** Gives the first empty job to the villager from a sack. Returns true if they were hired. */
	static boolean hireFromSack(ServerLevel level, Colony.Building b, String name) {
		int slot = b.villagers.indexOf(null);
		if (slot < 0 || !hiresVillagers(b.type)) {
			return false;
		}
		String clean = name.replaceAll("[\"\\\\§]", "").trim();
		UUID id = spawnVillager(level, b, slot, clean.isEmpty() ? NAMES[RANDOM.nextInt(NAMES.length)] : clean);
		if (id == null) {
			return false;
		}
		b.villagers.set(slot, id);
		markDirty();
		return true;
	}

	/** Why this building can't be upgraded, or null if it can. */
	static @Nullable String whyNoUpgrade(Colony.Building b) {
		if (b.tier >= b.type.maxTier()) {
			return "Already at the top tier.";
		}
		int extraJobs = b.type.needsBeds() ? b.type.workers(b.tier + 1) - b.type.workers(b.tier) : 0;
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
		if (b.type.rebuildsOnUpgrade()) {
			rebuild(level, b, false); // in the next tier's stone, or with two more cells; new crew get hired when it's done
		} else {
			hireMissing(level, b);
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
		if (!b.prisoners.isEmpty()) {
			return "There are still prisoners in the cells. Take them out or execute them first.";
		}
		String street = Street.whyNoDemolish(b);
		if (street != null) {
			return street;
		}
		int leaving = b.type.needsBeds() ? b.villagers.size() : 0;
		if (b.type.housing(b.tier) > 0 && b.colony.housing() - b.type.housing(b.tier) < b.colony.jobs() - leaving) {
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
		// the storehouse's warehouse is packed up with its stock (the core drops at the door), the dock is closed
		ItemStack packed = ItemStack.EMPTY;
		String warehouse = warehouseOf(b);
		if (warehouse != null) {
			packed = WarehouseLink.pack(warehouse);
			b.warehouse = null;
		}
		if (b.type == BuildingType.HARBOR_OFFICE) {
			WarehouseLink.undock(level, b.world(BuildingType.HARBOR_DOCK));
		}
		Street.forget(level, b);
		int h = b.half;
		int d = b.depth;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState ground = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int y = b.height + 1; y >= 0; y--) {
			for (int x = -h; x <= h; x++) {
				for (int z = -d; z <= d; z++) {
					BlockPos pos = toWorld(b.origin, b.quarter, x, y, z);
					level.removeBlockEntity(pos);
					level.setBlock(pos, y == 0 ? ground : air, QUIET);
				}
			}
		}
		BlockPos at = b.world(b.type.home());
		if (!packed.isEmpty()) {
			Block.popResource(level, at, packed);
		}
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
		// Governance (Skills mod): cheaper wages, and every payday is experience
		long wages = Math.round(c.dailyWages() * (1.0 - SkillsLink.bonus(c.owner, "governance/passive")));
		if (wages > 0 && !Bank.charge(c.owner, wages)) {
			if (!c.striking && server != null) {
				ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
				if (owner != null) {
					owner.sendSystemMessage(Component.literal("The workers of " + c.name + " are on strike: you can't pay today's wages (")
						.withStyle(ChatFormatting.RED).append(Bank.text(wages)).append(Component.literal("). Nothing gets gathered, and nobody stands guard, until you can.")
							.withStyle(ChatFormatting.RED)));
				}
			}
			c.striking = true;
			return;
		}
		c.striking = false;
		SkillsLink.xp(c.owner, "governance", wages / 100.0 * 1.5);
		double taskmaster = SkillsLink.bonus(c.owner, "governance/taskmaster");

		List<Colony.Building> stores = storesOf(c);
		for (Colony.Building s : stores) {
			migrate(s);
		}
		// gather
		for (Colony.Building b : c.buildings) {
			List<ItemStack> harvest = b.type == BuildingType.TOBACCO_FARM ? Production.tobacco(b.tier, b.alive(), RANDOM)
				: Production.gather(b.type, b.tier, b.alive(), RANDOM);
			for (ItemStack stack : harvest) {
				if (taskmaster > 0) {
					stack.setCount(Math.min(stack.getMaxStackSize(), (int) Math.round(stack.getCount() * (1.0 + taskmaster))));
				}
				stow(stores, stack); // what doesn't fit rots in the field
			}
		}
		// workshops turn raw goods into better ones
		for (Colony.Building b : c.buildings) {
			if (b.type != BuildingType.WORKSHOP || b.alive() == 0) {
				continue;
			}
			int budget = (int) (b.alive() * Production.WORKSHOP_PER_WORKER * Production.tierBonus(b.tier));
			for (Production.Recipe r : Production.WORKSHOP) {
				long have = count(stores, r.input());
				int batches = (int) Math.min(budget / r.in(), have / r.in());
				if (batches <= 0) {
					continue;
				}
				take(stores, r.input(), (long) batches * r.in());
				int out = batches * r.out();
				while (out > 0) {
					int n = Math.min(out, r.output().getDefaultMaxStackSize());
					stow(stores, new ItemStack(r.output(), n));
					out -= n;
				}
				budget -= batches * r.in();
			}
		}
		// storehouses with autosell on sell everything the Market will buy; a harbor gets them better prices
		double harbor = harborBonus(c);
		long earned = 0;
		for (Colony.Building s : stores) {
			if (!s.autosell) {
				continue;
			}
			double bonus = (s.tier >= 3 ? 1.1 : 1.0) * (1.0 + harbor);
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				long value = Bank.sellValue(stack);
				if (value > 0) {
					earned += Math.round(value * bonus);
					s.storage.setItem(i, ItemStack.EMPTY);
				}
			}
			String warehouse = warehouseOf(s);
			if (warehouse != null) {
				for (Map.Entry<ItemStack, Long> e : WarehouseLink.stock(warehouse)) {
					long each = Bank.sellValue(e.getKey().copyWithCount(1));
					if (each <= 0) {
						continue;
					}
					long sold = WarehouseLink.take(warehouse, e.getKey(), e.getValue());
					earned += Math.round(each * sold * bonus);
				}
			}
		}
		// the trading post, the bank and the museum earn their keep; the masters restock
		ServerLevel here = level(c);
		for (Colony.Building b : c.buildings) {
			earned += Street.earnings(here, b);
			if (b.type == BuildingType.TRADING_POST && here != null) {
				Street.restock(here, b);
			}
		}
		if (earned > 0) {
			Bank.credit(c.owner, c.ownerName, earned);
		}
	}

	/** What a bank's vault holds, in cents, by the bank's id (for the Riches mod's pile of gold). */
	static Long vaultOf(String buildingId) {
		for (Colony c : COLONIES) {
			for (Colony.Building b : c.buildings) {
				if (b.type == BuildingType.BANK && b.id.equals(buildingId)) {
					return b.vault;
				}
			}
		}
		return 0L;
	}

	/** Price in cents to replace one dead worker: a staffed chapel takes a quarter off per tier. */
	static long replacePrice(Colony c) {
		return Math.round(Bank.cents(REPLACE_PRICE) * (1.0 - Street.replaceDiscount(c)));
	}

	static List<Colony.Building> storesOf(Colony c) {
		List<Colony.Building> stores = new ArrayList<>();
		for (Colony.Building b : c.buildings) {
			if (b.type == BuildingType.STOREHOUSE) {
				stores.add(b);
			}
		}
		return stores;
	}

	/** A staffed harbor office gets the colony's goods to better markets: +5% on auto-sales per tier. */
	static double harborBonus(Colony c) {
		int best = 0;
		for (Colony.Building b : c.buildings) {
			if (b.type == BuildingType.HARBOR_OFFICE && b.alive() > 0) {
				best = Math.max(best, b.tier);
			}
		}
		return 0.05 * best;
	}

	/** Puts the stack in the storehouses: onto their warehouse shelves first, then into their own slots. Returns what didn't fit. */
	static ItemStack stow(List<Colony.Building> stores, ItemStack stack) {
		for (Colony.Building s : stores) {
			String warehouse = warehouseOf(s);
			if (warehouse != null) {
				WarehouseLink.deposit(warehouse, stack);
				if (stack.isEmpty()) {
					return ItemStack.EMPTY;
				}
			}
		}
		ItemStack rest = stack;
		for (Colony.Building s : stores) {
			rest = s.storage.addItem(rest);
			if (rest.isEmpty()) {
				return ItemStack.EMPTY;
			}
		}
		return rest;
	}

	static long count(List<Colony.Building> stores, net.minecraft.world.item.Item item) {
		long n = 0;
		ItemStack kind = new ItemStack(item);
		for (Colony.Building s : stores) {
			String warehouse = warehouseOf(s);
			if (warehouse != null) {
				n += WarehouseLink.count(warehouse, kind);
			}
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				if (stack.is(item)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	private static void take(List<Colony.Building> stores, net.minecraft.world.item.Item item, long amount) {
		ItemStack kind = new ItemStack(item);
		for (Colony.Building s : stores) {
			String warehouse = warehouseOf(s);
			if (warehouse != null && amount > 0) {
				amount -= WarehouseLink.take(warehouse, kind, amount);
			}
			for (int i = 0; i < s.storage.getContainerSize() && amount > 0; i++) {
				ItemStack stack = s.storage.getItem(i);
				if (stack.is(item)) {
					int n = (int) Math.min(amount, stack.getCount());
					stack.shrink(n);
					amount -= n;
					if (stack.isEmpty()) {
						s.storage.setItem(i, ItemStack.EMPTY);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- train stations

	/**
	 * Every two seconds: whatever a train dropped off at a station goes into the storehouses, and stations that ship
	 * goods out get their Pickup Station topped up from the storehouses for the next train.
	 */
	static void stations() {
		for (Colony c : COLONIES) {
			ServerLevel level = level(c);
			if (level == null) {
				continue;
			}
			List<Colony.Building> stores = null;
			for (Colony.Building b : c.buildings) {
				if (b.type != BuildingType.TRAIN_STATION) {
					continue;
				}
				if (stores == null) {
					stores = storesOf(c);
				}
				for (int t = 0; t < BuildingType.tracks(b.tier); t++) {
					BlockPos drop = b.world(BuildingType.DROP_OFFS[t]);
					if (level.isLoaded(drop) && level.getBlockEntity(drop) instanceof Container box && !box.isEmpty()) {
						for (int i = 0; i < box.getContainerSize(); i++) {
							ItemStack stack = box.getItem(i);
							if (!stack.isEmpty()) {
								box.setItem(i, stow(stores, stack.copy()));
							}
						}
						box.setChanged();
					}
					BlockPos pickup = b.world(BuildingType.PICKUPS[t]);
					if (b.export && !c.striking && level.isLoaded(pickup) && level.getBlockEntity(pickup) instanceof Container box) {
						ship(stores, box);
					}
				}
			}
		}
	}

	/** Fills the container from the storehouses, as far as it has room. */
	static void ship(List<Colony.Building> stores, Container box) {
		boolean moved = false;
		for (Colony.Building s : stores) {
			String warehouse = warehouseOf(s);
			if (warehouse != null) {
				for (Map.Entry<ItemStack, Long> e : WarehouseLink.stock(warehouse)) {
					ItemStack kind = e.getKey();
					long have = e.getValue();
					while (have > 0) {
						int n = (int) Math.min(have, kind.getMaxStackSize());
						int put = insert(box, kind.copyWithCount(n));
						if (put > 0) {
							WarehouseLink.take(warehouse, kind, put);
							have -= put;
							moved = true;
						}
						if (put < n) {
							break; // no more room for this kind
						}
					}
				}
			}
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				int put = insert(box, stack.copy());
				if (put > 0) {
					stack.shrink(put);
					moved = true;
					if (stack.isEmpty()) {
						s.storage.setItem(i, ItemStack.EMPTY);
					}
				}
			}
		}
		if (moved) {
			box.setChanged();
			markDirty();
		}
	}

	/** Puts as much of the stack into the container as fits. Returns how many went in. */
	private static int insert(Container box, ItemStack stack) {
		int total = stack.getCount();
		int left = total;
		for (int i = 0; i < box.getContainerSize() && left > 0; i++) {
			ItemStack there = box.getItem(i);
			if (there.isEmpty()) {
				int n = Math.min(left, stack.getMaxStackSize());
				box.setItem(i, stack.copyWithCount(n));
				left -= n;
			} else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
				int n = Math.min(left, there.getMaxStackSize() - there.getCount());
				there.grow(n);
				left -= n;
			}
		}
		return total - left;
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
		if (b.type == BuildingType.BANK && pos.equals(b.world(BuildingType.BANK_TELLER))) {
			if (hand == InteractionHand.MAIN_HAND) {
				BankMenu.open(player, b); // anyone may bank here
			}
			return InteractionResult.SUCCESS;
		}
		boolean counter = b.type == BuildingType.TOWN_HALL && pos.equals(b.world(BuildingType.COUNTER));
		boolean store = b.type == BuildingType.STOREHOUSE && level.getBlockState(pos).is(Blocks.BARREL);
		int cell = Prison.cellAt(b, pos);
		if (!counter && !store && cell < 0) {
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			if (!b.colony.isOwner(player.getUUID())) {
				player.sendSystemMessage(Component.literal("This is " + b.colony.name + ", " + b.colony.ownerName + "'s colony.")
					.withStyle(ChatFormatting.YELLOW));
			} else if (counter) {
				TownHallMenu.open(player, b.colony);
			} else if (cell >= 0) {
				openCell(player, level, hand, b, cell);
			} else if (warehouseOf(b) != null) {
				WarehouseLink.open(player, b.warehouse);
			} else {
				TownHallMenu.openStorage(player, b);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** A holding block: with full shackles in hand, an empty cell takes their prisoner straight away. Otherwise its screen opens. */
	private static void openCell(ServerPlayer player, ServerLevel level, InteractionHand hand, Colony.Building b, int cell) {
		ItemStack held = player.getItemInHand(hand);
		if (Prison.isFull(held) && Prison.inmate(level, b, cell) == null && Prison.lockUp(level, b, cell, held) == null) {
			player.setItemInHand(hand, ItemStack.EMPTY);
			return;
		}
		HoldingMenu.open(player, b, cell);
	}

	static InteractionResult useVillager(ServerPlayer player, InteractionHand hand, Entity entity) {
		Colony.Building b = VILLAGERS.get(entity.getUUID());
		if (b == null) {
			return InteractionResult.PASS;
		}
		if (b.type == BuildingType.BARRACKS && player.getItemInHand(hand).is(Items.IRON_INGOT)) {
			return InteractionResult.PASS; // patching up an iron golem works as usual
		}
		if (b.type == BuildingType.TRADING_POST) {
			return InteractionResult.PASS; // the masters trade, the vanilla way
		}
		if (hand == InteractionHand.MAIN_HAND) {
			String what = switch (b.type) {
				case BARRACKS -> " guards " + b.colony.name + ". Doesn't say much.";
				case WATCHTOWER -> " keeps watch from the " + b.title() + " of " + b.colony.name + ". Eyes on the horizon, please.";
				default -> " works at the " + b.title() + " of " + b.colony.name + ". No time to trade, sorry.";
			};
			player.sendSystemMessage(Component.literal(entity.getName().getString()).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(what).withStyle(ChatFormatting.GRAY)));
		}
		return InteractionResult.SUCCESS;
	}

	/** Test hook: finish every building job right away. */
	static void finishJobs() {
		for (BuildJob job : new ArrayList<>(JOBS)) {
			finish(job);
		}
		JOBS.clear();
	}

	/** Test hook: forget everything (after the smoke test). */
	static void forgetAll() {
		for (Iterator<Colony> it = COLONIES.iterator(); it.hasNext(); ) {
			it.next();
			it.remove();
		}
		VILLAGERS.clear();
		Prison.forget();
	}
}
