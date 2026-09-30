#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Cannon uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.Level 'explode|getSeaLevel|noCollision|getChunkSource'
dump net.minecraft.world.level.Level\$ExplosionInteraction
dump net.minecraft.world.level.chunk.ChunkSource 'hasChunk'
dump net.minecraft.world.level.LevelHeightAccessor 'isOutsideBuildHeight'
dump net.minecraft.world.level.block.state.BlockBehaviour\$BlockStateBase 'getCollisionShape|getFluidState|canBeReplaced'
dump net.minecraft.world.phys.AABB 'clip|inflate|<init>'
dump net.minecraft.world.phys.Vec3 'scale|add|subtract|length|distanceTo'
dump net.minecraft.core.SectionPos 'blockToSectionCoord'
dump net.minecraft.core.BlockPos 'betweenClosed|containing'
dump net.minecraft.server.level.ServerPlayer 'Input|connection|isCreative'
dump net.minecraft.world.entity.player.Input
dump net.minecraft.world.entity.player.Inventory 'getContainerSize|getItem|placeItemBackInInventory|setChanged'
dump net.minecraft.world.entity.Entity 'getTags|setPos\(|setYRot|getXRot|getPassengers|getFirstPassenger|ejectPassengers|stopRiding|discard|getVehicle|isShiftKeyDown'
dump net.minecraft.core.component.DataComponents 'MAX_STACK_SIZE|CUSTOM_DATA|ITEM_NAME|LORE|GLINT'
dump net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket '<init>|public'
dump net.minecraft.core.UUIDUtil 'IntArray'
dump net.minecraft.nbt.CompoundTag 'getStringOr|putString'
dump net.minecraft.resources.Identifier 'fromNamespaceAndPath'
dump net.minecraft.server.level.ServerLevel 'getEntity\(|getAllEntities'
dump net.minecraft.commands.Commands 'hasPermission|LEVEL_'
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry\$Builder
