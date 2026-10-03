#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Overenchant uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.item.enchantment.Enchantable
dump net.minecraft.world.item.enchantment.Enchantments 'POWER|PUNCH|FLAME|QUICK_CHARGE|PIERCING|MULTISHOT|INFINITY|SHARPNESS|UNBREAKING|MENDING'
dump net.minecraft.world.item.enchantment.ItemEnchantments 'getLevel|keySet|isEmpty|size|entrySet'
dump net.minecraft.world.item.enchantment.Enchantment 'getMaxLevel|definition|description|canEnchant|isSupportedItem|isPrimaryItem'
dump 'net.minecraft.world.item.enchantment.Enchantment$EnchantmentDefinition' 'maxLevel'
dump net.minecraft.core.Holder 'is\(|getRegisteredName|value'
dump net.minecraft.core.HolderLookup 'getOrThrow|listElements|lookupOrThrow'
dump net.minecraft.core.HolderLookup\$RegistryLookup 'getOrThrow|listElements'
dump net.minecraft.core.RegistryAccess 'lookupOrThrow'
dump net.minecraft.core.component.DataComponents 'ENCHANTABLE|ENCHANTMENTS'
dump net.minecraft.world.item.ItemStack 'isEnchantable|isEnchanted|getEnchantments|enchant\('
dump net.minecraft.world.entity.Entity 'igniteForSeconds|igniteForTicks|setRemainingFireTicks|isOnFire|getRemainingFireTicks'
dump net.minecraft.world.entity.LivingEntity 'setItemSlot|getMainHandItem'
dump net.minecraft.resources.ResourceKey 'identifier|location'
dump net.minecraft.commands.Commands 'hasPermission|LEVEL_'
dump net.minecraft.commands.CommandSourceStack 'registryAccess|sendSuccess|sendSystemMessage'
dump net.minecraft.server.MinecraftServer 'getCommands|createCommandSourceStack|overworld'
