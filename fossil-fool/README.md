# Fossil Fool

A Fabric mod for Minecraft **26.3** about old-timey oil: dowse for it, drill for it, strike it, pump it, tank it, refine it, burn it, sell it.

- **Oil pockets underground.** About one chunk in eight hides a pocket of crude between y=16 and y=-50. About one pocket in ten is a **gusher**.
- **A Dowsing Rod** twitches towards the nearest pocket and tells you roughly which way, how far and how deep.
- **A Drill Rig**: a wooden derrick with a steam engine that sinks a **5×5 shaft straight down**, one block at a time, all the way to bedrock. **Ores and stone go into separate holds.** It leaves a ladder up the north wall and seals out water and lava.
- **It strikes oil.** When the shaft hits a pocket, the rig stops and **pumps the pocket dry** into its own tank and into Oil Tanks nearby, then carries on down.
- **Fuel is the price of it all.** The rig and the refinery burn **coal, lava, crude or diesel**. Each is worth more per item than the last, and burns faster.
- **Oil Tanks** hold 1,000 buckets. **Refineries** turn 2 crude into 1 diesel.
- **Sell it at the [Market](../README.md)**: crude for ₥25 a bucket, diesel for ₥45.
- **Level [Wildcatting](../skills/README.md)** in the Skills mod. Rigs unload into a **[Warehouse](../warehouse/README.md)** by themselves.

It's **server-side only**. Friends join with a plain vanilla client. The rig is built from vanilla display entities, the tank is a cauldron, the refinery is a blast furnace, and the screens are chest screens.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`fossil-fool-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `fossil-fool-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Fossil Fool** run and download the `fossil-fool-mod` artifact. You can also build it yourself: run `./gradlew build` in `fossil-fool/` (needs Java 25).

Market, Skills and Warehouse are all optional. Each one adds something when it's there.

## Crafting

