package com.thatcoffeelock.hamlets;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * One structure type for every building. The JSON under data/hamlets/worldgen/structure picks the
 * plan and who lives there, so new variants are just new JSON files.
 */
public final class HamletStructure extends Structure {
	public static final MapCodec<HamletStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Plan.CODEC.fieldOf("plan").forGetter(s -> s.plan),
		Folk.CODEC.fieldOf("inhabitants").forGetter(s -> s.folk)
	).apply(i, HamletStructure::new));

	final Plan plan;
	final Folk folk;

	HamletStructure(StructureSettings settings, Plan plan, Folk folk) {
		super(settings);
		this.plan = plan;
		this.folk = folk;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		int x = ctx.chunkPos().getMiddleBlockX();
		int z = ctx.chunkPos().getMiddleBlockZ();
		ChunkGenerator gen = ctx.chunkGenerator();
		LevelHeightAccessor heights = ctx.heightAccessor();
		RandomState rs = ctx.randomState();

		// Flat, dry land only: sample a 3 x 3 grid over the footprint.
		int r = plan.flatRadius;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int sum = 0;
		for (int dx = -r; dx <= r; dx += r) {
			for (int dz = -r; dz <= r; dz += r) {
				int surface = gen.getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, heights, rs);
				int floor = gen.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, heights, rs);
				if (surface != floor) {
					return Optional.empty(); // water
				}
				min = Math.min(min, surface);
				max = Math.max(max, surface);
				sum += surface;
			}
		}
		if (max - min > plan.maxSlope) {
			return Optional.empty();
		}
		int y = Math.round(sum / 9f) - 1; // the top solid block
		if (y + plan.below <= heights.getMinY() + 4 || y + plan.above >= heights.getMaxY()) {
			return Optional.empty();
		}

		BlockPos origin = new BlockPos(x, y, z);
		Holder<Biome> biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), rs.sampler());
		Style style = Style.forBiome(biome);
		Rotation turn = Rotation.getRandom(ctx.random());
		long seed = ctx.random().nextLong();
		return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new HamletPiece(plan, folk, style, turn, seed, origin))));
	}

	@Override
	public StructureType<?> type() {
		return HamletsMod.STRUCTURE_TYPE;
	}
}
