# Fuck Illagers

A Fabric mod for Minecraft **26.3** about bounty hunting. Illagers drop **fingers** you can sell, and a **Bounty Station** posts **contracts** on named illager bosses hiding far away, in a wagon, a watchtower, a war camp, a fortress, a dungeon or a castle. Bring back the boss's **skull** for the reward.

It's **server-side only**. Players join with a plain vanilla client. Payments go through the [Market](../README.md) bank (Marks, ₥), so it needs the Market mod.

## Install

Put **Fabric API**, the **Market** jar and **`fuck-illagers-<version>.jar`** in your server's `mods/` folder. Get both jars from the [fuck-illagers-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/fuck-illagers-latest). It's in the [LIB Pack](../lib-pack/README.md) too.

## Crafting: Bounty Station

| | | |
|---|---|---|
| Paper | Copper Ingot | Paper |
| Copper Ingot | Crafting Table | Copper Ingot |
| Paper | Copper Ingot | Paper |

It's a fletching table with a gold name. Place it and right-click it. Breaking it gives the Bounty Station back.

## Trophies

Every illager **a player kills** drops an **Illager Finger** (a bone with a name): pillagers and vindicators one, evokers and illusioners two. Kills by your [Sellswords](../sellswords/README.md) mercenaries count too (they hit illagers 25% harder: they have history). Illagers that die some other way (lava, a golem) drop nothing. At a Bounty Station, **Sell trophies** sells every finger in your inventory for **₥3** each, and every skull for its reward.

## Contracts

At the station, pick a contract:

| Difficulty | The target hides in | Boss | Reward for the skull |
|---|---|---|---|
| **Easy** | a caravan **wagon** or a **watchtower**, two guards | pillager or vindicator, 40 health | ₥150 |
| **Medium** | a **war camp** (tents, a caged prisoner) or a **fortress** (walls, towers, a keep), four guards | vindicator with a diamond axe, 80 health | ₥400 |
| **Hard** | a **dungeon** (a crypt, a ladder 16 blocks down, a hall with cells, a skeleton spawner, a boss room) or a **castle** (curtain walls, four towers, a two-floor keep, a ravager), five or six guards | evoker, 140 health | ₥1,000 |

- You get the coordinates in chat and a **Wanted Poster**. Hold it to see how far away the target is and in which direction. Lost it? The station prints a new one.
- The hideout is 1000 to 2000 blocks from the station. It isn't built until someone gets within about 160 blocks, so nothing is generated out there before you go.
- Every hideout has **loot chests** (pillager outpost, weaponsmith, dungeon and stronghold loot; the hard ones woodland mansion and buried treasure too).
- The boss is named, shows its name, and drops a **skull** when it dies. Sell it at any Bounty Station (anyone can).
- **One contract at a time.** Abandon it at the station (no reward) to take another.
- Only in the Overworld.

## Commands

| Command | Who | What |
|---|---|---|
| `/bounty` | Everyone | Help |
| `/bounty contract` | Everyone | Your current contract and where to go |
| `/bounty give station` / `/bounty give fingers <n>` | Ops | Get a station or fingers |
| `/bounty build <wagon\|tower\|camp\|fortress\|dungeon\|castle>` | Ops | Builds a hideout where you stand, with a boss and guards (a test: no contract, so no skull) |

## Notes

- Contracts and stations are saved in `fuckillagers.json` in the world folder. Bosses remember their contract, also after a restart.
- Raid farms produce illagers too, and fingers with them. That's between you and your conscience.
- With Colonycraft and Sellswords, a colony's **Guildhouse** has a Bounty Station of its own, next to its Mercenary Station. Take your squad along on a contract: their kill gets you the skull just the same.
- CI (`.github/workflows/fuck-illagers.yml`) builds the Market mod and this one, then boots a real server. It places and breaks a station, kills illagers for fingers (and a cow and a non-player kill for none), sells them, accepts, refuses and abandons contracts, builds all six hideouts and checks every boss (name, health, contract tag), every loot chest and the guards, kills a boss for its skull, cashes it in, and saves and loads.