| Item | Recipe |
|---|---|
| **Drill Rig** | `Iron Block, Diamond Block, Iron Block` / `Piston, Blast Furnace, Piston` / `Log, Log, Log` (yes, two diamond blocks: it's an oil company, not a hobby) |
| **Oil Tank** | `Iron, _, Iron` / `Iron, Cauldron, Iron` / `Iron, Iron, Iron` |
| **Refinery** | `Copper, Cauldron, Copper` / `Copper, Blast Furnace, Copper` / `Bricks, Bricks, Bricks` |
| **Dowsing Rod** | `Stick, _, Stick` / `_, Stick, _` / `_, Gold Ingot, _` |

Buckets of crude come out of the ground and buckets of diesel come out of a refinery. They stack to 16. Pouring one out gives you the empty bucket back.

## How to play

1. **Find oil.** Right-click with the Dowsing Rod: *"The rod tugs to the north-east. About 24 blocks away, about 45 blocks down."* Walk until it says *"straight down!"*.
2. **Put up a Drill Rig.** Right-click the ground with it. The shaft is the 5×5 square centred on the block you clicked.
3. **Feed it.** Right-click the derrick (anyone can, not just the owner). The second row of its screen is the **firebox**: put fuel in it. Rows 3-4 are the **ore hold**, rows 5-6 the **stone hold**. Take things out like from any chest.
4. **Or let hoppers empty it.** A hopper standing around the shaft (within 4 blocks of its middle, on the ground or one block up) gets the holds' contents, ores first, then stone. Run a hopper line into a chest, or into a **Cargo Train Pickup Station**, and the train hauls it away.
5. **Wait.** At full speed it drills one block a second, so about 25 seconds a layer. Every block costs fuel.
6. **Strike oil.** It tells you in chat, and if it's a gusher, so does everyone else. The rig pumps one bucket every two seconds into its own 64-bucket tank, and pipes it into any **Oil Tank** within 9 blocks.
7. **Refine.** Put a **Refinery** within 6 blocks of the tank. It drinks crude from the tank and pipes diesel back into tanks once it has an empty one. Give it fuel too.
8. **Burn the diesel in the rig**, or sell it. Your call, oil baron.

Broke into a pocket by hand? Its rock turns into crude (black concrete). Right-click it with an empty bucket to scoop up a bucket. Break it with a pickaxe and the crude oozes away.

### The fuel ladder

| Fuel | Blocks drilled per item | Speed |
|---|---|---|
| Coal or charcoal (a coal block is 9) | 2 | 0.5× |
| Bucket of lava | 40 | 0.75× |
| Bucket of crude | 60 | 1× |
| Bucket of diesel | 200 | 1.5× |

A whole shaft from sea level to bedrock is about 3,200 blocks: 1,600 coal, or 16 buckets of diesel. Pumping costs a quarter of a block's fuel per bucket. A refinery burns 20 blocks' worth of fuel per bucket of diesel. Lava, crude and diesel leave their empty bucket in the firebox.

### Rig screen

| Button | What it does |
|---|---|
| Engine ON/OFF | Starts and stops it. A running rig keeps its chunk loaded, so it drills while you're away |
| Status | What it's doing, how deep it is, how far through the layer |
| Fuel | What's burning, and the fuel ladder |
| Crude tank | Click to fill the empty buckets you carry |
| Keep stone | Off: stone, dirt and gravel are thrown away and only ores are kept |
| Unload to a Warehouse | With the Warehouse mod: sends both holds to warehouses within 16 blocks (it also does this by itself) |
| Pack up | Shift-click: get the rig back, with everything in it. Empty the crude tank first. Owner only |

Every player can open any rig, feed it, take from its holds and switch it on or off. Only the owner can pack it up.

A rig stops (and says why) when a hold is full, it runs out of fuel, its tank is full, or there's a chest or spawner in the way. It never digs through blocks with contents.

## Works with

- **Market**: crude and diesel sell for ₥25 and ₥45 a bucket (set in `config/fossilfool.json`). Machines can't be sold by accident.
- **Skills**: the **Wildcatting** skill. XP from drilling, striking oil, pumping, refining and dowsing, even while you're offline. Passive: up to +25% fuel efficiency. Perks: **Roughneck** (faster rigs), **Refiner** (bonus diesel), **Dowser** (longer rod range).
- **Cargo Train**: put hoppers around the shaft and lead them into a Pickup Station chest; the train takes the ore and stone away.
- **Warehouse**: a rig with a Warehouse Core within 16 blocks unloads its holds onto the shelves every few seconds. An ores-only warehouse gets the ores first.

## Commands

`/fossilfool` shows the help. Ops: `/fossilfool give rig|tank|refinery|rod|crude|diesel`, `/fossilfool admin pocket` (exact location of the nearest pocket), `/fossilfool admin reload`.

## Config (`config/fossilfool.json`)

| Key | Default | Meaning |
|---|---|---|
| `pocketChance` | `0.12` | Chance a chunk has an oil pocket |
| `pocketMinY` / `pocketMaxY` | `-50` / `16` | Depth range of pockets |
| `gusherChance` | `0.1` | Share of pockets that are gushers |
| `dowsingRange` | `48` | Blocks the rod can feel |
| `coalBlocks` … `dieselBlocks` | 2 / 40 / 60 / 200 | Blocks drilled per item of fuel |
| `coalSpeed` … `dieselSpeed` | 0.5 / 0.75 / 1 / 1.5 | How fast each fuel runs a machine |
| `ticksPerBlock` | `20` | Ticks per block at speed 1 |
| `ticksPerBucket` | `40` | Ticks per bucket pumped at speed 1 |
| `pumpCost` | `0.25` | Fuel per bucket pumped, in blocks |
| `rigTank` | `64` | Buckets in a rig's own tank |
| `keepChunksLoaded` | `true` | Running rigs keep their chunk loaded |
| `pipeReach` | `6` | How far machines reach Oil Tanks (rigs reach 3 further, from the derrick's edge) |
| `warehouseReach` | `16` | How far a rig looks for a warehouse |
| `tankCapacity` | `1000` | Buckets per Oil Tank |
| `refineryCapacity` | `64` | Buckets of crude, and of diesel, a refinery holds |
| `crudePerDiesel` | `2` | Crude per bucket of diesel |
| `refineTicks` | `300` | Ticks per bucket of diesel at speed 1 |
| `refineHeat` | `20` | Fuel per bucket of diesel, in blocks |
| `crudeSellPrice` / `dieselSellPrice` | `25` / `45` | Market prices |

Rigs, tanks, refineries and opened pockets are saved in `<world>/fossilfool.json`.

## Notes

- Oil pockets follow from the world seed, so they exist in old chunks too. The crude only appears as blocks when someone breaks in. Black concrete you place yourself isn't oil, nice try.
- Only the overworld has oil.
- CI builds the mod, then boots a real server. It checks the items, the fuel ladder and the Market prices, rolls pockets from the seed, breaks into one by hand, dowses one, and drills a real shaft through stone, iron ore and a water leak. It checks the 5×5 shape, the ladder, the seal, ores and stone in separate holds, and diesel leaving its bucket. Then it strikes the pocket, pumps it dry into an Oil Tank, lets a hopper beside the derrick pass the holds into a Pickup Station chest (ores first), refines the crude into diesel, and saves and reloads it all (the rig's model gets rebuilt).
