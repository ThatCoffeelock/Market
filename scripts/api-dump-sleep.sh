#!/usr/bin/env bash
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "jar $JAR"
javap -cp "$JAR" -p net.minecraft.world.item.Items | grep -iE ' (\w*BED\w*|\w*WOOL\w*|\w*GLASS_PANE\w*|WHITE_\w*|\w*CARPET)[;]' | head -40
javap -cp "$JAR" -p net.minecraft.world.level.block.Blocks | grep -iE ' (\w*BED\w*|\w*WOOL\w*)[;]' | head -40
javap -cp "$JAR" -p net.minecraft.world.level.block.BedBlock | grep -iE 'FACING|PART|OCCUPIED|static'
javap -cp "$JAR" -p net.minecraft.world.entity.LivingEntity | grep -iE 'startSleeping|stopSleeping|getSleepingPos'
javap -cp "$JAR" -p net.minecraft.world.entity.Entity | grep -iE ' startRiding|stopRiding'
javap -cp "$JAR" -p net.minecraft.world.level.block.state.properties.BedPart | head
