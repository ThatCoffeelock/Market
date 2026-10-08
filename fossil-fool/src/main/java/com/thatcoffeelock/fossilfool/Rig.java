package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * A Drill Rig: a wooden derrick over a 5×5 shaft. It removes one block at a time, top layer first, and works its way
 * straight down to bedrock. Ores go into the ore hold, everything else into the stone hold. Every block costs fuel.
 * When it breaks into an oil pocket it stops drilling and pumps the pocket dry into its own tank (and into Oil Tanks
 * nearby), then carries on down. It leaves a ladder up the north wall, and seals water and lava out of the shaft.
 *
 * The derrick is built from display entities, all riding one invisible root (like the Cannon), so they load and
 * unload together. The drill head and the drill string ride along too; they're moved down with their
 * transformation, which the client interpolates, so the head glides down the shaft.
 *
 * Local coordinates of the model: origin is the middle of the shaft, one block above the ground.
 *
 * Offshore (set up on the seabed under water), it stands on a plank deck at the water's surface instead, with a
 * cobblestone cofferdam around the shaft from the seabed up to the deck. It pumps the cofferdam dry one layer at a
 * time (that costs fuel), puts a ladder down it, and then drills from the seabed like any other rig.
 */
final class Rig {
	enum State {
		OFF("Switched off", ChatFormatting.GRAY),
		DRILLING("Drilling", ChatFormatting.GREEN),
		DRAINING("Pumping the cofferdam dry", ChatFormatting.AQUA),
		PUMPING("Pumping oil", ChatFormatting.GOLD),
		NO_FUEL("Out of fuel", ChatFormatting.RED),
		HOLD_FULL("A hold is full", ChatFormatting.RED),
		TANK_FULL("Oil tank full", ChatFormatting.RED),
		BLOCKED("Something's in the way", ChatFormatting.RED),
		DONE("Hit bedrock. Shaft complete", ChatFormatting.AQUA);

		final String text;
		final ChatFormatting color;

		State(String text, ChatFormatting color) {
			this.text = text;
			this.color = color;
		}
	}

	/** One box of the model: min corner + size. */
	record Part(String block, double x, double y, double z, double sx, double sy, double sz) {
	}

	static final List<Part> FRAME = List.of(
		// four legs at the corners of the shaft
		new Part("minecraft:spruce_log", -2.8, -0.2, -2.8, 0.3, 6.4, 0.3),
		new Part("minecraft:spruce_log", 2.5, -0.2, -2.8, 0.3, 6.4, 0.3),
		new Part("minecraft:spruce_log", -2.8, -0.2, 2.5, 0.3, 6.4, 0.3),
		new Part("minecraft:spruce_log", 2.5, -0.2, 2.5, 0.3, 6.4, 0.3),
		// two rings of cross beams
		new Part("minecraft:dark_oak_planks", -2.8, 2.0, -2.8, 5.6, 0.2, 0.3),
		new Part("minecraft:dark_oak_planks", -2.8, 2.0, 2.5, 5.6, 0.2, 0.3),
		new Part("minecraft:dark_oak_planks", -2.8, 2.0, -2.8, 0.3, 0.2, 5.6),
		new Part("minecraft:dark_oak_planks", 2.5, 2.0, -2.8, 0.3, 0.2, 5.6),
		new Part("minecraft:dark_oak_planks", -2.8, 4.2, -2.8, 5.6, 0.2, 0.3),
		new Part("minecraft:dark_oak_planks", -2.8, 4.2, 2.5, 5.6, 0.2, 0.3),
		new Part("minecraft:dark_oak_planks", -2.8, 4.2, -2.8, 0.3, 0.2, 5.6),
		new Part("minecraft:dark_oak_planks", 2.5, 4.2, -2.8, 0.3, 0.2, 5.6),
		// crown deck and pulley
		new Part("minecraft:spruce_planks", -1.5, 6.2, -1.5, 3.0, 0.15, 3.0),
		new Part("minecraft:spruce_planks", -2.8, 6.2, -2.8, 5.6, 0.15, 0.3),
		new Part("minecraft:spruce_planks", -2.8, 6.2, 2.5, 5.6, 0.15, 0.3),
		new Part("minecraft:iron_block", -0.35, 6.35, -0.35, 0.7, 0.7, 0.7),
		// the engine house on the east side: firebox, boiler and chimney
		new Part("minecraft:blast_furnace", 3.0, -0.2, -0.5, 1.0, 1.0, 1.0),
		new Part("minecraft:copper_block", 3.0, -0.2, 0.55, 1.0, 0.9, 0.9),
		new Part("minecraft:bricks", 3.3, 0.8, -0.2, 0.4, 2.4, 0.4));

