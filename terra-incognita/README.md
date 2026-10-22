# Terra Incognita

A Fabric mod for Minecraft **26.3** that adds a new dimension: **Terra Incognita**, the unknown southland the VOC kept almost finding. An endless archipelago of jungle, savanna and badlands islands, with mountains rising straight out of a warm sea. It's rich and crawling with illagers. High risk, high reward.

You don't get there through a portal. You get there by sea, with a chart.

It's **server-side only**. Friends join with a plain vanilla client: the dimension is plain data the server sends them, and everything in it is vanilla.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`terra-incognita-<version>.jar`** in the server's `mods/` folder.

It works on an existing world: the dimension is added the first time the server starts with the mod. The islands are generated as players explore them, like the Overworld.

Get the jar from this repo's **Releases** page (the `terra-incognita-latest` pre-release always has the latest green build), or from the **Actions** tab. To build it yourself: `./gradlew build` in `terra-incognita/` (Java 25).

## Getting there

| Step | How |
|---|---|
| **1. Chart** | Craft a **Sea Chart of the Southland**: Map + Compass + Gold Ingot + Emerald (any shape) |
| **2. Put to sea** | Get in a boat, or aboard your Ahoy ship, out on the water |
| **3. Set a course** | Right-click the chart. Hold your course for **30 seconds**: stay aboard, stay on the water, keep the chart. Halfway, the fog comes in |
| **4. Arrive** | You come out of the fog in Terra Incognita, at sea near the same spot: in your own ship (see below), or in a fresh boat |
| **5. Come home** | The same chart, the same way: out to sea, right-click, hold your course |

Everyone aboard something within 12 blocks of whoever holds the chart comes along. That's your crew. A plain boat stays moored at home, and you each arrive in a fresh one. Step ashore or lose the chart and the voyage is off.

### Taking your own ship

Set the course from the deck of your **[Ahoy](../ahoy/README.md)** ship and **the ship comes along**: her name, holds and cargo, refits, guns and bunk beds. On the other side she comes out on the nearest open water with room for her hull, with the captain at the wheel and everyone who was aboard back in a seat. Fill the holds with plunder and sail her home the same way.

- **The hull needs Copper sheathing** (rigging II, from the Shipwright) for those waters. Without it the chart tells you so and nothing happens.
- Only someone who may sail her (her owner, or anyone if she isn't locked) can take her across.
- Everyone aboard comes along, even past the 12 blocks. [Sellswords](../sellswords/README.md) mercenaries on **Follow** catch up a moment later, the way they follow you through a portal, and climb back aboard.
- If there's no water roomy enough near where you come out of the fog, she stays safe in her **Ship in a Bottle**, in the captain's pack, and the crew rows. Launch her on open water.

## What's over there

- **Islands, not continents.** Terra Incognita is the Overworld's own terrain with the sea raised by 13 blocks: the lowlands are drowned, the hills are islands, and the mountains are sea stacks and peaks over 150 blocks high. Shallow turquoise straits run between them.
- **Lands that don't belong together:** jungle, bamboo jungle, sparse jungle, savanna and its plateaus, desert, three kinds of badlands, flower forest, meadow, cherry groves, dark forest, mangrove coasts, beaches, and stony and jagged peaks. Lush caves and dripstone caves are underneath.
- **Everything the Overworld has, and more of it.** The biomes are vanilla ones, so every structure that spawns in the Overworld spawns here too: villages (savanna and desert ones are the locals), jungle temples, desert temples, pillager outposts, woodland mansions in the dark forests, mineshafts in the badlands, shipwrecks, ocean ruins, buried treasure, trial chambers, and whatever your **structure mods** add for those biomes.
- **Illagers. Lots of them.** Every 20 seconds, each player has a fair chance of being found by a band of 2 to 5: pillagers and vindicators, now and then an evoker, rarely a ravager with a rider. They turn up out of sight and come looking, day and night, until there are 12 around you.
- **Plunder.** Every structure chest nobody has opened yet gets extra loot on top of its own: gold, emeralds, iron, often diamonds, sometimes a block of gold, and with [Specerij](../specerij/README.md) a spice cutting. Illagers killed over there drop a purse of emeralds and gold, and evokers and ravagers drop a fat one.
- **Spices.** With Specerij, every spice grows wild somewhere over there: the jungles, mangroves, bamboo, savannas, cherry groves and meadows are all home biomes.

## Works with

- **[Specerij](../specerij/README.md)**: spices grow wild over there, and chests hold cuttings. A Colonycraft Spice Plantation in Terra Incognita counts as being as far from home as it gets: double yield.
- **[Skills](../skills/README.md)**: the crossing trains **Sailing**, and opening a chest of plunder trains **Treasure Hunting**.
- **[Ahoy](../ahoy/README.md)**: your ship makes the crossing too, cargo and all, once she has Copper sheathing.
- **Riches**, **Fuck Illagers** and the rest: their relics, bounties and loot work over there as they do at home.

## Commands

- `/terra`: how it all works
- `/terra chart` (op): a Sea Chart
- `/terra go`, `/terra home` (op): skip the voyage
- `/terra raid` (op): call a band of illagers down on yourself

## Config (`config/terra_incognita.json`)

| Key | Default | Meaning |
|---|---|---|
| `voyageSeconds` | `30` | How long to hold your course |
| `crewRadius` | `12` | How far the crew may be from the chart-holder |
| `shipRigging` | `2` | The rigging refit an Ahoy ship needs to cross (2 is Copper sheathing, 0 lets any ship go) |
| `raidIntervalSeconds`, `raidChance` | `20`, `0.5` | How often a band may find each player, and the odds |
| `maxIllagersNearby`, `maxBandSize` | `12`, `5` | No new bands past this many around you; band size from 2 to this |
| `chestBounty`, `illagerBounty` | `1.0`, `1.0` | Multiply the extra plunder (0 turns it off) |

The sea level is set when the mod is built (`terra_sea_level` in `gradle.properties`, 76 by default).

## Coming next

v1 is the land. Next come the people and their gold: Terra Incognita's own tribes and settlements to raid, illager forts and occupied villages, natural arches and waterfalls, a seventh spice that only grows there, and a new Riches collection. After that, colonies over there that link to your network by sea.

## Notes

- To a vanilla client Terra Incognita is a world with an Overworld sky. Nothing to install.
- CI (`.github/workflows/terra-incognita.yml`) builds the mod and boots a real server. It checks that the dimension loaded with its raised sea, and prints a **text map of 4,096 × 4,096 blocks** of the islands (to judge the shapes by). It also looks for vanilla villages, outposts, temples and wrecks over there, checks which ships may cross, sends an entity across, finds open water to arrive in, calls down a band of illagers and checks they turned up, and fills a chest with plunder.
