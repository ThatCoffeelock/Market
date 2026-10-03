package com.thatcoffeelock.hamlets;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Where a blueprint draws. Coordinates are local to the structure's origin: y 0 is the ground, the
 * front door faces +z (south) before rotation. Everything outside {@link #clip} is skipped, so
 * worldgen can run the whole blueprint once per chunk and only the part inside that chunk lands.
 *
 * <p>The blueprint must make exactly the same random calls in every chunk, so nothing here draws
 * from {@link #rng} depending on whether a position is inside the clip.
 */
final class Canvas {
	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final Map<Block, Block[]> WORN = new IdentityHashMap<>();

	static {
		WORN.put(Blocks.COBBLESTONE, new Block[] {Blocks.MOSSY_COBBLESTONE});
		WORN.put(Blocks.COBBLESTONE_STAIRS, new Block[] {Blocks.MOSSY_COBBLESTONE_STAIRS});
		WORN.put(Blocks.COBBLESTONE_SLAB, new Block[] {Blocks.MOSSY_COBBLESTONE_SLAB});
		WORN.put(Blocks.STONE_BRICKS, new Block[] {Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS});
		WORN.put(Blocks.STONE_BRICK_STAIRS, new Block[] {Blocks.MOSSY_STONE_BRICK_STAIRS});
		WORN.put(Blocks.STONE_BRICK_SLAB, new Block[] {Blocks.MOSSY_STONE_BRICK_SLAB});
		WORN.put(Blocks.COBBLED_DEEPSLATE, new Block[] {Blocks.DEEPSLATE});
		WORN.put(Blocks.DEEPSLATE_BRICKS, new Block[] {Blocks.CRACKED_DEEPSLATE_BRICKS});
		WORN.put(Blocks.DEEPSLATE_TILES, new Block[] {Blocks.CRACKED_DEEPSLATE_TILES});
		WORN.put(Blocks.SMOOTH_SANDSTONE, new Block[] {Blocks.SANDSTONE});
		WORN.put(Blocks.CUT_SANDSTONE, new Block[] {Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE});
	}

	final WorldGenLevel level;
	final BoundingBox clip;
	final BlockPos origin;
	final Rotation turn;
	final RandomSource rng;
	final Style style;
	final boolean ruined;
	private final long seed;
	private final boolean live;
	private final List<BlockPos> reshape = new ArrayList<>();
	int spawned;

	/**
	 * @param live true when building into a running world (commands, tests): neighbour shapes are
	 *             fixed right away instead of when the chunk finishes generating.
	 */
	Canvas(WorldGenLevel level, BoundingBox clip, BlockPos origin, Rotation turn, long seed, Style style, boolean ruined, boolean live) {
		this.level = level;
		this.clip = clip;
		this.origin = origin;
		this.turn = turn;
		this.seed = seed;
		this.rng = RandomSource.create(seed);
		this.style = style;
		this.ruined = ruined;
		this.live = live;
	}

	BlockPos at(int x, int y, int z) {
		int wx;
		int wz;
		switch (turn) {
			case CLOCKWISE_90 -> {
				wx = -z;
				wz = x;
			}
			case CLOCKWISE_180 -> {
				wx = -x;
				wz = -z;
			}
			case COUNTERCLOCKWISE_90 -> {
				wx = z;
				wz = -x;
			}
			default -> {
				wx = x;
				wz = z;
			}
		}
		return origin.offset(wx, y, wz);
	}

	void set(int x, int y, int z, BlockState state) {
		BlockPos pos = at(x, y, z);
		if (!clip.isInside(pos)) {
			return;
		}
		BlockState s = state.rotate(turn);
		level.setBlock(pos, s, FLAGS);
		Block b = s.getBlock();
		if (b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock) {
			if (live) {
				reshape.add(pos);
			} else {
				level.getChunk(pos).markPosForPostProcessing(pos);
			}
		}
	}

	void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
		fill(x1, y1, z1, x2, y2, z2, AIR);
	}

	/** Like fill, but every block goes through {@link #worn}. */
	void wornFill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state, float holes) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, worn(state, holes));
				}
			}
		}
	}

	/** In a ruin: sometimes a hole, often moss or cracks. Otherwise the block as is. */
	BlockState worn(BlockState state, float holes) {
		if (!ruined) {
			return state;
		}
		if (rng.nextFloat() < holes) {
			return AIR;
		}
		Block[] options = WORN.get(state.getBlock());
		if (options != null && rng.nextFloat() < 0.4f) {
			return options[rng.nextInt(options.length)].withPropertiesOf(state);
		}
		return state;
	}

	/** Fills air, water and plants under the area (from y -1 down) until it hits solid ground. */
	void foundation(int x1, int z1, int x2, int z2, BlockState state) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
				for (int y = -1; y >= -12; y--) {
					BlockPos pos = at(x, y, z);
					if (!clip.isInside(pos) || pos.getY() <= level.getMinY()) {
						break;
					}
					BlockState now = level.getBlockState(pos);
					if (!now.canBeReplaced() && now.getFluidState().isEmpty()) {
						break;
					}
					set(x, y, z, state);
				}
			}
		}
	}

	void window(int x, int y, int z) {
		if (!ruined) {
			set(x, y, z, st(Blocks.GLASS_PANE));
			return;
		}
		float r = rng.nextFloat();
		set(x, y, z, r < 0.45f ? AIR : r < 0.7f ? st(Blocks.COBWEB) : st(Blocks.GLASS_PANE));
	}

	void door(int x, int y, int z, Block door, Direction facing) {
		BlockState s = facing(door, facing);
		set(x, y, z, s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		set(x, y + 1, z, s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** Foot at (x, y, z), head one block towards {@code head}. */
	void bed(int x, int y, int z, Block bed, Direction head) {
		BlockState s = facing(bed, head);
		set(x, y, z, s.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		set(x + head.getStepX(), y, z + head.getStepZ(), s.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	void ladder(int x, int y1, int y2, int z, Direction facing) {
		for (int y = y1; y <= y2; y++) {
			set(x, y, z, facing(Blocks.LADDER, facing));
		}
	}

	void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> loot) {
		container(x, y, z, facing(Blocks.CHEST, facing), loot);
	}

	void barrel(int x, int y, int z, ResourceKey<LootTable> loot) {
		container(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), loot);
	}

	private void container(int x, int y, int z, BlockState state, ResourceKey<LootTable> loot) {
		set(x, y, z, state);
		BlockPos pos = at(x, y, z);
		if (clip.isInside(pos)) {
			RandomizableContainer.setBlockEntityLootTable(level, local(pos), pos, loot);
		}
	}

	void spawner(int x, int y, int z, EntityType<?> type) {
		set(x, y, z, st(Blocks.SPAWNER));
		BlockPos pos = at(x, y, z);
		if (clip.isInside(pos) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
			spawner.setEntityId(type, local(pos));
		}
	}

	/** A random source that depends only on the position, never on which chunk is generating. */
	private RandomSource local(BlockPos pos) {
		return RandomSource.create(seed ^ pos.asLong());
	}

	void villager(int x, int y, int z, boolean baby) {
		Entity e = spawn(EntityTypes.VILLAGER, x, y, z);
		if (baby && e instanceof AgeableMob mob) {
			mob.setAge(-24000);
		}
	}

	/** Spawns a persistent mob standing on (x, y - 1, z). */
	<T extends Entity> T spawn(EntityType<T> type, int x, int y, int z) {
		BlockPos pos = at(x, y, z);
		float yaw = rng.nextFloat() * 360f;
		if (!clip.isInside(pos)) {
			return null;
		}
		T entity = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (entity == null) {
			return null;
		}
		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		if (entity instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
			mob.setPersistenceRequired();
		}
		level.addFreshEntityWithPassengers(entity);
		spawned++;
		return entity;
	}

	/** Live builds: connect fences, panes, walls and stairs to their neighbours. */
	void finish() {
		for (BlockPos pos : reshape) {
			BlockState now = level.getBlockState(pos);
			BlockState shaped = Block.updateFromNeighbourShapes(now, level, pos);
			if (shaped != now) {
				level.setBlock(pos, shaped, FLAGS);
			}
		}
		reshape.clear();
	}

	<T> T pick(List<T> options) {
		return options.get(rng.nextInt(options.size()));
	}

	static BlockState st(Block block) {
		return block.defaultBlockState();
	}

	/** Faces a block (chest, stairs, door, ladder, torch, workstation...) and stands it on the floor. */
	static BlockState facing(Block block, Direction d) {
		BlockState s = block.defaultBlockState();
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		} else if (s.hasProperty(BlockStateProperties.FACING)) {
			s = s.setValue(BlockStateProperties.FACING, d);
		}
		if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) {
			s = s.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
		}
		return s;
	}

	static BlockState stairs(Block block, Direction d, boolean top) {
		return facing(block, d).setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block block, boolean top) {
		return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState hanging(Block lantern) {
		return lantern.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	static BlockState crop(Block crop, int age) {
		BlockState s = crop.defaultBlockState();
		if (s.hasProperty(BlockStateProperties.AGE_7)) {
			return s.setValue(BlockStateProperties.AGE_7, Math.min(age, 7));
		}
		if (s.hasProperty(BlockStateProperties.AGE_3)) {
			return s.setValue(BlockStateProperties.AGE_3, Math.min(age, 3));
		}
		return s;
	}

	static BlockState farmland() {
		return Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
	}

	static Direction dir(int dx, int dz) {
		if (dx > 0) {
			return Direction.EAST;
		}
		if (dx < 0) {
			return Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