	/** The drill head, relative to its own height (see {@link #headY}). */
	static final List<Part> HEAD = List.of(
		new Part("minecraft:polished_blackstone", -0.4, 0.35, -0.4, 0.8, 0.6, 0.8),
		new Part("minecraft:gold_block", -0.2, 0.0, -0.2, 0.4, 0.35, 0.4));
	static final double STRING_TOP = 6.35;
	/** Which moving part a display is (see {@link FossilFoolMod#RIG_PART}): the drill string, then the head parts. */
	static final int STRING_PART = 1;
	static final int HEAD_PART = 2;

	static final String ROOT_TAG = "fossilfool_rig";
	static final String PART_TAG = "fossilfool_rig_part";
	static final String HITBOX_TAG = "fossilfool_rig_hitbox";
	static final String HEAD_TAG = "fossilfool_rig_head";
	static final String STRING_TAG = "fossilfool_rig_string";

	private static final ItemStack PICK = new ItemStack(Items.DIAMOND_PICKAXE);

	final String id;
	String owner = "";
	String ownerName = "";
	final String dimension;
	/** Middle of the shaft, and the ground height it started at. */
	final int cx;
	final int cz;
	final int top;
	/** Offshore: the top water block, where the plank deck goes. On land, the same as {@link #top}. */
	int deck;
	/** Offshore: the next layer of water to pump out of the cofferdam. Dry once it's down to the seabed. */
	int drainY;
	/** The layer being drilled now, and how far through its 25 blocks. */
	int layer;
	int cell;
	boolean on = true;
	boolean keepStone = true;
	boolean bedrock;
	State state = State.DRILLING;
	double energy;
	@Nullable Fuel burning;
	double progress;
	int crude;
	/** The pocket being pumped, or null while drilling. */
	@Nullable Long struck;
	/** Where it got stuck, for the status line. */
	@Nullable BlockPos blockedAt;

