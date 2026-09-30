package com.thatcoffeelock.hamlets;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** Building materials, picked from the biome a structure stands in. */
enum Style {
	OAK(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_SLAB, Blocks.OAK_DOOR, Blocks.OAK_FENCE,
		Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
		Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.RED_BED, Blocks.GRASS_BLOCK, Blocks.DIRT, BuiltInLootTables.VILLAGE_PLAINS_HOUSE),
	SPRUCE(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE,
		Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
		Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.BLUE_BED, Blocks.GRASS_BLOCK, Blocks.DIRT, BuiltInLootTables.VILLAGE_TAIGA_HOUSE),
	BIRCH(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.BIRCH_SLAB, Blocks.BIRCH_DOOR, Blocks.BIRCH_FENCE,
		Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
		Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.YELLOW_BED, Blocks.GRASS_BLOCK, Blocks.DIRT, BuiltInLootTables.VILLAGE_PLAINS_HOUSE),
	DARK(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_FENCE,
		Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB,
		Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.BLACK_BED, Blocks.GRASS_BLOCK, Blocks.DIRT, BuiltInLootTables.VILLAGE_TAIGA_HOUSE),
	ACACIA(Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG, Blocks.ACACIA_SLAB, Blocks.ACACIA_DOOR, Blocks.ACACIA_FENCE,
		Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
		Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ORANGE_BED, Blocks.GRASS_BLOCK, Blocks.DIRT, BuiltInLootTables.VILLAGE_SAVANNA_HOUSE),
	SANDSTONE(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_SLAB, Blocks.BIRCH_DOOR, Blocks.BIRCH_FENCE,
		Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB,
		Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.GREEN_BED, Blocks.SAND, Blocks.SANDSTONE, BuiltInLootTables.VILLAGE_DESERT_HOUSE);

	/** Walls of houses and floors. */
	final Block planks;
	/** Corner posts. */
	final Block log;
	final Block slab;
	final Block door;
	final Block fence;
	/** Rough stone: plinths and foundations. */
	final Block stone;
	/** Castle and dungeon masonry. */
	final Block bricks;
	final Block brickStairs;
	final Block brickSlab;
	final Block roof;
	final Block roofSlab;
	final Block bed;
	final Block ground;
	final Block fill;
	final ResourceKey<LootTable> houseLoot;

	Style(Block planks, Block log, Block slab, Block door, Block fence, Block stone, Block bricks, Block brickStairs, Block brickSlab,
		Block roof, Block roofSlab, Block bed, Block ground, Block fill, ResourceKey<LootTable> houseLoot) {
		this.planks = planks;
		this.log = log;
		this.slab = slab;
		this.door = door;
		this.fence = fence;
		this.stone = stone;
		this.bricks = bricks;
		this.brickStairs = brickStairs;
		this.brickSlab = brickSlab;
		this.roof = roof;
		this.roofSlab = roofSlab;
		this.bed = bed;
		this.ground = ground;
		this.fill = fill;
		this.houseLoot = houseLoot;
	}

	static Style forBiome(Holder<Biome> biome) {
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			return SANDSTONE;
		}
		if (biome.is(BiomeTags.IS_SAVANNA)) {
			return ACACIA;
		}
		if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.SWAMP)) {
			return DARK;
		}
		if (biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.GROVE)) {
			return SPRUCE;
		}
		if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			return BIRCH;
		}
		return OAK;
	}

	static Style byOrdinal(int i) {
		Style[] all = values();
		return all[Math.floorMod(i, all.length)];
	}
}
