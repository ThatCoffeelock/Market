#!/usr/bin/env bash
set -uo pipefail
JAR=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -viE 'sources|loader|fabric-api|mixin' \
	| while read -r j; do unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class' && echo "$j"; done | sed -n 1p)
echo "##DUMP-START"
echo "== Player.startSleeping"
javap -cp "$JAR" -p -c net.minecraft.world.entity.player.Player | awk '/public boolean startSleeping\(net.minecraft.core.BlockPos\)/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|instanceof|if|return|getstatic' | head -40
echo "== ServerPlayer.startSleeping?"
javap -cp "$JAR" -p -c net.minecraft.server.level.ServerPlayer | awk '/startSleeping\(/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|if|return' | head -20
echo "== BedBlock getShape"
javap -cp "$JAR" -p -c net.minecraft.world.level.block.BedBlock | awk '/VoxelShape getShape\(/{p=1} p{print} /^$/{p=0}' | grep -E 'invoke|getstatic|return' | head -12
echo "##DUMP-END"
exit 0
