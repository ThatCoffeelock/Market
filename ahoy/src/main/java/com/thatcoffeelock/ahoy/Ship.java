package com.thatcoffeelock.ahoy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * One ship. Anchored, it is real blocks in the world plus an invisible anchor entity that carries its
 * data. Sailing, the blocks are lifted into block displays riding an invisible root entity, and
 * everyone aboard sits in an invisible seat that we move along every tick.
 */
public final class Ship {
	public record Controls(boolean forward, boolean back, boolean left, boolean right, boolean jump) {
		static final Controls NONE = new Controls(false, false, false, false, false);

		static Controls of(Input input) {
			return new Controls(input.forward(), input.backward(), input.left(), input.right(), input.jump());
		}
	}

	private static final class Seat {
		final ShipTemplate.Spot spot;
		@Nullable Entity entity;
		@Nullable UUID rider;

		Seat(ShipTemplate.Spot spot) {
			this.spot = spot;
		}
	}

	/** Setting blocks without neighbour updates or drops, so nothing pops off while we build or lift the ship. */
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	private static final double MAX_SPEED = 0.4;
	private static final double[] HULL_Z = {-5.1, 0.5, 6.1};
	private static final double[] HITBOX_Z = {-5, 1, 6.5};

	final ServerLevel level;
	Entity root;
	final ShipData data;
	private final List<Seat> seats = new ArrayList<>();
	private final Entity[] hitboxes = new Entity[HITBOX_Z.length];

	double speed;
	private int age;
	private float sentYaw = Float.NaN;
	private boolean lastJump;
	private int bellCooldown;
	private boolean removed;
	@Nullable Controls testControls;

	Ship(ServerLevel level, Entity root, ShipData data) {
		this.level = level;
		this.root = root;
		this.data = data;
		for (ShipTemplate.Spot spot : ShipTemplate.SPOTS) {
			seats.add(new Seat(spot));
		}
	}

	boolean isRemoved() {
		return removed || root.isRemoved();
	}

	boolean isOwner(Player player) {
		return data.owner.isEmpty() || data.owner.equals(player.getUUID().toString());
	}

	boolean mayCommand(Player player) {
		return !data.locked || isOwner(player) || player.isCreative();
	}

	// ---------------------------------------------------------------- geometry

	/** Quarter turns (0-3) of an anchored ship. */
	int quarter() {
		return Math.floorMod(Math.round(root.getYRot() / 90f), 4);
	}

	BlockPos origin() {
		return BlockPos.containing(root.getX(), data.waterline, root.getZ());
	}

