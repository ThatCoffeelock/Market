package com.thatcoffeelock.hamlets;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The whole building as one piece. Only the plan, materials, rotation and a seed are saved; the
 * blueprint is re-run from the seed for each chunk it touches, so every chunk agrees on the layout.
 */
final class HamletPiece extends StructurePiece {
	final Plan plan;
	final Folk folk;
	final Style style;
	final Rotation turn;
	final long seed;
	final BlockPos origin;

	HamletPiece(Plan plan, Folk folk, Style style, Rotation turn, long seed, BlockPos origin) {
		super(HamletsMod.PIECE_TYPE, 0, plan.box(origin));
		this.plan = plan;
		this.folk = folk;
		this.style = style;
		this.turn = turn;
		this.seed = seed;
		this.origin = origin;
	}

	HamletPiece(CompoundTag tag) {
		super(HamletsMod.PIECE_TYPE, tag);
		this.plan = Plan.byId(tag.getStringOr("plan", "cottage"));
		this.folk = Folk.byId(tag.getStringOr("folk", "villagers"));
		this.style = Style.byOrdinal(tag.getIntOr("style", 0));
		this.turn = Rotation.values()[Math.floorMod(tag.getIntOr("turn", 0), Rotation.values().length)];
		this.seed = tag.getLongOr("seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("ox", 0), tag.getIntOr("oy", 0), tag.getIntOr("oz", 0));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("plan", plan.id);
		tag.putString("folk", folk.id);
		tag.putInt("style", style.ordinal());
		tag.putInt("turn", turn.ordinal());
		tag.putLong("seed", seed);
		tag.putInt("ox", origin.getX());
		tag.putInt("oy", origin.getY());
		tag.putInt("oz", origin.getZ());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
		BoundingBox box, ChunkPos chunk, BlockPos pivot) {
		Builders.build(new Canvas(level, box, origin, turn, seed, style, folk == Folk.MONSTERS, false), plan);
	}
}
