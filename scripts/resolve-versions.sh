#!/usr/bin/env bash
# Fills in any gradle.properties value set to "latest" with the newest Fabric release
# that matches minecraft_version. Needs curl + internet access to meta/maven.fabricmc.net.
set -euo pipefail
trap 'echo "resolve-versions.sh failed at line $LINENO (exit $?)"' ERR
set -x
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" gradle.properties | cut -d= -f2- | tr -d '[:space:]'; }
setprop() { sed -i.bak -E "s|^$1=.*|$1=$2|" gradle.properties && rm -f gradle.properties.bak; }

MC=$(prop minecraft_version)
META=https://meta.fabricmc.net/v2
MAVEN=https://maven.fabricmc.net

echo "Minecraft version: $MC"
GAMES=$(curl -fsSL "$META/versions/game")
echo "Fabric knows ${#GAMES} bytes of game versions; newest: $(grep -oE '"version": *"[^"]+"' <<<"$GAMES" | sed -n 1,3p | tr "\n" " ")"
if ! grep -qE "\"version\": *\"${MC//./\\.}\"" <<<"$GAMES"; then
	echo "Fabric does not list Minecraft '$MC'. Newest versions Fabric knows about:"
	grep -oE '"version": *"[^"]+"' <<<"$GAMES" | sed -n 1,15p
	exit 1
fi

if [ "$(prop loader_version)" = "latest" ]; then
	LOADERS=$(curl -fsS "$META/versions/loader")
	LOADER=$(tr '{' '\n' <<<"$LOADERS" | grep '"stable": *true' | grep -oE '"version": *"[^"]+"' | sed -n 1p | cut -d'"' -f4)
	echo "Fabric Loader: $LOADER"; setprop loader_version "$LOADER"
fi

if [ "$(prop loom_version)" = "latest" ]; then
	LOOM=$(curl -fsS "$MAVEN/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml" | grep -oE '<version>[^<]+</version>' | sed -E 's/<\/?version>//g' | grep -v SNAPSHOT | tail -1)
	echo "Fabric Loom: $LOOM"; setprop loom_version "$LOOM"
fi

if [ "$(prop fabric_api_version)" = "latest" ]; then
	APIS=$(curl -fsS "$MAVEN/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml" | grep -oE '<version>[^<]+</version>' | sed -E 's/<\/?version>//g')
	API=$(grep -E "\+${MC//./\\.}$" <<<"$APIS" | tail -1 || true)
	if [ -z "$API" ]; then
		echo "No Fabric API build for Minecraft $MC yet. Latest builds:"
		tail -10 <<<"$APIS"
		exit 1
	fi
	echo "Fabric API: $API"; setprop fabric_api_version "$API"
fi

echo "---- gradle.properties ----"
grep -E '_version=' gradle.properties
