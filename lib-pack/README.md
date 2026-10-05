# LIB Pack

Every mod in this repo in **one jar**, for Minecraft **26.3** (Fabric):

| Mod | What it adds |
|---|---|
| [Market](../README.md) | Sell anything for Marks (₥), buy items back, flex with pallets of cash |
| [Colonycraft](../colonycraft/README.md) | Colonies, prefab buildings and villager workers paid in Marks |
| [Ahoy](../ahoy/README.md) | A sailing ship in a bottle: captain + 8 passengers, cargo holds, wind |
| [Burlap Sack](../burlap-sack/README.md) | Bag villagers and wandering traders, let them out somewhere else |
| [Cannon](../cannon/README.md) | An aimable cannon and iron + gunpowder cannonballs |
| [Cargo Train](../cargo-train/README.md) | A self-driving locomotive and cargo wagons that haul between station chests |
| [Flintlock](../flintlock/README.md) | Flintlock pistols, muskets and blunderbusses, with cartridges and slow reloads |
| [Havana](../havana/README.md) | Grow tobacco, cure it in a barrel, roll and smoke cigars |
| [Mobile Home](../mobile-home/README.md) | Drivable camper vans and tanks with storage and a force field |
| [Warehouse](../warehouse/README.md) | Big sorted storage: a core plus racks, intake chests, train drop-off, Loading Docks for ships, and drill rigs unloading straight in |
| [Skills](../skills/README.md) | Elder Scrolls style skills: level up by doing, spend perk points every 10 levels |
| [Hamlets & Horrors](../hamlets/README.md) | Random cottages, castles and dungeons: some lived in by villagers, some overrun by monsters |
| [Overenchant](../overenchant/README.md) | Higher maximum enchantment levels: every enchantment goes to X (Sharpness X instead of V). Flintlock guns can be enchanted too |
| [Fuck Illagers](../fuck-illagers/README.md) | Bounty hunting: illager fingers for Marks, and contracts on named illager bosses in wagons, towers, camps, fortresses, dungeons and castles |
| [Fossil Fool](../fossil-fool/README.md) | Old-timey oil: dowse for pockets, sink a 5×5 shaft with a fuel-guzzling Drill Rig, strike crude, tank it and refine it into diesel |

It's **server-side only**, like the mods inside it. Friends join with a plain vanilla client.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/server/) for Minecraft 26.3.
2. Put **Fabric API** and **`lib-pack-<version>.jar`** in the server's `mods/` folder.
3. **Remove the separate jars** of these mods if you had them installed. Your worlds, balances, colonies and config files carry over: they belong to the mods, not the jar.

Get the jar from the [lib-pack-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/lib-pack-latest).

## How it's built

The pack doesn't contain any code of its own. It nests the jars that the other projects build (Fabric's "jar-in-jar"), and Fabric Loader loads them as if they were in `mods/` separately. Each mod is still released on its own too.

To build it yourself (Java 25): run `./gradlew build` in the repo root and in each mod folder, then `./gradlew build` in `lib-pack/`. The jar ends up in `lib-pack/build/libs/`.

There's a printable tutorial with one page per mod in `tutorial/LIB-Pack-Tutorial.pdf` (rebuild it with `python3 tutorial/build.py`). There is also a one-page player guide with every recipe, control and command: open `field-guide.html` in a browser.

CI (`.github/workflows/lib-pack.yml`) builds all fifteen mods and the pack, then installs the pack on a real Fabric server, the way a server owner would, and checks that all fifteen mods load and the server starts.
