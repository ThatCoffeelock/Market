#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Burlap Sack uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.storage.TagValueOutput 'static|buildResult'
dump net.minecraft.util.ProblemReporter 'DISCARDING|static'
dump net.minecraft.world.entity.Entity 'saveWithoutId|registryAccess|level\(\)|discard|stopRiding|ejectPassengers|getType|isRemoved'
dump net.minecraft.world.entity.EntityType 'getKey|VILLAGER|WANDERING_TRADER|IRON_GOLEM'
dump net.minecraft.world.entity.Mob 'setTarget|getTarget'
dump net.minecraft.world.entity.LivingEntity 'setLastHurtByMob'
dump net.minecraft.nbt.CompoundTag 'getStringOr|getIntOr|getCompoundOrEmpty|getListOrEmpty|putIntArray|contains\(|remove\('
dump net.minecraft.nbt.ListTag 'getStringOr|copy|add\('
dump net.minecraft.core.component.DataComponents 'BUNDLE_CONTENTS|CUSTOM_DATA|ITEM_NAME|LORE|GLINT|MAX_STACK_SIZE'
dump net.minecraft.world.item.ItemStack 'remove\(|set\('
dump net.minecraft.server.level.ServerPlayer 'setItemInHand|getItemInHand|isCreative'
echo "===== Villager classes"
unzip -l "$MC" | grep -E '/(Villager|AbstractVillager|WanderingTrader|IronGolem)\.class'
V=$(unzip -l "$MC" | grep -oE 'net/minecraft/[a-z/]+/Villager\.class' | sed -n 1p | sed 's/\.class$//; s#/#.#g')
[ -n "$V" ] && dump "$V" 'releaseAllPois|isTrading'
