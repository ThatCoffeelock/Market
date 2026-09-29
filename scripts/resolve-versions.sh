#!/usr/bin/env bash
# Fills in any gradle.properties value set to "latest" with the newest Fabric release
# that matches minecraft_version. Needs curl + internet access to meta/maven.fabricmc.net.
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" gradle.properties | cut -d= -f2- | tr -d '[:space:]'; }
setprop() { sed -i.bak -E "s|^$1=.*|$1=$2|" gradle.properties && rm -f gradle.properties.bak; }

MC=$(prop minecraft_version)
META=https://meta.fabricmc.net/v2
MAVEN=https://maven.fabricmc.net

echo "Minecraft version: $MC"
if ! curl -fsS "$META/versions/game" | grep -q "\"version\": *\"$MC\""; then
	echo "::error::Fabric does not list Minecraft '$MC'. Newest versions Fabric knows about:"
	curl -fsS "$META/versions/game" | grep -oE '"version": *"[^"]+"' | head -15
	exit 1
fi

if [ "$(prop loader_version)" = "latest" ]; then
	LOADER=$(curl -fsS "$META/versions/loader" | tr '{' '\n' | grep '"stable": *true' | grep -oE '"version": *"[^"]+"' | head -1 | cut -d'"' -f4)
	echo "Fabric Loader: $LOADER"; setprop loader_version "$LOADER"
fi

if [ "$(prop loom_version)" = "latest" ]; then
	LOOM=$(curl -fsS "$MAVEN/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml" | grep -oE '<version>[^<]+</version>' | sed -E 's/<\/?version>//g' | grep -v SNAPSHOT | tail -1)
	echo "Fabric Loom: $LOOM"; setprop loom_version "$LOOM"
fi

if [ "$(prop fabric_api_version)" = "latest" ]; then
	API=$(curl -fsS "$MAVEN/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml" | grep -oE '<version>[^<]+</version>' | sed -E 's/<\/?version>//g' | grep -E "\+${MC//./\\.}$" | tail -1 || true)
	if [ -z "$API" ]; then
		echo "::error::No Fabric API build for Minecraft $MC yet. Latest builds:"
		curl -fsS "$MAVEN/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml" | grep -oE '<version>[^<]+</version>' | tail -10
		exit 1
	fi
	echo "Fabric API: $API"; setprop fabric_api_version "$API"
fi

echo "---- gradle.properties ----"
grep -E '_version=' gradle.properties
