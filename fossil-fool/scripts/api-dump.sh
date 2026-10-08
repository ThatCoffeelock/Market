#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Fossil Fool uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.block.Block 'getDrops|popResource'
dump net.minecraft.world.level.block.state.BlockBehaviour\$BlockStateBase 'getDestroySpeed|hasBlockEntity|getFluidState|canBeReplaced|isAir'
dump net.minecraft.world.level.Level 'getHeight|setBlock|removeBlock|isLoaded|getGameTime|dimension'
dump net.minecraft.server.level.ServerLevel 'getSeed|getEntity\(|isPositionEntityTicking|getChunkSource'
dump net.minecraft.server.level.ServerChunkCache 'addTicket'
dump net.minecraft.server.level.TicketType 'ENDER_PEARL'
dump net.minecraft.world.level.LevelHeightAccessor 'getMinY|getMaxY'
dump net.minecraft.world.SimpleContainer 'addItem|addListener|canPlaceItem|removeItem|isEmpty|clearContent'
dump net.minecraft.world.Container
dump net.minecraft.world.inventory.ChestMenu '<init>|quickMoveStack'
dump net.minecraft.world.inventory.AbstractContainerMenu 'clicked|canTakeItemForPickAll|canDragTo|sendAllDataToRemote|removed'
dump net.minecraft.world.inventory.MenuType 'GENERIC_9x'
dump net.minecraft.world.inventory.Slot 'index'
dump net.minecraft.world.entity.Entity 'getTags|getPassengers|getVehicle|discard|isRemoved|distanceToSqr|blockPosition'
dump net.minecraft.world.entity.player.Inventory 'getContainerSize|getItem|placeItemBackInInventory|setChanged'
dump net.minecraft.core.component.DataComponents 'MAX_STACK_SIZE|CUSTOM_DATA|ITEM_NAME|ITEM_MODEL|LORE|GLINT'
dump net.minecraft.nbt.CompoundTag 'getStringOr|getIntOr|putString|putInt'
dump net.minecraft.world.level.block.state.properties.BlockStateProperties 'LIT|HORIZONTAL_FACING'
dump net.minecraft.resources.Identifier 'fromNamespaceAndPath|withDefaultNamespace'
dump net.minecraft.commands.Commands 'hasPermission|LEVEL_'
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
dump net.minecraft.server.level.ServerLevel 'recipeAccess|registryAccess'
dump net.minecraft.world.item.crafting.RecipeManager 'getRecipeFor'
dump net.minecraft.world.item.crafting.Recipe 'assemble'
dump net.minecraft.world.item.crafting.SingleRecipeInput
dump net.minecraft.world.item.crafting.RecipeType 'SMELTING'
dump net.minecraft.world.item.crafting.RecipeHolder 'value'
dump net.minecraft.world.level.block.Blocks 'SMOKER|CAULDRON'
dump net.minecraft.world.item.Items 'SMOKER|WATER_BUCKET|RAW_IRON|LIGHTNING'
dump net.minecraft.world.level.block.state.properties.BlockStateProperties 'LEVEL_CAULDRON'
dump net.minecraft.core.BlockPos 'atY|of\(J
