# Riches

A Fabric mod for Minecraft **26.3** for people who made it and want everyone to know.

- **A walk-in vault.** Put a **Vault Ledger** on the floor and your [Market](../README.md) balance piles up around it as a mound of gold, coins and ingots on top. ₥1,000 is a scattering, ₥100,000 is waist-deep, ₥1,000,000 is up to your eyebrows. Spend it and the pile shrinks. Walk in and you **wade**: slow, clinking, glorious.
- **Vault Doors** open only for you and the players you trust, and swing shut by themselves after five seconds. Redstone won't keep them open either.
- **Display Cases and Pedestals.** Put anything in one and it floats and slowly turns, under a brass plaque that says what it is, who put it there and when.
- **24 relics in 4 collections**, each one of a kind on the server: the Crown of the Illager King, the Dragon's Tooth, a Mosquito in Amber… Whoever finds one first owns the only one. Show a whole collection in your own cases and the **Royal Society pays you ₥5,000**.

It's **server-side only**. Friends join with a plain vanilla client. The pile, the floating items and the plaques are vanilla display entities, and the blocks are a lodestone, an iron door, glass and a quartz pillar. Needs the **Market** mod (it's in the [LIB Pack](../lib-pack/README.md)).

## Install

Put **Fabric API**, **`market-<version>.jar`** (1.1.0 or newer) and **`riches-<version>.jar`** in your `mods/` folder. Get the jar from the `riches-latest` release, or from the **Actions** tab (the `riches-mod` artifact). To build it yourself, run `./gradlew build` in the repo root (Market), then in `riches/` (needs Java 25).

## Crafting

| Item | Recipe |
|---|---|
| **Vault Ledger** | `Gold Block, Iron Block, Gold Block` / `Iron Block, Lodestone, Iron Block` / `Gold Block, Iron Block, Gold Block` |
| **Vault Door** | Iron Door surrounded by 8 Iron Ingots |
| **Display Case** ×8 | 8 Glass around a Gold Nugget |
| **Pedestal** ×2 | Gold Nugget on top of 2 Quartz Pillars |

## How to play

1. **Build a vault room** with a clear floor. Put the **Vault Ledger** in the middle. The pile spreads 3 blocks around it (a 7×7 floor) and goes around walls and pillars.
2. **Hang a Vault Door.** Only you, and the players you `/riches trust`, can open it or break it. Right-click the ledger to see how much is in there.
3. **Get rich.** The pile updates every few seconds as your balance changes.
4. **Put things on show.** Right-click a Display Case or Pedestal with an item. Right-click with an empty hand to take it back. Other players get to look but not touch.
5. **Hunt relics.** `/riches relics` lists all 24: who found the ones that are found, and a hint for the rest.

### The relics

| Collection | Relics (where to look) |
|---|---|
| **The Royal Collection** | Crown of the Illager King (evokers) · Horn of the Great Ravager (ravagers) · Scepter of the Piglin Court (piglin brutes) · The Bastion Signet (bastion treasure) · Great Seal of the Mansion (woodland mansion chests) · The Golden Chalice (desert pyramid chests) |
| **Treasures of the Deep** | Eye of the Elder (elder guardians) · Captain's Spyglass (shipwreck chests) · The Sunken Doubloon (buried treasure) · Drowned Sailor's Compass (drowned) · Kraken's Ink Pot (glow squid) · Pearl of the Monument (sea lanterns) |
| **Relics of the Underworld** | The Dragon's Tooth (Ender Dragon) · Crown of Bones (the Wither) · The Netherite Idol (ancient debris) · The Ghast's Last Tear (ghasts) · Heart of the Blaze (blazes) · The Warden's Echo (wardens) |
| **The Ancient World** | Petrified Trilobite (deepslate) · Tyrant Lizard Tooth (dripstone blocks) · Mosquito in Amber (bee nests) · Lantern of the Deep Dark (ancient city chests) · The Emerald Idol (jungle temple chests) · The Stronghold Codex (stronghold libraries) |

Mobs only count when a player kills them. Chest relics turn up the first time anyone opens that structure chest. Relics can't be sold to the Market (priceless, darling), but you can trade them. Lose one in lava and it's gone for good. Admins can bring a lost relic back with `/riches admin unfind <id>`.

## Works with Colonycraft

A Colonycraft **Bank** has a Vault Ledger in its vault: the pile shows what's in the bank's shared vault (not anyone's own balance), and its Vault Door opens for anyone. A Colonycraft **Museum** is full of Display Cases and Pedestals that belong to the colony's owner, so relics on show there count towards your collections. Other mods can set up vaults, doors and cases (and shared "pool" vaults) through `riches:api` in Fabric's ObjectShare.

## Commands

`/riches` (help), `/riches relics`, `/riches trust <player>`, `/riches untrust <player>`, `/riches trusted`. Ops: `/riches give ledger|door|case|pedestal|relic <id>`, `/riches admin reload`, `/riches admin unfind <id>`.

## Config (`config/riches.json`)

| Key | Default | Meaning |
|---|---|---|
| `pileRadius` | `3` | How far the pile spreads around the ledger (3 = 7×7) |
| `pileStart` | `1000` | Balance where the first coins show |
| `pileHeightPerTenfold` | `0.9` | Blocks of height per tenfold of balance |
| `pileMaxHeight` | `4.0` | The tallest a pile gets |
| `doorOpenTicks` | `100` | How long a Vault Door stays open (20 ticks = 1 second) |
| `uniqueRelics` | `true` | One of each relic per server |
| `collectionReward` | `5000` | Marks for showing a full collection (once per player per collection) |
| `relicChanceMultiplier` | `1.0` | Multiplies every relic's drop chance |

Vaults, doors, cases, finds and trust are saved in `<world>/riches.json`.

## Notes

- Nothing in the pile can be mined: it's all display entities. Sorry.
- Server-side mods can't make you actually swim through gold (that animation only happens in water), so you wade instead.
- CI builds Market and Riches, then boots a real server. It checks the 24 relics (unique names, real models and sources), the pile height against a real Market balance (and that redrawing replaces the pile, not doubles it), a vault door opening and swinging shut, six cases and pedestals with floating relics and plaques, the Royal Collection reward paid exactly once, uniqueness, spotting an unopened loot chest, and everything surviving a save and load.
