#!/usr/bin/env bash
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "##DUMP-START"
for c in net.minecraft.world.level.block.ColorCollection net.minecraft.world.level.block.AbstractBedBlock net.minecraft.world.level.block.BedBlock net.minecraft.world.level.block.StrawBedBlock; do
  echo "== $c"; javap -cp "$JAR" -p "$c" 2>&1 | grep -vE 'lambda|\$VALUES' | head -40
done
echo "== checkBedExists"
javap -cp "$JAR" -p -c net.minecraft.world.entity.LivingEntity | awk '/boolean checkBedExists\(/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|instanceof|getfield' | head -12
echo "== Items bed fields"; javap -cp "$JAR" -p net.minecraft.world.item.Items | grep -iE 'straw|\bBED\b'
echo "== registry bed ids"; unzip -l "$JAR" 2>/dev/null | grep -ciE 'models/item/(red_bed|straw_bed)' || true
echo "##DUMP-END"
exit 0
