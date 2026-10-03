#!/usr/bin/env bash
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "jar $JAR"
for c in net.minecraft.world.entity.LivingEntity net.minecraft.world.entity.player.Player net.minecraft.server.level.ServerPlayer; do
  echo "===== $c"; javap -cp "$JAR" -p "$c" | grep -iE 'sleep|bed|wake|respawn'
done
echo "===== LivingEntity.checkBedExists / isSleeping"
javap -cp "$JAR" -p -c net.minecraft.world.entity.LivingEntity | awk '/checkBedExists\(|boolean isSleeping\(|void tick\(\)|void stopSleeping\(|void startSleeping\(|getBedOrientation\(/{p=1} p{print} /^$/{p=0}' | head -150
echo "===== Player.tick sleeping"
javap -cp "$JAR" -p -c net.minecraft.world.entity.player.Player | awk '/public void tick\(\)/{p=1} p{print} /^$/{p=0}' | grep -iE 'sleep|invoke|bright' | head -60
echo "===== ServerLevel sleeping"
javap -cp "$JAR" -p net.minecraft.server.level.ServerLevel | grep -iE 'sleep|wakeUp|setDayTime|dayTime'
javap -cp "$JAR" -p net.minecraft.world.level.Level | grep -iE 'dayTime|isBrightOutside|isDarkOutside|isNight'
javap -cp "$JAR" -p net.minecraft.server.players.SleepStatus 2>&1 | head -20
