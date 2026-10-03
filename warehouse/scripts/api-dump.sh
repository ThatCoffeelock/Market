#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Warehouse uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.block.BaseRailBlock 'getShapeProperty|isRail'
dump net.minecraft.world.level.block.state.properties.RailShape
dump net.minecraft.server.level.ServerChunkCache 'Ticket|hasChunk'
dump net.minecraft.server.level.TicketType 'static|public'
dump net.minecraft.world.level.ChunkPos '<init>|public static'
dump net.minecraft.world.level.Level 'isLoaded|getEntities\(|getBlockEntity'
dump net.minecraft.world.Nameable
dump net.minecraft.world.Container 'getMaxStackSize|canPlaceItem|isEmpty|countItem|setChanged'
dump net.minecraft.world.item.ItemStack 'isSameItemSameComponents|getMaxStackSize|split|CODEC'
dump net.minecraft.world.level.block.Block 'popResource'
dump net.minecraft.core.BlockPos 'asLong|of\(|containing|offset|toShortString'
dump net.minecraft.world.damagesource.DamageTypes 'IN_WALL'
dump net.minecraft.world.damagesource.DamageSource ' is\('
dump net.minecraft.server.level.ServerPlayer 'Input|connection|isCreative'
dump net.minecraft.world.entity.player.Input
dump net.minecraft.world.entity.Entity 'setPos\(|setYRot|getPassengers|getFirstPassenger|ejectPassengers|stopRiding|discard|getVehicle|position\(\)'
dump net.minecraft.world.inventory.ChestMenu '<init>|clicked|stillValid'
dump net.minecraft.world.inventory.ContainerInput
dump net.minecraft.world.item.Items 'FURNACE_MINECART|GOAT_HORN|YELLOW_STAINED_GLASS_PANE'
dump net.minecraft.core.component.DataComponents 'MAX_STACK_SIZE|CUSTOM_DATA|CUSTOM_NAME|ITEM_NAME|LORE|GLINT'
dump net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket '<init>|public'
dump net.minecraft.core.UUIDUtil 'IntArray'
dump net.minecraft.nbt.CompoundTag 'getStringOr|putString'
dump net.minecraft.resources.Identifier 'fromNamespaceAndPath'
dump net.minecraft.server.level.ServerLevel 'getEntity\(|getAllEntities'
dump net.minecraft.commands.Commands 'hasPermission|LEVEL_'
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry\$Builder
dump net.minecraft.world.inventory.AbstractContainerMenu 'Carried|broadcastChanges|sendAllDataToRemote|quickMoveStack|slots'
dump net.minecraft.world.item.component.ItemLore
dump net.minecraft.network.chat.PlayerChatMessage 'signedContent|public'
dump net.minecraft.world.item.ItemStack 'getHoverName|copyWithCount|setDamageValue|hashItem'
dump net.minecraft.world.entity.player.Inventory 'placeItemBackInInventory|getItem|setItem'
dump net.minecraft.core.HolderLookup\$Provider 'createSerializationContext'
FAPI=$(grep -i 'fabric-message-api' <<<"$JARS" | sed -n 1p)
[ -n "${FAPI:-}" ] && javap -cp "$FAPI:$CP" -p 'net.fabricmc.fabric.api.message.v1.ServerMessageEvents$AllowChatMessage' 2>&1 | head -20
