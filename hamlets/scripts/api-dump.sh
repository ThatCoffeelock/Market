#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes Hamlets uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$MC" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.levelgen.structure.Structure 'protected|public|static'
dump net.minecraft.world.level.levelgen.structure.Structure\$GenerationContext
dump net.minecraft.world.level.levelgen.structure.Structure\$GenerationStub
dump net.minecraft.world.level.levelgen.structure.StructurePiece 'protected|public'
dump net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
dump net.minecraft.world.level.levelgen.structure.StructureStart 'public'
dump net.minecraft.world.level.levelgen.structure.BoundingBox 'public'
dump net.minecraft.world.level.chunk.ChunkGenerator 'getBaseHeight|findNearestMapStructure|getBiomeSource'
dump net.minecraft.world.level.chunk.ChunkAccess 'markPosForPostprocessing|getStartForStructure'
dump net.minecraft.world.level.LevelHeightAccessor
dump net.minecraft.world.level.WorldGenLevel
dump net.minecraft.world.level.ServerLevelAccessor
dump net.minecraft.world.entity.EntityType ' create\('
dump net.minecraft.world.entity.Entity 'snapTo|moveTo'
dump net.minecraft.world.entity.Mob 'finalizeSpawn|setPersistenceRequired'
dump net.minecraft.world.RandomizableContainer 'static|getLootTable'
dump net.minecraft.world.level.block.entity.SpawnerBlockEntity 'setEntityId'
dump net.minecraft.world.level.block.Block 'withPropertiesOf|updateFromNeighbourShapes|UPDATE_'
dump net.minecraft.nbt.CompoundTag 'getIntOr|getLongOr|getStringOr'
dump net.minecraft.core.Registry ' get\(|getValue'
dump net.minecraft.world.level.biome.BiomeSource 'getNoiseBiome'
dump net.minecraft.commands.CommandSourceStack 'getRotation|getPosition'
echo "===== classes named like EntityType / DyeColor block helpers"
unzip -l "$MC" | grep -E "net/minecraft/world/entity/EntityTypes?\.class|world/entity/EntityType[A-Za-z]*\.class|level/block/[A-Za-z]*Blocks?\.class|ColoredBlock|DyedBlock|BlockFamil" | awk '{print $4}' | head -30
dump net.minecraft.world.entity.EntityType 'static final.*(VILLAGER|ZOMBIE|SKELETON|IRON_GOLEM|CAT|PILLAGER)'
dump net.minecraft.world.entity.EntityTypes 'VILLAGER|ZOMBIE|SKELETON|IRON_GOLEM|CAT\b|PILLAGER'
dump net.minecraft.world.level.block.Blocks '_BED|BED\b|CARPET|WOOL|DyeColor|static .*(Map|Function|Family)'
dump net.minecraft.world.level.levelgen.RandomState 'public'
dump net.minecraft.world.level.chunk.ChunkAccess 'ost[Pp]rocess'
dump net.minecraft.world.level.chunk.ProtoChunk 'ost[Pp]rocess'
dump net.minecraft.world.level.biome.BiomeSource 'public'
dump net.minecraft.world.level.biome.BiomeResolver
