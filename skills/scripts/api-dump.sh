#!/usr/bin/env bash
# CI diagnostics: prints the real signatures of the Minecraft classes Skills uses,
# so compile errors and mixin target errors after a Minecraft update are quick to fix.
set -uo pipefail
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -viE 'sources')
MC=$(for j in $(grep -i minecraft <<<"$JARS" | grep -viE 'loader|fabric-api|mixin'); do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "Minecraft jar: ${MC:-not found}"
[ -z "${MC:-}" ] && exit 0
CP="$MC"
dump() { echo "===== $1 (filter: ${2:-all})"; javap -cp "$CP" -p "$1" 2>&1 | grep -E "${2:-.}" | head -60; }
dump net.minecraft.world.entity.LivingEntity 'hurtServer|getAttribute\(|isBaby|getMaxHealth|addEffect|removeEffect|getActiveEffects|getItemBySlot'
dump net.minecraft.world.item.ItemStack 'hurtAndBreak|isSameItemSameComponents|copyWithCount|isDamageableItem| is\('
dump net.minecraft.world.level.block.entity.BrewingStandBlockEntity 'doBrew|serverTick'
dump net.minecraft.world.item.BlockItem 'updateCustomBlockEntityTag|place'
dump net.minecraft.world.level.block.Block 'getDrops|popResource'
dump net.minecraft.world.entity.ai.attributes.Attributes 'BLOCK_BREAK|LUCK|OXYGEN|WATER_MOVEMENT|MOVEMENT_SPEED|JUMP'
dump net.minecraft.world.entity.ai.attributes.AttributeInstance 'Modifier'
dump net.minecraft.world.entity.ai.attributes.AttributeModifier
dump net.minecraft.world.entity.ai.attributes.AttributeModifier\$Operation
dump net.minecraft.stats.Stats 'FISH_CAUGHT|ANIMALS_BRED|TRADED_WITH_VILLAGER|ENCHANT_ITEM|ITEM_USED|ITEM_CRAFTED|CUSTOM'
dump net.minecraft.stats.StatType 'get\('
dump net.minecraft.stats.ServerStatsCounter 'getValue'
dump net.minecraft.server.level.ServerPlayer 'getStats|giveExperience|gameMode|level\(\)|connection'
dump net.minecraft.world.entity.player.Player 'totalExperience|experienceLevel|giveExperience'
dump net.minecraft.server.level.ServerPlayerGameMode 'destroyBlock'
dump net.minecraft.world.effect.MobEffectInstance 'public'
dump net.minecraft.world.effect.MobEffect 'isBeneficial|getCategory'
dump net.minecraft.world.item.trading.Merchant
dump net.minecraft.world.item.trading.MerchantOffer 'getBaseCostA|SpecialPriceDiff'
dump net.minecraft.world.level.block.state.StateHolder 'getValue|getProperties'
dump net.minecraft.world.level.block.state.properties.Property 'getName|getPossibleValues'
dump net.minecraft.tags.DamageTypeTags 'IS_PROJECTILE|IS_EXPLOSION|IS_DROWNING'
dump net.minecraft.world.damagesource.DamageSource 'getEntity|getDirectEntity| is\('
dump net.minecraft.server.network.ServerGamePacketListenerImpl 'player'
dump net.minecraft.world.level.chunk.LevelChunk 'class'
dump net.minecraft.core.Holder 'getRegisteredName'
