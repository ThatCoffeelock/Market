#!/usr/bin/env bash
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "##DUMP-START"
echo "== AbstractBedBlock.getSleepHeight"
javap -cp "$JAR" -p -c net.minecraft.world.level.block.AbstractBedBlock | awk '/public java.util.OptionalDouble getSleepHeight\(/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|instanceof|if|return|getstatic|ldc|dconst' | head -40
echo "== AbstractBedBlock.getBedRule"
javap -cp "$JAR" -p -c net.minecraft.world.level.block.AbstractBedBlock | awk '/public net.minecraft.world.attribute.BedRule getBedRule\(/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|getstatic|return' | head -20
echo "== BedRule"
javap -cp "$JAR" -p net.minecraft.world.attribute.BedRule | grep -vE 'lambda|\$VALUES' | head -30
echo "== BedBlock.getBedEnvironmentAttribute / StrawBed"
javap -cp "$JAR" -p -c net.minecraft.world.level.block.BedBlock | awk '/getBedEnvironmentAttribute\(\)/{p=1} p{print} /^$/{p=0}' | grep -E 'getstatic|invoke|return' | head -6
echo "== Player.startSleepInBed(AbstractBedBlock...)"
javap -cp "$JAR" -p -c net.minecraft.world.entity.player.Player | awk '/startSleepInBed\(net.minecraft.world.level.block.AbstractBedBlock/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|instanceof|getstatic|return' | head -50
echo "##DUMP-END"
exit 0
