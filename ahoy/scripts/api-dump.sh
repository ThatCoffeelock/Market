#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Mobile Home uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.server.level.ServerPlayer 'Input|connection|isCreative'
dump net.minecraft.commands.arguments.blocks.BlockStateParser 'public static'
dump net.minecraft.world.level.block.Block 'UPDATE_|updateFromNeighbourShapes|popResource'
dump net.minecraft.world.level.block.state.BlockBehaviour\$BlockStateBase 'rotate|getRenderShape|canOcclude|canBeReplaced|getCollisionShape|isAir'
dump net.minecraft.world.level.block.RenderShape
dump net.minecraft.world.level.block.Rotation
dump net.minecraft.world.entity.Entity ' pick\(|getYRot|removeAttached'
dump net.minecraft.world.level.Level 'removeBlockEntity|setBlock\(|isLoaded|getBlockEntity'
dump net.minecraft.world.level.material.FluidState 'isSource|is\('
dump net.minecraft.world.Container 'removeItemNoUpdate|getContainerSize'
dump net.minecraft.core.registries.BuiltInRegistries BLOCK
dump net.minecraft.core.DefaultedRegistry 'getValue'
dump net.minecraft.server.level.ServerLevel 'players\(\)|getAllEntities'
dump net.minecraft.server.MinecraftServer 'getAllLevels'
dump net.minecraft.server.level.ServerPlayerGameMode 'useItem|useItemOn'
dump net.minecraft.world.entity.player.Player 'interactOn|fishing|stopUsingItem|closeContainer|entityInteractionRange|blockInteractionRange'
dump net.minecraft.world.entity.Entity 'interactAt|getPickRadius|isPickable|pick\(|getRootVehicle'
dump net.minecraft.world.entity.ai.attributes.Attributes 'INTERACTION_RANGE'
dump net.minecraft.world.phys.EntityHitResult '<init>|getEntity'
dump net.minecraft.world.level.Level 'getEntities\('
dump net.fabricmc.fabric.api.entity.FakePlayer 'get\('
