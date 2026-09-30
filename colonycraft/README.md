# Colonycraft

A Fabric mod for Minecraft **26.3** for the "greed is good" colonist playthrough. Found colonies, buy prefab buildings with Marks (₥) from the **Market** mod, and let villagers do the gathering.

It's **server-side only**. Friends join with a plain vanilla client. It needs the **Market** mod, a version from after Colonycraft was added, which knows about offline wages.

## Install

Put **Fabric API**, **`market-<version>.jar`** and **`colonycraft-<version>.jar`** in your server's `mods/` folder. Both jars are on the [colonycraft-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/colonycraft-latest). Replace your old Market jar with the one there.

## How it works

1. **Found a colony.** Buy a **Colony Charter** with `/colonycraft charter` (₥2,500), or at any Town Hall you already have. Rename it in an anvil to name the colony. Right-click the ground and a **Town Hall** gets built. The colony claims the land around it (65 × 65 blocks to start).
2. **Shop at the counter.** Right-click the Town Hall's **lectern**. It sells blueprints, upgrades and replacement workers, and lists your buildings.
3. **Place a blueprint.** Right-click the ground inside your colony. The first click shows the outline, and the door faces you. Click the same spot again to build. The building goes up layer by layer and its villagers move in.
4. **Every morning** (every 20 minutes, one Minecraft day):
   - Wages are paid from your balance: ₥3 per worker. **If you can't pay, nobody gathers** until you can.
   - Farms, lumber camps and mines deliver to your storehouses.
   - Workshops turn raw goods into better ones: logs into planks, raw ores into ingots, cobble into stone, wheat into bread.
   - Storehouses with **autosell** on sell everything the Market buys. You switch this per storehouse.
5. **Workers can die.** Their building page at the Town Hall offers a replacement (₥100 each).
6. **Found more colonies** anywhere else. They need room between them to grow.

| Building | Price | Crew (tier 1/2/3) | Does |
|---|---|---|---|
| Town Hall | ₥2,500 | 1 mayor | The counter. Upgrades give more land and more building slots (8/14/20) |
| Residence | ₥400 | – | Beds: 4/6/8. Every worker needs a bed |
| Farm | ₥800 | 2/3/4 | Wheat, carrots, potatoes, beetroot, pumpkins, melons |
| Lumber Camp | ₥900 | 2/3/4 | Oak, spruce and birch logs, sticks, saplings, apples |
| Mine | ₥2,500 | 3/4/5 | Cobble, coal, iron, copper, gold, redstone, lapis, the odd diamond or emerald |
| Workshop | ₥1,500 | 2/3/4 | Processes 64 items per worker per day into better goods |
| Storehouse | ₥500 | 1/1/2 | 27 slots, 54 at tier 2, and +10% on auto-sales at tier 3. Right-click any barrel to open it |

Upgrades cost 1.5× the price for tier 2 and 3× for tier 3. Each tier adds workers, and each worker produces more. Demolishing a building refunds half of everything you paid for it. The Town Hall goes last, and demolishing it disbands the colony.

Only the owner can break blocks of colony buildings. Colony villagers don't trade; they're busy.

## Commands

- `/colonycraft`: help
- `/colonycraft charter`: buy a Colony Charter
- `/colonycraft give <building>` (op): get a blueprint for free
- `/colonycraft payday` (op): run a payday right now

Colonies are saved in `<world>/colonycraft.json`.
