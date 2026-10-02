#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes Havana uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.core.component.DataComponents 'ITEM_MODEL|MAX_DAMAGE|DAMAGE|MAX_STACK_SIZE|CUSTOM_NAME|ITEM_NAME|LORE|GLINT'
dump net.minecraft.world.item.ItemStack 'copyWithCount|setDamageValue|getDamageValue|getMaxDamage|getOrDefault| has\(|getMaxStackSize'
dump net.minecraft.world.item.component.ItemLore 'EMPTY|lines'
dump net.minecraft.world.level.block.CropBlock 'isMaxAge|getAge'
dump net.minecraft.world.entity.item.ItemEntity 'getItem|setItem'
dump net.minecraft.world.level.Level 'destroyBlock|removeBlock|getGameTime|getEntitiesOfClass'
dump net.minecraft.world.entity.Entity 'isUnderWater|getLookAngle|getEyeY|blockPosition'
dump net.minecraft.world.inventory.AbstractContainerMenu 'clicked|quickMoveStack|canTakeItemForPickAll|canDragTo|stillValid|removed|clearContainer|moveItemStackTo|broadcastChanges'
dump net.minecraft.world.inventory.ContainerInput
dump net.minecraft.world.InteractionResult 'static'
dump net.minecraft.nbt.CompoundTag 'getIntOr|getStringOr|putInt|putString'
dump net.minecraft.resources.Identifier 'withDefaultNamespace'
