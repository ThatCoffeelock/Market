#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes Sellswords uses,
# so compile errors and mixin target errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
echo "===== mannequin classes"; unzip -l "$MC" | grep -oE 'net/minecraft/world/entity/[A-Za-z/]*(Mannequin|Avatar)[A-Za-z$]*\.class' | sort -u
dump net.minecraft.world.entity.Mob 'goalSelector|targetSelector|getNavigation|getLookControl|getJumpControl|setNoAi|isNoAi|setPersistenceRequired|isLeashed|setTarget|getTarget'
dump net.minecraft.world.entity.ai.goal.GoalSelector 'removeAllGoals|getAvailableGoals'
dump net.minecraft.world.entity.ai.navigation.PathNavigation 'moveTo|stop|isDone'
dump net.minecraft.world.entity.LivingEntity 'hurtServer|getLastHurtByMob|getLastHurtMob|swing\(|setYBodyRot|setYHeadRot|isBaby|heal\(|setInvulnerableTime|getAttribute\('
dump net.minecraft.world.entity.Entity 'getTags|setInvisible|setSilent|isInWater|isInLava|clearFire|getLookAngle|getEyePosition|onGround\(|getRootVehicle|stopRiding|setPos\('
dump net.minecraft.server.level.ServerLevel 'broadcastDamageEvent|getEntity\(|getSeaLevel'
dump net.minecraft.world.level.Level 'broadcastDamageEvent'
dump net.minecraft.world.damagesource.DamageSource 'getSourcePosition|getEntity|getDirectEntity'
dump net.minecraft.world.entity.ai.attributes.Attributes 'MAX_HEALTH|ARMOR|KNOCKBACK|MOVEMENT_SPEED|FOLLOW_RANGE'
dump net.minecraft.world.entity.TamableAnimal 'isTame'
dump net.minecraft.world.entity.monster.Enemy
dump net.minecraft.world.entity.Mob 'isPersistenceRequired|requiresCustomPersistence'
dump net.minecraft.server.level.ServerChunkCache 'broadcast'
dump net.minecraft.network.protocol.game.ClientboundAnimatePacket
dump net.minecraft.world.item.component.SwingAnimation
dump net.minecraft.world.entity.Entity 'Tag'
dump net.minecraft.world.entity.decoration.Mannequin
