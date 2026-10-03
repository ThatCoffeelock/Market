#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes Apocalypse uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$MC" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.Level 'Bright|Day|Night|Dark|Time|Difficulty|isLoaded|getHeight'
dump net.minecraft.server.level.ServerLevel 'destroyBlockProgress|getEntity\(|getCurrentDifficultyAt|getDayTime|Clock'
dump net.minecraft.world.entity.Mob 'getNavigation|setTarget|getTarget|setNoAi|setPersistenceRequired|isPersistenceRequired|setCanPickUpLoot|finalizeSpawn'
dump net.minecraft.world.entity.ai.navigation.PathNavigation 'moveTo|isDone'
dump net.minecraft.world.entity.Entity 'snapTo|setCustomName|getCustomName|blockPosition|getBlockY|distanceToSqr|setInvulnerable'
dump net.minecraft.world.entity.EntitySpawnReason
dump net.minecraft.world.entity.EntityTypes 'ZOMBIE|HUSK|DROWNED|VILLAGER'
dump net.minecraft.world.entity.ai.attributes.Attributes 'MOVEMENT_SPEED|FOLLOW_RANGE'
dump net.minecraft.world.phys.Vec3 'atCenterOf|distanceTo'
dump net.minecraft.world.level.block.Blocks 'OAK_DOOR|COPPER_DOOR|IRON_DOOR|GLASS_PANE|SPRUCE_TRAPDOOR'
dump net.minecraft.core.Holder 'getRegisteredName'
dump net.minecraft.world.effect.MobEffectInstance 'getEffect'
dump net.minecraft.world.entity.SpawnPlacements 'public'
dump net.minecraft.world.entity.Mob 'checkSpawnRules|checkSpawnObstruction'
dump net.minecraft.world.entity.monster.Monster 'static'
