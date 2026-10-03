#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Flintlock uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.level.Level 'getSeaLevel|getChunkSource|getRandom'
dump net.minecraft.world.level.chunk.ChunkSource 'hasChunk'
dump net.minecraft.world.level.LevelHeightAccessor 'isOutsideBuildHeight'
dump net.minecraft.world.level.block.state.BlockBehaviour\$BlockStateBase 'getCollisionShape|getFluidState'
dump net.minecraft.world.phys.AABB 'clip|inflate|contains|<init>'
dump net.minecraft.world.phys.Vec3 'scale|add|subtract|length|distanceTo|cross|normalize|ZERO'
dump net.minecraft.util.RandomSource 'nextGaussian'
dump net.minecraft.world.entity.Entity '[Ii]nvulnerable|[Hh]urt|[Ii]mpulse|push|DeltaMovement|getLookAngle|getEyePosition|getEyeY|isSpectator'
dump net.minecraft.world.entity.LivingEntity '[Ii]nvulnerable|[Hh]urt|knockback|getAttributeValue|getHealth|getMaxHealth'
dump net.minecraft.world.entity.ai.attributes.Attributes 'KNOCKBACK'
dump net.minecraft.world.entity.player.Inventory 'getContainerSize|getItem|placeItemBackInInventory|setChanged'
dump net.minecraft.world.item.ItemStack 'remove\(|set\(|has\(|isDamageableItem|getMaxStackSize'
dump net.minecraft.world.item.component.ChargedProjectiles 'public static|EMPTY'
dump net.minecraft.core.component.DataComponents 'MAX_STACK_SIZE|MAX_DAMAGE| DAMAGE|CUSTOM_DATA|ITEM_NAME|ITEM_MODEL|LORE|CHARGED_PROJECTILES'
dump net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket '<init>|public'
dump net.minecraft.nbt.CompoundTag 'getStringOr|getBooleanOr|putString|putBoolean'
dump net.minecraft.resources.Identifier 'withDefaultNamespace'
dump net.minecraft.server.level.ServerLevel 'getEntity\(|getEntitiesOfClass'
dump net.minecraft.commands.Commands 'hasPermission|LEVEL_'
dump net.fabricmc.fabric.api.event.player.UseItemCallback
