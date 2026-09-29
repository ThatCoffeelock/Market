#!/usr/bin/env bash
# Fills in any gradle.properties value set to "latest" with the newest Fabric release
# that matches minecraft_version. Needs curl, python3 and access to meta/maven.fabricmc.net.
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" gradle.properties | cut -d= -f2- | tr -d '[:space:]'; }
setprop() { sed -i.bak -E "s|^$1=.*|$1=$2|" gradle.properties && rm -f gradle.properties.bak; }

MC=$(prop minecraft_version)
META=https://meta.fabricmc.net/v2
MAVEN=https://maven.fabricmc.net
echo "Minecraft version: $MC"

curl -fsSL "$META/versions/game" | MC="$MC" python3 -c '
import json, os, sys
versions = [v["version"] for v in json.load(sys.stdin)]
if os.environ["MC"] not in versions:
    print("Fabric does not list Minecraft", os.environ["MC"], "- newest:", versions[:10]); sys.exit(1)'

if [ "$(prop loader_version)" = "latest" ]; then
	LOADER=$(curl -fsSL "$META/versions/loader" | python3 -c '
import json, sys
print(next(v["version"] for v in json.load(sys.stdin) if v.get("stable")))')
	echo "Fabric Loader: $LOADER"; setprop loader_version "$LOADER"
fi

maven_versions() {
	curl -fsSL "$1" | python3 -c '
import re, sys
for v in re.findall(r"<version>([^<]+)</version>", sys.stdin.read()): print(v)'
}

if [ "$(prop loom_version)" = "latest" ]; then
	LOOM=$(maven_versions "$MAVEN/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml" | grep -v SNAPSHOT | tail -n 1)
	echo "Fabric Loom: $LOOM"; setprop loom_version "$LOOM"
fi

if [ "$(prop fabric_api_version)" = "latest" ]; then
	APIS=$(maven_versions "$MAVEN/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
	API=$(grep -E "\+${MC//./\\.}$" <<<"$APIS" | tail -n 1 || true)
	if [ -z "$API" ]; then
		echo "No Fabric API build for Minecraft $MC yet. Latest builds:"
		tail -n 10 <<<"$APIS"
		exit 1
	fi
	echo "Fabric API: $API"; setprop fabric_api_version "$API"
fi

echo "---- gradle.properties ----"
grep -E '_version=' gradle.properties
