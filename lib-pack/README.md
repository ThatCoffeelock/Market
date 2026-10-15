# LIB Pack

Every mod in this repo in **one jar**, for Minecraft **26.3** (Fabric):

| Mod | What it adds |
|---|---|
| [Market](../README.md) | Sell anything for Marks (₥), buy items back, flex with pallets of cash |
| [Colonycraft](../colonycraft/README.md) | Colonies, prefab buildings and villager workers paid in Marks: farms, ranches, apiaries, fisheries, tobacco farms, a harbor with a Loading Dock, a train station, medieval walls and towers, and a town street with a trading post (master traders), a bank with a shared walk-in vault, a museum for your relics, a chapel, a library and a fuel depot, plus a guildhouse that hires Sellswords cheaper, and a distillery and a tavern that turn your grain into genever money. Storehouses are real warehouses |
| [Ahoy](../ahoy/README.md) | A sailing ship in a bottle: captain + 8 passengers, cargo holds, wind |
| [Burlap Sack](../burlap-sack/README.md) | Bag villagers and wandering traders, let them out somewhere else |
| [Cannon](../cannon/README.md) | An aimable cannon and iron + gunpowder cannonballs |
| [Cargo Train](../cargo-train/README.md) | A self-driving locomotive and cargo wagons that haul between station chests |
| [Flintlock](../flintlock/README.md) | Flintlock pistols, muskets and blunderbusses, with cartridges and slow reloads |
| [Havana](../havana/README.md) | Grow tobacco, cure it in a barrel, roll and smoke cigars |
| [Warehouse](../warehouse/README.md) | Big sorted storage: a core plus racks, intake chests, train drop-off, Loading Docks for ships, drill rigs unloading straight in, and networks of branches around a central warehouse |
| [Skills](../skills/README.md) | Elder Scrolls style skills: level up by doing, spend perk points every 10 levels |
| [Hamlets & Horrors](../hamlets/README.md) | Random cottages, castles and dungeons: some lived in by villagers, some overrun by monsters |
| [Overenchant](../overenchant/README.md) | Higher maximum enchantment levels: every enchantment goes to X (Sharpness X instead of V). Flintlock guns can be enchanted too |
| [Fuck Illagers](../fuck-illagers/README.md) | Bounty hunting: illager fingers for Marks, and contracts on named illager bosses in wagons, towers, camps, fortresses, dungeons and castles |
| [Fossil Fool](../fossil-fool/README.md) | Old-timey oil: dowse for pockets, sink a 5×5 shaft with a fuel-guzzling Drill Rig, strike crude, tank it (or water, or lava), pipe it across the map (or from an offshore rig), upgrade the rig in its workshop, refine it into diesel and burn it in an Industrial Oven that smelts ores double |
| [Riches](../riches/README.md) | Show off: a walk-in vault where your Market balance piles up in gold, locked vault doors, display cases with plaques, and 24 one-of-a-kind relics |
| [Blimey](../blimey/README.md) | A riveted iron airship that runs on Fossil Fool diesel: refits for speed, fuel economy and cargo, plus small, big and huge bombs that wreck buildings |
| [Sellswords](../sellswords/README.md) | Hire Dutch mercenaries with crossbows and swords: they follow you, hold a spot, hunt with you, guard your walls and villagers, board your ships and airships, and climb the ranks for gold to Musketeer or Foestopper Bulwark |
| [Proost](../proost/README.md) | Ferment wheat, sugar cane and apples into mash, distill genever, whisky, rum and brandy in a cauldron over a fire, age them in oak casks, and drink until the world spins (Dutch Courage included) |
| [De Compagnie](../compagnie/README.md) | Run a shady VOC trading company from a Company Desk: hire agents, bankroll 36 trade, raid, explore and smuggling jobs three ways, answer telegrams from the road, lend money to dubious borrowers and send collectors after them, grow from a Charter to the VOC |
| [De Hooghe Heeren](../hooghe-heeren/README.md) | Rival houses behind a tab on the Company Desk: lords with treasuries, regiments and long memories who play by your rules. Gifts, pacts, alliances and tribute; spies, saboteurs, forged letters and assassins run by your agents; wars with pitched battles at noon you can ride out to and fight in; seats in the world to visit (or storm), and an Audience Bell hall to feast your guests in (or bar the doors) |

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

CI (`.github/workflows/lib-pack.yml`) builds all twenty mods and the pack, then installs the pack on a real Fabric server, the way a server owner would, and checks that all twenty mods load and the server starts.