	final SimpleContainer firebox = new SimpleContainer(9) {
		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return Fuel.burns(stack) || stack.is(Items.BUCKET);
		}
	};
	final SimpleContainer ores = new SimpleContainer(18);
	final SimpleContainer stone = new SimpleContainer(18);

	/** The model's root entity while it's loaded. */
	@Nullable Entity root;
	int age;
	/** Ticks the shaft area has been loaded without the model turning up (it gets rebuilt after a while). */
	int missing;
	private int shownLayer = Integer.MIN_VALUE;

	Rig(String id, String dimension, int cx, int top, int cz) {
		this.id = id;
		this.dimension = dimension;
		this.cx = cx;
		this.top = top;
		this.cz = cz;
		this.layer = top;
		this.deck = top;
		this.drainY = top;
	}

	boolean offshore() {
		return deck > top;
	}

	/** Is the cofferdam pumped dry (always, on land)? */
	boolean drained() {
		return drainY <= top;
	}

	/** Where the derrick stands: the ground, or offshore, the deck. Hoppers and pipes go on this level or one up. */
	int floor() {
		return offshore() ? deck : top;
	}

	BlockPos center() {
		return new BlockPos(cx, top, cz);
	}

	/** Where the model stands: the middle of the shaft, on the ground. */
	double modelX() {
		return cx + 0.5;
	}

	double modelY() {
		return floor() + 1;
	}

	double modelZ() {
		return cz + 0.5;
	}

	String tag() {
		return "fossilfool_rig_" + id;
	}

	int depth() {
		return top - layer;
	}

	@Nullable UUID ownerId() {
		try {
			return owner.isEmpty() ? null : UUID.fromString(owner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	boolean isOwner(ServerPlayer player) {
		return owner.isEmpty() || owner.equals(player.getUUID().toString());
	}

	List<Container> holds() {
		return List.of(ores, stone);
	}

	// ---------------------------------------------------------------- ticking

	void tick(ServerLevel level) {
		age++;
		if (age % 20 == 0) {
			if (crude > 0) {
				Machines.pipeOut(level, this);
			}
			if (struck == null && state == State.PUMPING) {
				state = State.DRILLING;
			}
		}
		if (age % 100 == 0 && !(ores.isEmpty() && stone.isEmpty())) {
			unload(level);
		}
		if (age % HOPPER_TICKS == 0 && !(ores.isEmpty() && stone.isEmpty())) {
			feedHoppers(level);
			feedPipes(level);
		}
		if (!on) {
			state = State.OFF;
			return;
		}
		if (state == State.DONE) {
			return;
		}
		if (state == State.OFF) {
			state = State.DRILLING;
		}
		FossilConfig c = FossilConfig.get();
		UUID who = ownerId();
		double speed = (burning == null ? 1.0 : burning.speed()) * (1.0 + (who == null ? 0 : Hooks.bonus(who, "speed")));
		double period = struck != null ? c.ticksPerBucket : c.ticksPerBlock;
		progress = Math.min(progress + speed, period);
		if (progress < period) {
			return;
		}
		progress -= period;
		if (struck != null) {
			pump(level);
		} else if (!drained()) {
			drain(level);
		} else {
			drill(level);
		}
		if ((state == State.DRILLING || state == State.PUMPING || state == State.DRAINING) && age % 10 == 0) {
			Cmd.particles(level, "minecraft:large_smoke", modelX() + 3.5, modelY() + 3.4, modelZ(), 0.1, 0.02, 2);
		}
	}

	/**
	 * Burns fuel until there's at least this much in the fire: from the firebox first, then from the tanks it reaches
	 * (the fuel line). Empties that don't fit in the firebox go in the ore hold.
	 */
	boolean refuel(ServerLevel level, double needed) {
		UUID who = ownerId();
		double bonus = who == null ? 0 : Hooks.bonus(who, "fuel");
		while (energy < needed) {
			Fuel.Burn burn = Fuel.take(firebox, left -> {
				ItemStack rest = ores.addItem(left);
				if (!rest.isEmpty()) {
					Block.popResource(level, center().above(), rest);
				}
			});
			if (burn == null) {
				burn = Machines.tankFuel(Machines.tanksFor(level, this));
			}
			if (burn == null) {
				return false;
			}
			energy += burn.blocks() * (1.0 + bonus);
			burning = burn.fuel();
		}
		return true;
	}

	BlockPos cellPos(int i) {
		return new BlockPos(cx - 2 + i % 5, layer, cz - 2 + i / 5);
	}

	/** One step of drilling: removes the next block of the current layer (air is skipped for free). */
	private void drill(ServerLevel level) {
		if (layer < level.getMinY()) {
			finish(level);
			return;
		}
		if (!level.isLoaded(center())) {
			return;
		}
		for (; cell < 25; cell++) {
			BlockPos pos = cellPos(cell);
			BlockState s = level.getBlockState(pos);
			if (s.isAir()) {
				continue;
			}
			if (Pockets.isOil(level, pos)) {
				Pockets.Pocket p = Pockets.pocketOf(pos);
				if (p != null) {
					strike(level, p);
					return;
				}
			}
			if (s.is(Blocks.WATER) || s.is(Blocks.LAVA)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				continue;
			}
			if (s.getDestroySpeed(level, pos) < 0) {
				bedrock = true;
				continue;
			}
			if (s.hasBlockEntity()) {
				state = State.BLOCKED;
				blockedAt = pos;
				return;
			}
			if (energy < 1 && !refuel(level, 1)) {
				state = State.NO_FUEL;
				return;
			}
			boolean ore = isOre(s);
			List<ItemStack> drops = Block.getDrops(s, level, pos, null, null, PICK);
			SimpleContainer hold = ore ? ores : stone;
			boolean keep = ore || keepStone;
			if (keep && !fits(hold, drops)) {
				unload(level);
				if (!fits(hold, drops)) {
					state = State.HOLD_FULL;
					return;
				}
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			if (keep) {
				for (ItemStack drop : drops) {
					ItemStack rest = hold.addItem(drop);
					if (!rest.isEmpty()) {
						Block.popResource(level, center().above(), rest);
					}
				}
			}
			energy -= 1;
			state = State.DRILLING;
			blockedAt = null;
			UUID who = ownerId();
			if (who != null) {
				Hooks.xp(who, ore ? 2.0 : 0.4);
			}
			if (cell % 5 == 0) {
				Cmd.sound(level, "minecraft:block.stone.break", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.7f, 0.6f);
			}
			Pockets.Pocket opened = Pockets.breach(level, pos, null);
			if (opened != null) {
				strike(level, opened);
			}
			cell++;
			return;
		}
		finishLayer(level);
		if (bedrock || layer - 1 < level.getMinY()) {
			finish(level);
			return;
		}
		layer--;
		cell = 0;
		Store.changed();
		showHead(level);
	}

	// ---------------------------------------------------------------- offshore

	/** Is this water (or something sitting in water, like kelp and seagrass)? */
	static boolean isWater(BlockState s) {
		return !s.getFluidState().isEmpty() && !s.is(Blocks.LAVA);
	}

	/** Is this spot inside the 5×5 shaft? */
	private boolean inShaft(int x, int z) {
		return Math.abs(x - cx) <= 2 && Math.abs(z - cz) <= 2;
	}

	/**
	 * Offshore: a plank deck around the shaft at the water's surface (9×9, open over the shaft), and a cobblestone
	 * cofferdam around the shaft from the seabed up to the deck. Only water and loose stuff gets replaced.
	 */
	void buildCofferdam(ServerLevel level) {
		BlockState planks = Gui.block("minecraft:spruce_planks", Blocks.COBBLESTONE).defaultBlockState();
		for (int dx = -HOPPER_REACH; dx <= HOPPER_REACH; dx++) {
			for (int dz = -HOPPER_REACH; dz <= HOPPER_REACH; dz++) {
				if (inShaft(cx + dx, cz + dz)) {
					continue;
				}
				BlockPos pos = new BlockPos(cx + dx, deck, cz + dz);
				BlockState s = level.getBlockState(pos);
				if ((s.canBeReplaced() || isWater(s)) && !s.hasBlockEntity()) {
					level.setBlock(pos, planks, 3);
				}
			}
		}
		for (int y = top + 1; y < deck; y++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (Math.abs(dx) != 3 && Math.abs(dz) != 3) {
						continue;
					}
					BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
					BlockState s = level.getBlockState(pos);
					if ((s.canBeReplaced() || isWater(s)) && !s.hasBlockEntity()) {
						level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	/**
	 * Offshore: pumps one layer of the cofferdam dry, from the deck down. Water costs {@code drainCost} fuel a block;
	 * anything solid in the way (a lump of seabed) is dug out like drilling. Leaves a ladder on the north wall.
	 */
	private void drain(ServerLevel level) {
		if (!level.isLoaded(center())) {
			return;
		}
		FossilConfig c = FossilConfig.get();
		double cost = 0;
		for (int i = 0; i < 25; i++) {
			BlockPos pos = new BlockPos(cx - 2 + i % 5, drainY, cz - 2 + i / 5);
			BlockState s = level.getBlockState(pos);
			if (s.isAir() || s.is(Blocks.LADDER)) {
				continue;
			}
			if (s.hasBlockEntity()) {
				state = State.BLOCKED;
				blockedAt = pos;
				return;
			}
			cost += isWater(s) || s.canBeReplaced() ? c.drainCost : 1;
		}
		if (energy < cost && !refuel(level, cost)) {
			state = State.NO_FUEL;
			return;
		}
		for (int i = 0; i < 25; i++) {
			BlockPos pos = new BlockPos(cx - 2 + i % 5, drainY, cz - 2 + i / 5);
			BlockState s = level.getBlockState(pos);
			if (s.isAir() || s.is(Blocks.LADDER)) {
				continue;
			}
			if (!isWater(s) && !s.canBeReplaced() && keepStone) {
				for (ItemStack drop : Block.getDrops(s, level, pos, null, null, PICK)) {
					ItemStack rest = stone.addItem(drop);
					if (!rest.isEmpty()) {
						Block.popResource(level, center().above(deck - top + 1), rest);
					}
				}
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		energy -= cost;
		BlockPos ladder = new BlockPos(cx, drainY, cz - 2);
		if (level.getBlockState(ladder).isAir()) {
			level.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
		}
		state = State.DRAINING;
		blockedAt = null;
		drainY--;
		Store.changed();
		if (drained()) {
			tellOwner(level, Component.literal("The cofferdam is dry. Your offshore rig is drilling into the seabed.").withStyle(ChatFormatting.AQUA));
			Cmd.sound(level, "minecraft:block.bubble_column.upwards_inside", modelX(), modelY(), modelZ(), 0.8f, 0.8f);
		}
	}

	// ---------------------------------------------------------------- hoppers

	/** How often the rig hands items to hoppers next to it, and how many per hopper each time. */
	static final int HOPPER_TICKS = 8;
	static final int HOPPER_BATCH = 8;
	/** Hoppers count when they stand around the shaft, within this many blocks of its middle. */
	static final int HOPPER_REACH = 4;

	private final List<BlockPos> hoppers = new ArrayList<>();
	private int hopperScan = -1;

	/** Is this spot where a rig looks for output hoppers? Around the shaft (not over it), on the ground or one above. */
	boolean isHopperSpot(BlockPos pos) {
		int dx = Math.abs(pos.getX() - cx);
		int dz = Math.abs(pos.getZ() - cz);
		boolean overShaft = dx <= 2 && dz <= 2;
		return !overShaft && dx <= HOPPER_REACH && dz <= HOPPER_REACH && (pos.getY() == floor() || pos.getY() == floor() + 1);
	}

	/**
	 * Hands the holds' contents to hoppers standing around the shaft: ores first, then stone. The hoppers pass it on
	 * the vanilla way, so a hopper line can carry it to a chest, a Cargo Train Pickup Station, anything.
	 */
	void feedHoppers(ServerLevel level) {
		if (hopperScan < 0 || age - hopperScan >= 100) {
			hopperScan = age;
			hoppers.clear();
			for (int dx = -HOPPER_REACH; dx <= HOPPER_REACH; dx++) {
				for (int dz = -HOPPER_REACH; dz <= HOPPER_REACH; dz++) {
					for (int y = floor(); y <= floor() + 1; y++) {
						BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
						if (isHopperSpot(pos) && level.isLoaded(pos) && isHopper(level, pos)) {
							hoppers.add(pos);
						}
					}
				}
			}
		}
		for (BlockPos pos : hoppers) {
			if (!(level.getBlockEntity(pos) instanceof Container hopper) || !isHopper(level, pos)) {
				continue;
			}
			int budget = HOPPER_BATCH;
			for (Container hold : holds()) {
				budget -= move(hold, hopper, budget);
				if (budget <= 0) {
					break;
				}
			}
		}
	}

	// ---------------------------------------------------------------- pipes

	/** How many items a rig pushes down its pipeline each time (every {@link #HOPPER_TICKS} ticks). */
	static final int PIPE_BATCH = 16;

	private final Pipes.Link pipes = new Pipes.Link();

	/** The pipeline that starts at a pipe in the ring around the shaft (where hoppers go). */
	Pipes.Network pipeline(ServerLevel level) {
		return pipes.get(level, () -> {
			List<BlockPos> ring = new ArrayList<>();
			for (int dx = -HOPPER_REACH; dx <= HOPPER_REACH; dx++) {
				for (int dz = -HOPPER_REACH; dz <= HOPPER_REACH; dz++) {
					for (int y = floor(); y <= floor() + 1; y++) {
						BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
						if (isHopperSpot(pos)) {
							ring.add(pos);
						}
					}
				}
			}
			return ring;
		});
	}

	/**
	 * Pushes the holds down the pipeline: the ore hold into Industrial Ovens on it first (they smelt ores double), then
	 * both holds into the chests, barrels and shulker boxes on it. Nearest first.
	 */
	void feedPipes(ServerLevel level) {
		int budget = PIPE_BATCH;
		Pipes.Network net = pipeline(level);
		for (Oven oven : net.ovens()) {
			budget -= move(ores, oven.input, budget, Oven::doubles);
			if (budget <= 0) {
				return;
			}
		}
		for (BlockPos pos : net.storage()) {
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container box)) {
				continue;
			}
			for (Container hold : holds()) {
				budget -= move(hold, box, budget);
				if (budget <= 0) {
					return;
				}
			}
		}
	}

	private static boolean isHopper(ServerLevel level, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().equals("hopper");
	}

	/** Moves up to n items from one container to another, filling existing stacks first. Returns how many moved. */
	static int move(Container from, Container to, int n) {
		return move(from, to, n, stack -> true);
	}

	/** The same, but only the items that pass the filter. */
	static int move(Container from, Container to, int n, java.util.function.Predicate<ItemStack> which) {
		int moved = 0;
		for (int i = 0; i < from.getContainerSize() && moved < n; i++) {
			ItemStack stack = from.getItem(i);
			if (stack.isEmpty() || !which.test(stack)) {
				continue;
			}
			for (int pass = 0; pass < 2 && moved < n && !stack.isEmpty(); pass++) {
				for (int j = 0; j < to.getContainerSize() && moved < n && !stack.isEmpty(); j++) {
					ItemStack slot = to.getItem(j);
					int max = Math.min(to.getMaxStackSize(), stack.getMaxStackSize());
					if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < max) {
						int k = Math.min(Math.min(stack.getCount(), max - slot.getCount()), n - moved);
						slot.grow(k);
						stack.shrink(k);
						moved += k;
					} else if (pass == 1 && slot.isEmpty() && to.canPlaceItem(j, stack)) {
						int k = Math.min(Math.min(stack.getCount(), max), n - moved);
						to.setItem(j, stack.split(k));
						moved += k;
					}
				}
			}
			if (stack.isEmpty()) {
				from.setItem(i, ItemStack.EMPTY);
			}
		}
		if (moved > 0) {
			from.setChanged();
			to.setChanged();
		}
		return moved;
	}

	/** Ores, ancient debris and raw ore blocks go to the ore hold. */
	static boolean isOre(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.endsWith("_ore") || id.equals("ancient_debris") || (id.startsWith("raw_") && id.endsWith("_block"));
	}

	static boolean fits(Container hold, List<ItemStack> drops) {
		int emptyNeeded = 0;
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			int room = 0;
			for (int i = 0; i < hold.getContainerSize(); i++) {
				ItemStack slot = hold.getItem(i);
				if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, drop)) {
					room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
				}
			}
			if (room < drop.getCount()) {
				emptyNeeded++;
			}
		}
		int empty = 0;
		for (int i = 0; i < hold.getContainerSize(); i++) {
			if (hold.getItem(i).isEmpty()) {
				empty++;
			}
		}
		return empty >= emptyNeeded;
	}

	/** The layer is clear: seal water and lava out of the walls, and put a ladder on the north wall. */
	private void finishLayer(ServerLevel level) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) != 3 && Math.abs(dz) != 3) {
					continue;
				}
				BlockPos wall = new BlockPos(cx + dx, layer, cz + dz);
				BlockState s = level.getBlockState(wall);
				if (!s.getFluidState().isEmpty() && !s.hasBlockEntity()) {
					level.setBlock(wall, Blocks.COBBLESTONE.defaultBlockState(), 3);
				}
			}
		}
		BlockPos behind = new BlockPos(cx, layer, cz - 3);
		if (level.getBlockState(behind).canBeReplaced()) {
			level.setBlock(behind, Blocks.COBBLESTONE.defaultBlockState(), 3);
		}
		BlockPos ladder = new BlockPos(cx, layer, cz - 2);
		if (level.getBlockState(ladder).isAir()) {
			level.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
		}
	}

	private void finish(ServerLevel level) {
		state = State.DONE;
		Store.changed();
		tellOwner(level, Component.literal("Your Drill Rig hit bedrock " + depth() + " blocks down. The shaft is done.").withStyle(ChatFormatting.AQUA));
		Cmd.sound(level, "minecraft:block.anvil.land", modelX(), modelY(), modelZ(), 0.8f, 0.6f);
	}

	private void strike(ServerLevel level, Pockets.Pocket p) {
		if (struck != null && struck == p.key()) {
			return;
		}
		struck = p.key();
		state = State.PUMPING;
		progress = 0;
		Store.changed();
		int buckets = Pockets.left(p);
		tellOwner(level, Component.literal("STRUCK OIL! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("Your Drill Rig hit a pocket of about " + buckets + " buckets, " + depth()
				+ " blocks down. It's pumping.").withStyle(ChatFormatting.YELLOW)));
		Cmd.sound(level, "minecraft:entity.player.levelup", modelX(), modelY() + 2, modelZ(), 1.0f, 0.6f);
		Cmd.particles(level, "minecraft:squid_ink", modelX(), modelY() + 1, modelZ(), 0.6, 0.15, 40);
		UUID who = ownerId();
		if (who != null) {
			Hooks.xp(who, 50.0);
		}
	}

	/** One bucket out of the struck pocket, into the rig's tank. */
	private void pump(ServerLevel level) {
		Pockets.Pocket p = struck == null ? null : Pockets.OPENED.get(struck);
		if (p == null) {
			struck = null;
			state = State.DRILLING;
			return;
		}
		FossilConfig c = FossilConfig.get();
		if (crude >= c.rigTank) {
			Machines.pipeOut(level, this);
			if (crude >= c.rigTank) {
				state = State.TANK_FULL;
				return;
			}
		}
		if (energy < c.pumpCost && !refuel(level, c.pumpCost)) {
			state = State.NO_FUEL;
			return;
		}
		BlockPos oil = Pockets.nearestOil(level, p, new BlockPos(cx, layer, cz));
		if (oil == null || !Pockets.drain(level, oil)) {
			if (oil == null) {
				struck = null;
				state = State.DRILLING;
				Store.changed();
				tellOwner(level, Component.literal("The well's dry. Your Drill Rig is back to drilling.").withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		crude++;
		energy -= c.pumpCost;
		state = State.PUMPING;
		Store.changed();
		UUID who = ownerId();
		if (who != null) {
			Hooks.xp(who, 1.0);
		}
		if (crude % 4 == 0) {
			Cmd.sound(level, "minecraft:block.piston.extend", modelX(), modelY() + 1, modelZ(), 0.6f, 0.5f);
		}
	}

	/**
	 * Sends both holds to the warehouses nearby, if the Warehouse mod is there, and then to the ones near the far ends
	 * of its pipeline. Returns how many items went.
	 */
	long unload(ServerLevel level) {
		long moved = FossilFoolApi.unload(level, center(), holds());
		if (!FossilFoolApi.hasUnloaders()) {
			return moved;
		}
		int reach = FossilFoolApi.reach();
		for (BlockPos end : pipeline(level).ends()) {
			if (ores.isEmpty() && stone.isEmpty()) {
				break;
			}
			if (end.distSqr(center()) > (double) reach * reach) {
				moved += FossilFoolApi.unload(level, end, holds());
			}
		}
		return moved;
	}

	private void tellOwner(ServerLevel level, Component text) {
		UUID who = ownerId();
		ServerPlayer player = who == null ? null : level.getServer().getPlayerList().getPlayer(who);
		if (player != null) {
			player.sendSystemMessage(text);
		}
	}

	// ---------------------------------------------------------------- the model

	/** How far below the model's origin the drill head sits: just above the layer being drilled. */
	double headY() {
		return layer + 1 - modelY();
	}

	static String transformation(double tx, double ty, double tz, double sx, double sy, double sz) {
		return "{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[" + Cmd.f(tx) + "f," + Cmd.f(ty) + "f," + Cmd.f(tz)
			+ "f],scale:[" + Cmd.f(sx) + "f," + Cmd.f(sy) + "f," + Cmd.f(sz) + "f]}";
	}

	private static String transformation(Part p, double dy) {
		return transformation(p.x(), p.y() + dy, p.z(), p.sx(), p.sy(), p.sz());
	}

	String stringTransformation() {
		double bottom = headY() + 0.9;
		return transformation(-0.08, bottom, -0.08, 0.16, Math.max(0.1, STRING_TOP - bottom), 0.16);
	}

	private String display(String block, String extraTag, String transformation) {
		return "{id:\"minecraft:block_display\",block_state:\"" + block + "\",Tags:[\"" + PART_TAG + "\",\"" + tag() + "\""
			+ (extraTag.isEmpty() ? "" : ",\"" + extraTag + "\"") + "],teleport_duration:2,view_range:4f,transformation:" + transformation + "}";
	}

	String summonCommand(UUID rootId) {
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(modelX(), modelY(), modelZ())).append(" {")
			.append(Cmd.uuidNbt(rootId)).append(",Tags:[\"").append(ROOT_TAG).append("\",\"").append(tag()).append("\"],Passengers:[");
		cmd.append("{id:\"minecraft:interaction\",width:3f,height:2.5f,response:1b,Tags:[\"").append(PART_TAG).append("\",\"")
			.append(tag()).append("\",\"").append(HITBOX_TAG).append("\"]}");
		for (Part part : FRAME) {
			cmd.append(",").append(display(part.block(), "", transformation(part, 0)));
		}
		cmd.append(",").append(display("minecraft:iron_block", STRING_TAG, stringTransformation()));
		for (int i = 0; i < HEAD.size(); i++) {
			cmd.append(",").append(display(HEAD.get(i).block(), HEAD_TAG + i, transformation(HEAD.get(i), headY())));
		}
		return cmd.append("]}").toString();
	}

	/** Moves the drill head and string down to the current layer. The client glides them there. */
	void showHead(ServerLevel level) {
		if (root == null || root.isRemoved() || shownLayer == layer) {
			return;
		}
		shownLayer = layer;
		for (Entity part : new ArrayList<>(root.getPassengers())) {
			Integer which = part.getAttached(FossilFoolMod.RIG_PART);
			String nbt = null;
			if (which != null && which == STRING_PART) {
				nbt = stringTransformation();
			} else if (which != null && which >= HEAD_PART && which - HEAD_PART < HEAD.size()) {
				nbt = transformation(HEAD.get(which - HEAD_PART), headY());
			}
			if (nbt != null) {
				Cmd.run(level, "data merge entity " + part.getUUID() + " {transformation:" + nbt + ",start_interpolation:0,interpolation_duration:20}");
			}
		}
	}

	/** Forgets which layer the head was drawn at, so it's moved again (after the model reloads). */
	void headMoved() {
		shownLayer = Integer.MIN_VALUE;
	}
}
