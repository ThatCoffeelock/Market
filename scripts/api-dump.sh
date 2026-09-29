#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes Market uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | head -1)
echo "Minecraft jar: ${JAR:-not found}"
[ -z "${JAR:-}" ] && exit 0
CP="$JAR"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.inventory.AbstractContainerMenu 'clicked|quickMoveStack|canTakeItemForPickAll|canDragTo|stillValid|removed|clearContainer|sendAllDataToRemote|moveItemStackTo'
dump net.minecraft.world.inventory.ChestMenu 'public ChestMenu|<init>'
dump net.minecraft.commands.CommandSourceStack 'permission|Permission|withLevel|withSuppressedOutput|sendSuccess|getPlayerOrException'
dump net.minecraft.world.item.BlockItem 'updateCustomBlockEntityTag|place'
dump net.minecraft.world.level.Level 'isClientSide|dimension\(|getServer'
dump net.minecraft.world.entity.Entity 'getTags|entityTags|distanceToSqr|isShiftKeyDown|level\(\)|getYRot'
dump net.minecraft.server.level.ServerPlayer 'playNotifySound|openMenu|closeContainer|sendSystemMessage'
dump net.minecraft.nbt.CompoundTag 'getLongOr|getStringOr|contains\('
dump net.minecraft.world.item.ItemStack 'getTags|getRarity|isDamageableItem|getEnchantments|isEnchanted'
dump net.minecraft.world.item.enchantment.ItemEnchantments 'entrySet|keySet|EMPTY'
dump net.minecraft.world.item.component.ItemContainerContents 'EMPTY'
dump net.minecraft.world.entity.player.Inventory 'placeItemBackInInventory'
dump net.minecraft.world.SimpleContainer 'addListener|addItem'
dump net.minecraft.sounds.SoundEvents 'UI_BUTTON_CLICK|EXPERIENCE_ORB_PICKUP|VILLAGER_NO '
dump net.minecraft.server.MinecraftServer 'getWorldPath|createCommandSourceStack|getCommands|halt|overworld'
dump net.minecraft.world.inventory.ClickType
dump net.minecraft.tags.TagKey 'location|public'
