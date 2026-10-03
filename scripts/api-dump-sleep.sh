#!/usr/bin/env bash
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "##DUMP-START"
echo "== LivingEntity.startSleeping"
javap -cp "$JAR" -p -c net.minecraft.world.entity.LivingEntity | awk '/public boolean startSleeping\(/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|instanceof|if|return|getstatic|iconst' | head -60
echo "== Entity.startRiding(3)"
javap -cp "$JAR" -p -c net.minecraft.world.entity.Entity | awk '/public boolean startRiding\(net.minecraft.world.entity.Entity, boolean, boolean\)/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|instanceof|if|return|getstatic|iload' | head -60
echo "== ServerPlayer.startRiding / Player"
javap -cp "$JAR" -p net.minecraft.server.level.ServerPlayer net.minecraft.world.entity.player.Player | grep -iE 'startRiding|startSleep|canRide'
echo "##DUMP-END"
exit 0
