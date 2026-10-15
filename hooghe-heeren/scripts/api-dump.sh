#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes De Hooghe Heeren uses,
# so compile errors and mixin target errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.item.component.LodestoneTracker
dump net.minecraft.core.GlobalPos 'of\('
dump net.minecraft.world.item.component.ItemLore 'EMPTY|lines'
dump net.minecraft.world.item.component.SwingAnimation 'DEFAULT'
dump net.minecraft.world.entity.LivingEntity 'swing\(|hurtServer|setYBodyRot|setYHeadRot|setHealth|getAttribute\('
dump net.minecraft.world.entity.Mob 'goalSelector|targetSelector|getNavigation|getLookControl|setPersistenceRequired'
dump net.minecraft.world.entity.ai.control.LookControl 'setLookAt'
dump net.minecraft.world.entity.ai.navigation.PathNavigation 'moveTo|stop|isDone'
dump net.minecraft.world.entity.Entity 'teleportTo|setInvisible|isInvisible|getRemovalReason|setYRot|setPos\('
dump net.minecraft.world.level.Level 'isLoaded|getHeight|getSeaLevel|damageSources'
dump net.minecraft.server.level.ServerLevel 'broadcastDamageEvent|getEntity\(|getSeaLevel|players\('
dump net.minecraft.server.MinecraftServer 'getTickCount'
dump net.minecraft.nbt.CompoundTag 'getStringOr'
dump net.minecraft.world.level.block.state.BlockBehaviour\$BlockStateBase 'isSolid|isAir'
dump net.minecraft.world.entity.decoration.Mannequin