	static Rotation rotation(int quarter) {
		return switch (Math.floorMod(quarter, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

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
		int dz = world.getZ() - origin.getZ();
		int dy = world.getY() - origin.getY();
		return switch (Math.floorMod(quarter, 4)) {
			case 1 -> new BlockPos(dz, dy, -dx);
			case 2 -> new BlockPos(-dx, dy, -dz);
			case 3 -> new BlockPos(-dz, dy, dx);
			default -> new BlockPos(dx, dy, dz);
		};
	}

	/** Continuous local (x = port, z = bow) to world x/z. Same convention as display entity rotation. */
	static double[] toWorld(double x, double z, float yaw, double lx, double lz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[] {x + lx * c - lz * s, z + lx * s + lz * c};
	}

	static double[] toLocal(double x, double z, float yaw, double wx, double wz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		double dx = wx - x;
		double dz = wz - z;
		return new double[] {dx * c + dz * s, -dx * s + dz * c};
	}

	private static boolean replaceable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.canBeReplaced() || (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty());
	}

	/** Can this layout go here? With requireWater, everything below the waterline must be in water (deep enough). */
	static boolean canPlace(ServerLevel level, List<ShipTemplate.ShipBlock> blocks, BlockPos origin, int quarter, boolean requireWater) {
		for (ShipTemplate.ShipBlock b : blocks) {
			BlockPos pos = toWorld(origin, quarter, b.x(), b.y(), b.z());
			if (!level.isLoaded(pos) || !replaceable(level, pos)) {
				return false;
			}
			if (requireWater && b.y() <= 0 && !level.getFluidState(pos).is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}

	/** Puts the ship's blocks into the world. */
	static void place(ServerLevel level, List<ShipTemplate.ShipBlock> blocks, BlockPos origin, int quarter) {
		Rotation rot = rotation(quarter);
		List<ShipTemplate.ShipBlock> sorted = new ArrayList<>(blocks);
		sorted.sort(Comparator.comparingInt(ShipTemplate.ShipBlock::y));
		for (ShipTemplate.ShipBlock b : sorted) {
			level.setBlock(toWorld(origin, quarter, b.x(), b.y(), b.z()), b.state().rotate(rot), QUIET);
		}
		// let fences, panes, stairs etc. connect to their new neighbours
		for (ShipTemplate.ShipBlock b : sorted) {
			if (b.state().isAir()) {
				continue;
			}
			BlockPos pos = toWorld(origin, quarter, b.x(), b.y(), b.z());
			BlockState placed = level.getBlockState(pos);
			BlockState shaped = Block.updateFromNeighbourShapes(placed, level, pos);
			if (shaped != placed) {
				level.setBlock(pos, shaped, QUIET);
			}
		}
	}

	// ---------------------------------------------------------------- anchored: blocks in the world

	/** Positions of this anchored ship's real blocks (for clicks and grief protection). */
	Set<BlockPos> anchoredPositions() {
		Set<BlockPos> set = new HashSet<>();
		BlockPos origin = origin();
		int q = quarter();
		for (ShipTemplate.ShipBlock b : data.blocks) {
			if (!b.state().isAir()) {
				set.add(toWorld(origin, q, b.x(), b.y(), b.z()));
			}
		}
		return set;
	}

	BlockPos helmPos() {
		return toWorld(origin(), quarter(), ShipTemplate.HELM.getX(), ShipTemplate.HELM.getY(), ShipTemplate.HELM.getZ());
	}

	/** 0 = bay A (port barrels), 1 = bay B (starboard barrels), -1 = not a cargo barrel. */
	int cargoBayAt(BlockPos pos) {
		BlockPos local = toLocal(origin(), quarter(), pos);
		for (ShipTemplate.ShipBlock b : data.blocks) {
			if (b.x() == local.getX() && b.y() == local.getY() && b.z() == local.getZ() && b.state().is(Blocks.BARREL)) {
				return b.x() >= 0 ? 0 : 1;
			}
		}
		return -1;
	}

	/** Makes sure the wheel is there (explosions happen). */
	void repairHelm() {
		BlockPos helm = helmPos();
		if (!level.getBlockState(helm).is(Blocks.GRINDSTONE) && replaceable(level, helm)) {
			level.setBlock(helm, ShipTemplate.state("minecraft:grindstone[face=floor,facing=north]").rotate(rotation(quarter())), Block.UPDATE_ALL);
		}
	}

	/**
	 * Reads the ship as it stands now: its own blocks (minus any that were broken) plus anything built
	 * onto it above the deck. Items in chests, furnaces etc. go to the cargo hold. Doesn't remove anything.
	 */
	private List<ShipTemplate.ShipBlock> capture(@Nullable ServerPlayer overflowTo) {
		BlockPos origin = origin();
		int q = quarter();
		Rotation back = rotation(4 - q);
		Map<BlockPos, BlockState> layout = new HashMap<>();
		Deque<BlockPos> frontier = new ArrayDeque<>();

		for (ShipTemplate.ShipBlock b : data.blocks) {
			BlockPos pos = toWorld(origin, q, b.x(), b.y(), b.z());
			BlockState now = level.getBlockState(pos);
			if (now.isAir()) {
				if (b.y() <= 0) {
					layout.put(b.local(), now); // dry hold
				}
			} else if (!replaceable(level, pos)) {
				layout.put(b.local(), now.rotate(back));
				frontier.add(pos);
			}
		}
		// things built onto the ship above deck level
		Set<BlockPos> seen = new HashSet<>(frontier);
		while (!frontier.isEmpty()) {
			BlockPos from = frontier.poll();
			for (Direction dir : Direction.values()) {
				BlockPos pos = from.relative(dir);
				if (!seen.add(pos)) {
					continue;
				}
				BlockPos local = toLocal(origin, q, pos);
				if (layout.containsKey(local) || local.getY() < 2 || local.getY() > ShipTemplate.MAX_Y
					|| local.getX() < ShipTemplate.MIN_X || local.getX() > ShipTemplate.MAX_X
					|| local.getZ() < ShipTemplate.MIN_Z || local.getZ() > ShipTemplate.MAX_Z) {
					continue;
				}
				BlockState now = level.getBlockState(pos);
				if (!now.isAir() && !replaceable(level, pos)) {
					layout.put(local, now.rotate(back));
					frontier.add(pos);
				}
			}
		}
		// empty containers into the hold
		for (BlockPos local : layout.keySet()) {
			BlockPos pos = toWorld(origin, q, local.getX(), local.getY(), local.getZ());
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container container) {
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack stack = container.removeItemNoUpdate(i);
					if (!stack.isEmpty()) {
						ItemStack rest = data.stow(stack);
						if (!rest.isEmpty()) {
							if (overflowTo != null) {
								Bottle.give(overflowTo, rest);
							} else {
								Block.popResource(level, pos, rest);
							}
						}
					}
				}
			}
		}
		List<ShipTemplate.ShipBlock> blocks = new ArrayList<>();
		layout.forEach((p, s) -> blocks.add(new ShipTemplate.ShipBlock(p.getX(), p.getY(), p.getZ(), s)));
		return blocks;
	}

	/** Takes the ship's blocks out of the world, leaving sea (below the waterline) or air behind. */
	private void clear(List<ShipTemplate.ShipBlock> blocks) {
		BlockPos origin = origin();
		int q = quarter();
		List<ShipTemplate.ShipBlock> sorted = new ArrayList<>(blocks);
		sorted.sort(Comparator.comparingInt(ShipTemplate.ShipBlock::y).reversed());
		BlockState water = Blocks.WATER.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (ShipTemplate.ShipBlock b : sorted) {
			BlockPos pos = toWorld(origin, q, b.x(), b.y(), b.z());
			level.removeBlockEntity(pos);
			level.setBlock(pos, b.y() <= 0 ? water : air, QUIET);
		}
	}

	// ---------------------------------------------------------------- summoning

	private static String safeName(String name) {
		String cleaned = name.replaceAll("[^A-Za-z0-9 '!?.,()&-]", "").trim();
		return cleaned.isEmpty() ? "Nameless" : cleaned.substring(0, Math.min(40, cleaned.length()));
	}

	/** Two back-to-back name plates on the stern. */
	private static String nameplates(ShipData data, float yaw) {
		String text = "text:{text:\"" + safeName(data.name) + "\",color:\"gold\",bold:true},background:0,shadow:1b";
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		String common = ",Tags:[\"ahoy_part\"]," + rot + ",teleport_duration:2,billboard:\"fixed\",";
		return "{id:\"minecraft:text_display\"" + common + text
			+ ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,3.2f,-8.56f],scale:[1.5f,1.5f,1.5f]}},"
			+ "{id:\"minecraft:text_display\"" + common + text
			+ ",transformation:{left_rotation:[0f,1f,0f,0f],right_rotation:[0f,0f,0f,1f],translation:[0f,3.2f,-8.56f],scale:[1.5f,1.5f,1.5f]}}";
	}

	/** The invisible anchor that holds the data of an anchored ship. */
	static String anchorCommand(ShipData data, UUID id, double x, double y, double z, float yaw) {
		return "summon minecraft:item_display " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id) + ",Tags:[\"ahoy_ship\"],Rotation:["
			+ Cmd.f(yaw) + "f,0f],Passengers:[" + nameplates(data, yaw) + "]}";
	}

	/** The sailing ship: a root carrying one block display per visible block. */
	static String sailingCommand(ShipData data, UUID id, double x, double y, double z, float yaw) {
		Map<BlockPos, BlockState> layout = new HashMap<>();
		for (ShipTemplate.ShipBlock b : data.blocks) {
			layout.put(b.local(), b.state());
		}
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(x, y, z)).append(" {")
			.append(Cmd.uuidNbt(id)).append(",Tags:[\"ahoy_ship\"],").append(rot).append(",teleport_duration:2,Passengers:[")
			.append(nameplates(data, yaw));
		for (ShipTemplate.ShipBlock b : data.blocks) {
			BlockState state = b.state();
			if (state.isAir() || hidden(layout, b)) {
				continue;
			}
			if (state.getRenderShape() != RenderShape.MODEL) {
				// chests, beds, signs... are drawn by the client in special ways that displays can't do
				if (!BlockStateParser.serialize(state).contains("chest")) {
					continue;
				}
				state = Blocks.BARREL.defaultBlockState();
			}
			cmd.append(",{id:\"minecraft:block_display\",block_state:").append(blockStateNbt(state))
				.append(",Tags:[\"ahoy_part\"],").append(rot)
				.append(",teleport_duration:2,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[")
				.append(Cmd.f(b.x() - 0.5)).append("f,").append(Cmd.f(b.y())).append("f,").append(Cmd.f(b.z() - 0.5))
				.append("f],scale:[1f,1f,1f]}}");
		}
		return cmd.append("]}").toString();
	}

	/** Display block_state: a bare id, or {Name:..., Properties:{...}} when the block has properties. */
	static String blockStateNbt(BlockState state) {
		String text = BlockStateParser.serialize(state).replace("\"", "");
		int open = text.indexOf('[');
		if (open < 0 || !text.endsWith("]")) {
			return "\"" + text + "\"";
		}
		StringBuilder nbt = new StringBuilder("{Name:\"").append(text, 0, open).append("\",Properties:{");
		String[] pairs = text.substring(open + 1, text.length() - 1).split(",");
		for (int i = 0; i < pairs.length; i++) {
			String[] kv = pairs[i].split("=", 2);
			if (kv.length != 2) {
				continue;
			}
			if (i > 0) {
				nbt.append(',');
			}
			nbt.append(kv[0]).append(":\"").append(kv[1]).append('"');
		}
		return nbt.append("}}").toString();
	}

	/** A block completely buried in the ship doesn't need a display (saves a lot of entities). */
	private static boolean hidden(Map<BlockPos, BlockState> layout, ShipTemplate.ShipBlock b) {
		for (Direction dir : Direction.values()) {
			BlockState next = layout.get(b.local().relative(dir));
			if (next == null || !next.canOcclude()) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- setting sail / dropping anchor

	/** Anchored → sailing. Everyone standing on the ship gets a seat where they stand. */
	boolean setSail(@Nullable ServerPlayer captain) {
		if (data.sailing || isRemoved()) {
			return false;
		}
		float yaw = quarter() * 90f;
		double cx = root.getX();
		double cz = root.getZ();

		// who's aboard, and where
		List<ServerPlayer> aboard = new ArrayList<>();
		List<double[]> where = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.getVehicle() != null || p == captain) {
				continue;
			}
			double[] local = toLocal(cx, cz, yaw, p.getX(), p.getZ());
			double ly = p.getY() - data.waterline;
			if (local[0] > -5 && local[0] < 5 && local[1] > -10 && local[1] < 13 && ly > -3.5 && ly < 14) {
				aboard.add(p);
				where.add(new double[] {local[0], ly + 0.45, local[1]});
			}
		}

		List<ShipTemplate.ShipBlock> layout = capture(captain);
		data.blocks = layout;
		Ships.unindex(this);
		clear(layout);

		data.sailing = true;
		Entity old = root;
		UUID id = UUID.randomUUID();
		Cmd.run(level, sailingCommand(data, id, cx, data.waterline, cz, yaw));
		Entity fresh = level.getEntity(id);
		if (fresh == null) {
			// should never happen; put the ship back rather than lose it
			AhoyMod.LOG.error("Could not summon the sailing ship {}; putting it back", data.name);
			data.sailing = false;
			place(level, layout, origin(), quarter());
			Ships.index(this);
			return false;
		}
		fresh.setAttached(AhoyMod.DATA, data);
		old.removeAttached(AhoyMod.DATA);
		for (Entity part : new ArrayList<>(old.getPassengers())) {
			part.discard();
		}
		old.discard();
		root = fresh;
		sentYaw = yaw;
		Ships.rekey(old.getUUID(), this);

		if (captain != null) {
			seat(captain, 0);
		}
		for (int i = 0; i < aboard.size(); i++) {
			double[] w = where.get(i);
			seats.add(new Seat(new ShipTemplate.Spot("On deck", w[0], w[1], w[2])));
			seat(aboard.get(i), seats.size() - 1);
		}
		Cmd.sound(level, "minecraft:entity.player.splash.high_speed", cx, data.waterline + 1, cz, 1.5f, 0.6f);
		Cmd.sound(level, "minecraft:block.bell.use", cx, data.waterline + 3, cz, 1.5f, 1.0f);
		return true;
	}

	/** Sailing → anchored. Snaps to the block grid and the nearest quarter turn. False if there's no room here. */
	boolean dropAnchor() {
		if (!data.sailing || isRemoved()) {
			return true;
		}
		int nearest = Math.floorMod(Math.round(root.getYRot() / 90f), 4);
		BlockPos base = BlockPos.containing(root.getX(), data.waterline, root.getZ());
		BlockPos origin = null;
		int quarter = nearest;
		search:
		for (int dq : new int[] {0, 1, -1}) {
			for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
				BlockPos candidate = base.offset(d[0], 0, d[1]);
				if (canPlace(level, data.blocks, candidate, nearest + dq, false)) {
					origin = candidate;
					quarter = Math.floorMod(nearest + dq, 4);
					break search;
				}
			}
		}
		if (origin == null) {
			return false;
		}
		float yaw = quarter * 90f;
		double cx = origin.getX() + 0.5;
		double cz = origin.getZ() + 0.5;

		// everyone gets off their seat and back on their feet, at the same spot on the deck
		List<ServerPlayer> riders = new ArrayList<>();
		List<ShipTemplate.Spot> spots = new ArrayList<>();
		for (int i = 0; i < seats.size(); i++) {
			ServerPlayer p = rider(i);
			seats.get(i).rider = null;
			if (p != null) {
				Ships.forgetRider(p.getUUID(), this);
				p.stopRiding();
				riders.add(p);
				spots.add(seats.get(i).spot);
			}
		}
		discardSeatsAndHitboxes();

		place(level, data.blocks, origin, quarter);
		data.sailing = false;
		Entity old = root;
		UUID id = UUID.randomUUID();
		Cmd.run(level, anchorCommand(data, id, cx, data.waterline, cz, yaw));
		Entity fresh = level.getEntity(id);
		if (fresh == null) {
			AhoyMod.LOG.error("Could not summon the anchor for {}", data.name);
			return false;
		}
		fresh.setAttached(AhoyMod.DATA, data);
		old.removeAttached(AhoyMod.DATA);
		for (Entity part : new ArrayList<>(old.getPassengers())) {
			part.discard();
		}
		old.discard();
		root = fresh;
		speed = 0;
		Ships.rekey(old.getUUID(), this);
		Ships.index(this);

		for (int i = 0; i < riders.size(); i++) {
			ShipTemplate.Spot s = spots.get(i);
			double[] w = toWorld(cx, cz, yaw, s.x(), s.z());
			Cmd.run(level, "tp " + riders.get(i).getUUID() + " " + Cmd.pos(w[0], data.waterline + s.y() + 0.1, w[1]));
		}
		// keep only the fixed spots; the "where you stood" ones are made again next time
		seats.subList(ShipTemplate.SPOTS.size(), seats.size()).clear();
		Cmd.sound(level, "minecraft:block.chain.place", cx, data.waterline + 1, cz, 1.5f, 0.5f);
		Cmd.sound(level, "minecraft:entity.generic.splash", cx, data.waterline, cz, 1.5f, 0.8f);
		return true;
	}

	/** Anchored ship → bottle item. Anyone standing on it is about to go for a swim. */
	ItemStack bottleUp(@Nullable ServerPlayer overflowTo) {
		List<ShipTemplate.ShipBlock> layout = capture(overflowTo);
		data.blocks = layout;
		Ships.unindex(this);
		clear(layout);
		ItemStack bottle = Bottle.packed(level, data);
		remove();
		return bottle;
	}

	// ---------------------------------------------------------------- ticking

	void tick() {
		if (isRemoved()) {
			return;
		}
		age++;
		if (!data.sailing) {
			return;
		}
		ensureHitboxes();
		updateRiders();
		ServerPlayer captain = rider(0);
		Controls controls = testControls != null ? testControls : captain != null ? Controls.of(captain.getLastClientInput()) : Controls.NONE;
		sail(controls);
		placeSeatsAndHitboxes();

		if (controls.jump() && !lastJump && bellCooldown <= 0) {
			ringBell();
		}
		lastJump = controls.jump();
		if (bellCooldown > 0) {
			bellCooldown--;
		}
		if (hasRiders()) {
			protectRiders();
			if (age % 4 == 0) {
				repelMonsters();
			}
		}
		if (captain != null && age % 5 == 0) {
			hud(captain);
		}
	}

	private boolean hullFits(double x, double z, float yaw) {
		double y0 = data.waterline - 3 + 0.05;
		double y1 = data.waterline + 3.5;
		for (double segment : HULL_Z) {
			double[] c = toWorld(x, z, yaw, 0, segment);
			if (!level.noCollision(new AABB(c[0] - 3.4, y0, c[1] - 3.4, c[0] + 3.4, y1, c[1] + 3.4))) {
				return false;
			}
		}
		return true;
	}

	private void sail(Controls c) {
		double x = root.getX();
		double z = root.getZ();
		float yaw = root.getYRot();
		double wind = Wind.factor(level, yaw);
		double max = MAX_SPEED * wind;

		if (c.forward()) {
			speed = Math.min(max, speed + 0.004);
		} else if (c.back()) {
			speed = Math.max(-0.06, speed - 0.006);
		} else {
			speed *= 0.996;
		}
		if (speed > max) {
			speed *= 0.99;
		}
		if (Math.abs(speed) < 0.002) {
			speed = 0;
		}

		int rudder = (c.right() ? 1 : 0) - (c.left() ? 1 : 0);
		if (rudder != 0) {
			double way = Mth.clamp(Math.abs(speed) / 0.12, 0.25, 1.0);
			float delta = (float) (rudder * 1.2 * way * (speed < 0 ? -1 : 1));
			float newYaw = Mth.wrapDegrees(yaw + delta);
			if (hullFits(x, z, newYaw)) {
				yaw = newYaw;
			}
		}

		if (speed != 0) {
			double rad = Math.toRadians(yaw);
			double dx = -Math.sin(rad) * speed;
			double dz = Math.cos(rad) * speed;
			if (hullFits(x + dx, z + dz, yaw)) {
				x += dx;
				z += dz;
			} else {
				if (Math.abs(speed) > 0.15) {
					Cmd.sound(level, "minecraft:block.wood.break", x, data.waterline + 1, z, 1.5f, 0.5f);
					ServerPlayer captain = rider(0);
					if (captain != null) {
						captain.sendSystemMessage(Component.literal("CRUNCH. Land ho! (That's the ground.)").withStyle(ChatFormatting.GRAY));
					}
				}
				speed = 0;
			}
		}

		double y = data.waterline + 0.06 * Math.sin(age * 0.08);
		root.setPos(x, y, z);
		root.setYRot(yaw);
		// re-aiming a few hundred displays is the expensive bit; do it at most every other tick
		if (yaw != sentYaw && age % 2 == 0) {
			sentYaw = yaw;
			for (Entity part : root.getPassengers()) {
				part.setYRot(yaw);
			}
		}
		if (speed > 0.1 && age % 4 == 0) {
			double[] bow = toWorld(x, z, yaw, 0, 9.5);
			Cmd.particles(level, "minecraft:splash", bow[0], data.waterline + 1, bow[1], 1.0, 0.1, 12);
		}
		if (speed > 0.05 && age % 30 == 0) {
			Cmd.sound(level, "minecraft:ambient.underwater.loop.additions", x, data.waterline, z, 0.6f, 1.2f);
		}
	}

	void ringBell() {
		bellCooldown = 20;
		Cmd.sound(level, "minecraft:block.bell.use", root.getX(), data.waterline + 3, root.getZ(), 2.0f, 1.0f);
	}

	private void hud(ServerPlayer captain) {
		int kmh = (int) Math.round(Math.abs(speed) * 20 * 3.6);
		MutableComponent line = Component.literal("⛵ " + kmh + " km/h").withStyle(ChatFormatting.AQUA);
		line.append(Component.literal("   Wind " + Wind.arrow(level, root.getYRot()) + " " + Wind.label(level, root.getYRot()))
			.withStyle(ChatFormatting.WHITE));
		line.append(Component.literal("   Shift: drop anchor").withStyle(ChatFormatting.GRAY));
		captain.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	// ---------------------------------------------------------------- seats and hitboxes

	/** Summons a seat or hitbox and marks it, so strays can be cleaned up after a crash. */
	private @Nullable Entity summonMarker(String type, String nbt, double x, double y, double z) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon " + type + " " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id) + "," + nbt + "}");
		Entity entity = level.getEntity(id);
		if (entity != null) {
			entity.setAttached(AhoyMod.MARKER, true);
		}
		return entity;
	}

	private @Nullable Entity seatEntity(int index) {
		Seat seat = seats.get(index);
		if (seat.entity == null || seat.entity.isRemoved()) {
			double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), seat.spot.x(), seat.spot.z());
			seat.entity = summonMarker("minecraft:item_display", "teleport_duration:2,Tags:[\"ahoy_seat\"]", w[0], root.getY() + seat.spot.y(), w[1]);
			if (seat.entity != null) {
				Ships.registerMarker(seat.entity, this);
			}
		}
		return seat.entity;
	}

	private void ensureHitboxes() {
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] == null || hitboxes[i].isRemoved()) {
				double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, HITBOX_Z[i]);
				hitboxes[i] = summonMarker("minecraft:interaction", "width:7f,height:5f,response:1b,Tags:[\"ahoy_hitbox\"]", w[0], root.getY() - 1, w[1]);
				if (hitboxes[i] != null) {
					Ships.registerMarker(hitboxes[i], this);
				}
			}
		}
	}

	private void placeSeatsAndHitboxes() {
		float yaw = root.getYRot();
		for (Seat seat : seats) {
			if (seat.entity != null && !seat.entity.isRemoved()) {
				double[] w = toWorld(root.getX(), root.getZ(), yaw, seat.spot.x(), seat.spot.z());
				seat.entity.setPos(w[0], root.getY() + seat.spot.y(), w[1]);
				seat.entity.setYRot(yaw);
			}
		}
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] != null) {
				double[] w = toWorld(root.getX(), root.getZ(), yaw, 0, HITBOX_Z[i]);
				hitboxes[i].setPos(w[0], root.getY() - 1, w[1]);
			}
		}
	}

	private void discardSeatsAndHitboxes() {
		for (Seat seat : seats) {
			if (seat.entity != null) {
				seat.entity.ejectPassengers();
				seat.entity.discard();
				seat.entity = null;
			}
		}
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] != null) {
				hitboxes[i].discard();
				hitboxes[i] = null;
			}
		}
	}

	@Nullable ServerPlayer rider(int index) {
		Entity seat = seats.get(index).entity;
		if (seat == null) {
			return null;
		}
		return seat.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	int seatCount() {
		return seats.size();
	}

	String seatName(int index) {
		return seats.get(index).spot.name();
	}

	boolean seatFree(int index) {
		Entity seat = seats.get(index).entity;
		return seat == null || seat.isRemoved() || seat.getPassengers().isEmpty();
	}

	int seatOf(Player player) {
		for (int i = 0; i < seats.size(); i++) {
			Entity seat = seats.get(i).entity;
			if (seat != null && player.getVehicle() == seat) {
				return i;
			}
		}
		return -1;
	}

	boolean hasRiders() {
		for (Seat seat : seats) {
			if (seat.rider != null) {
				return true;
			}
		}
		return false;
	}

	/** Puts someone in a seat (making the seat entity if needed). */
	boolean seat(ServerPlayer player, int index) {
		if (!seatFree(index)) {
			return false;
		}
		Entity seat = seatEntity(index);
		if (seat == null) {
			return false;
		}
		int current = seatOf(player);
		if (current >= 0) {
			seats.get(current).rider = null;
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		Cmd.run(level, "ride " + player.getUUID() + " mount " + seat.getUUID());
		if (player.getVehicle() != seat) {
			return false;
		}
		seats.get(index).rider = player.getUUID();
		Ships.rememberRider(player.getUUID(), this);
		return true;
	}

	/** First free seat for someone climbing aboard at sea. */
	int pickSeat(Player player) {
		if (mayCommand(player) && seatFree(0)) {
			return isOwner(player) ? 0 : firstFreeOr(0);
		}
		return firstFreeOr(-1);
	}

	private int firstFreeOr(int fallback) {
		for (int i = 1; i < ShipTemplate.SPOTS.size(); i++) {
			if (seatFree(i)) {
				return i;
			}
		}
		return fallback;
	}

	private void updateRiders() {
		for (int i = 0; i < seats.size(); i++) {
			Seat seat = seats.get(i);
			Entity passenger = seat.entity == null ? null : seat.entity.getFirstPassenger();
			UUID now = passenger instanceof ServerPlayer ? passenger.getUUID() : null;
			if (seat.rider != null && !seat.rider.equals(now)) {
				UUID gone = seat.rider;
				seat.rider = null;
				Ships.forgetRider(gone, this);
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
				if (player != null && !player.isDeadOrDying() && player.getVehicle() == null && player.level() == level
					&& seat.entity != null && player.distanceToSqr(seat.entity) < 16) {
					left(player, i);
					if (!data.sailing) {
						return; // anchored: seats are gone
					}
				}
			}
			if (now != null && seat.rider == null) {
				seat.rider = now;
				Ships.rememberRider(now, this);
			}
		}
	}

	/** Someone stood up. The captain standing up means "drop anchor"; anyone else goes overboard. */
	private void left(ServerPlayer player, int index) {
		if (index == 0) {
			if (dropAnchor()) {
				player.sendSystemMessage(Component.literal("Anchor's down. You can walk around again.").withStyle(ChatFormatting.GOLD));
			} else {
				seat(player, 0);
				player.sendSystemMessage(Component.literal("Can't drop anchor here: too close to land or something in the way. Sail to open water first.")
					.withStyle(ChatFormatting.RED));
			}
			return;
		}
		ShipTemplate.Spot s = seats.get(index).spot;
		double side = s.x() >= 0 ? 5 : -5;
		double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), side, s.z());
		Cmd.run(level, "tp " + player.getUUID() + " " + Cmd.pos(w[0], data.waterline + 1, w[1]));
		ServerPlayer captain = rider(0);
		if (captain != null) {
			captain.sendSystemMessage(Component.literal("Man overboard! " + player.getName().getString() + " jumped ship.").withStyle(ChatFormatting.YELLOW));
		}
	}

	// ---------------------------------------------------------------- safety at sea

	private void protectRiders() {
		for (int i = 0; i < seats.size(); i++) {
			ServerPlayer player = rider(i);
			if (player != null) {
				player.clearFire();
				player.setAirSupply(player.getMaxAirSupply());
			}
		}
	}

	/** Drowned, guardians, phantoms...: shoved away from the ship and they forget who was aboard. */
	private void repelMonsters() {
		double x = root.getX();
		double y = root.getY();
		double z = root.getZ();
		AABB near = new AABB(x - 40, y - 20, z - 40, x + 40, y + 20, z + 40);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, near, m -> m instanceof Enemy && m.isAlive())) {
			LivingEntity target = mob.getTarget();
			if (target != null && Ships.shipOf(target) == this) {
				mob.setTarget(null);
			}
			double[] local = toLocal(x, z, root.getYRot(), mob.getX(), mob.getZ());
			if (Math.abs(local[0]) < 7 && Math.abs(local[1]) < 13 && Math.abs(mob.getY() - y) < 10) {
				double[] out = toWorld(0, 0, root.getYRot(), Math.signum(local[0] == 0 ? 1 : local[0]), 0);
				mob.setDeltaMovement(mob.getDeltaMovement().add(out[0] * 0.9, 0.3, out[1] * 0.9));
				Cmd.particles(level, "minecraft:electric_spark", mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 0.3, 0.1, 8);
			}
		}
	}

	// ---------------------------------------------------------------- removal

	/** Gone for good (packed into a bottle). */
	void remove() {
		if (removed) {
			return;
		}
		removed = true;
		for (int i = 0; i < seats.size(); i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				p.stopRiding();
			}
		}
		discardSeatsAndHitboxes();
		for (Entity part : new ArrayList<>(root.getPassengers())) {
			part.discard();
		}
		root.removeAttached(AhoyMod.DATA);
		root.discard();
		Ships.unindex(this);
		Ships.unregister(this);
	}

	/** Chunk unloaded / server stopping: drop the seats and hitboxes, keep the rest. */
	void unload() {
		if (removed) {
			return;
		}
		removed = true;
		for (int i = 0; i < seats.size(); i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				p.stopRiding();
			}
		}
		discardSeatsAndHitboxes();
		Ships.unindex(this);
		Ships.unregister(this);
	}
}
