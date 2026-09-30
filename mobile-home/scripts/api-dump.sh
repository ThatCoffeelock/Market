#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft/Fabric classes Mobile Home uses,
# so compile errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
ATTACH=$(grep -i 'fabric-data-attachment' <<<"$JARS" | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"; echo "Attachment jar: ${ATTACH:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC:${ATTACH:-}"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.server.level.ServerPlayer 'Input|connection|isCreative'
dump net.minecraft.world.entity.player.Input
dump net.minecraft.world.level.Level 'fuelValues|noCollision|getFluidState|registryAccess'
dump net.minecraft.world.level.CollisionGetter 'noCollision'
dump net.minecraft.world.item.component.CookingFuel
dump net.minecraft.core.component.DataComponents FUEL
dump net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt
dump net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity 'uel|urn'
echo "===== ResolvableInt usage in furnace"; javap -cp "$CP" -p -c net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity 2>&1 | grep -nE "ResolvableInt|CookingFuel|Method .*(resolve|burn|Fuel|Context)" | head -40
for c in $(unzip -l "$MC" | grep -oE 'net/minecraft/world/level/storage/loot/providers/number/ints/[A-Za-z$]+\.class' | sed 's/\.class//; s#/#.#g' | sort -u); do dump "$c" 'public|resolve'; done
echo "===== fuel classes"; unzip -l "$MC" | grep -i fuel
dump net.minecraft.world.entity.Entity 'getTags|setPos\(|setYRot|getPassengers|getFirstPassenger|ejectPassengers|stopRiding|discard|clearFire|AirSupply|blockPosition|getVehicle'
dump net.minecraft.world.entity.LivingEntity 'knockback|isDeadOrDying'
dump net.minecraft.world.entity.Mob 'setTarget|getTarget'
dump net.minecraft.world.inventory.CraftingMenu '<init>|stillValid|public'
dump net.minecraft.world.inventory.ChestMenu 'threeRows|public ChestMenu'
dump net.minecraft.world.inventory.ContainerLevelAccess 'create'
dump net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket '<init>|public'
dump net.minecraft.core.UUIDUtil 'IntArray'
dump net.minecraft.core.HolderLookup\$Provider 'createSerializationContext'
dump net.minecraft.world.item.ItemStack 'CODEC'
dump net.minecraft.nbt.CompoundTag 'getStringOr|contains\(|put\('
dump net.minecraft.resources.Identifier 'fromNamespaceAndPath'
dump net.minecraft.server.level.ServerLevel 'getEntity\(|getAllEntities'
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
dump net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry\$Builder
