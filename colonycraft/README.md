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
   - Wages are paid from your balance: ₥3 per worker, more for guards (see Defences). **If you can't pay, nobody gathers** until you can.
   - Farms, lumber camps and mines deliver to your storehouses.
   - Workshops turn raw goods into better ones: logs into planks, raw ores into ingots, cobble into stone, wheat into bread.
   - Storehouses with **autosell** on sell everything the Market buys. You switch this per storehouse.
5. **Workers can die.** Their building page at the Town Hall offers a replacement (₥100 each), or bring your own: a villager in a [Burlap Sack](../burlap-sack/README.md) takes an empty job for free.
6. **Found more colonies** anywhere else. They need room between them to grow.

| Building | Price | Crew (tier 1/2/3) | Does |
|---|---|---|---|
| Town Hall | ₥2,500 | 1 mayor | The counter. Upgrades give more land and more building slots (8/14/20) |
| Residence | ₥400 | – | Beds: 4/6/8. Every worker needs a bed |
| Farm | ₥800 | 2/3/4 | Wheat, carrots, potatoes, beetroot, pumpkins, melons |
| Lumber Camp | ₥900 | 2/3/4 | Oak, spruce and birch logs, sticks, saplings, apples |
| Mine | ₥2,500 | 3/4/5 | Cobble, coal, iron, copper, gold, redstone, lapis, the odd diamond or emerald |
| Workshop | ₥1,500 | 2/3/4 | Processes 64 items per worker per day into better goods |
| Fishery | ₥1,000 | 2/3/4 | Cod, salmon, the odd tropical fish and pufferfish, ink sacs, kelp, now and then a nautilus shell. The workshop cooks the fish |
| Tobacco Farm | ₥1,200 | 2/3/4 | Tobacco leaves (needs the [Havana](../havana/README.md) mod). From tier 2 the curing barn cures two leaves in five, at tier 3 it ages one in six too |
| Storehouse | ₥500 | 1/1/2 | 27 slots, 54 at tier 2, and +10% on auto-sales at tier 3. Right-click any barrel to open it. With the [Warehouse](../warehouse/README.md) mod it's a real warehouse (see below) |
| Harbor Office | ₥2,500 | 1/2/3 | A customs house with a pier out the back. The lantern at the end of the pier is a Warehouse **Loading Dock**. While it's staffed, auto-sales pay +5% / +10% / +15% |
| Train Station | ₥1,500 | 1 | A straight track through a platform, a **Pickup Station** and a **Drop-off Station** for [Cargo Trains](../cargo-train/README.md) (see below). One tier only |
| Barracks | ₥2,000 | 1/2/3 iron golems | Golems patrol the colony's land and fight monsters. ₥15 a day each, no beds needed |

The Town Hall counter has the farms, fishery, tobacco farm, mine, workshop and storehouse in the blueprint row, the harbor, station, barracks, shackles and charter along the top, and the fortifications and law-and-order buildings in the row below.

Every building is in the same Dutch neo-renaissance style: red brick dressed in cream sandstone (quoins, string courses, lintels with keystones) under dark slate mansard roofs. The Town Hall is a three-storey mansion with a gabled centre bay, a portico and a council chamber upstairs. The Town Hall is 11 × 11 and the Residence 9 × 9; existing ones grow into the path around them when you **Repair & renovate** them (see below).

Upgrades cost 1.5× the price for tier 2 and 3× for tier 3. Each tier adds workers, and each worker produces more. Demolishing a building refunds half of everything you paid for it. The Town Hall goes last, and demolishing it disbands the colony.

Only the owner can break blocks of colony buildings. Colony villagers don't trade; they're busy.

## Defences

Fortifications are in their own row at the Town Hall. They **don't use building slots**, they may touch each other (other buildings still need a path around them), and they **snap together**: place one near the end of another and it lines up end to end, at the same height, so the wall-walk runs straight through.

They're generic medieval curtain walls: a rubble footing, coursed stone above, dressed quoins where the segments join, a corbel table under a crenellated parapet, and a walkway behind it at the same height on every piece.

| Fortification | Price | Crew (tier 1/2/3) | What it is |
|---|---|---|---|
| Wall | ₥300 | – | 9 blocks long, 5 thick, with a 3-wide walkway behind the battlements and a low railing on the inside. The side that faces you when you place it is the inside: two blind arches between buttresses, a torch in each. Long runs read as bays: the quoins at the joints pair up |
| Wall Stairs | ₥350 | – | The same wall, with a flight of steps up the inside face to the walkway |
| Wall Tower | ₥800 | – | A tower for corners and long runs: a door, arrow slits, a ladder, a floor at walkway height with openings on all four sides (so walls run into it from any side) and a crenellated top |
| Gatehouse | ₥1,200 | – | Two solid towers either side of an arched passage, a portcullis in the outer arch and fence gates halfway. You can open them, monsters can't. Its deck joins the wall-walk |
| Watchtower | ₥1,800 | 1/2/3 archers | The wall tower with a bunk room, a parapet the archers stand in and a pointed timber roof. Archers shoot monsters up to 24 blocks away. ₥10 a day each |

Upgrading a fortification rebuilds it in better stone: **cobblestone** at tier 1, **stone bricks on a cobbled foot** at tier 2, **dressed stone bricks with chiseled trim** at tier 3 (taller merlons, lanterns on the railing). Watchtowers get an archer per tier: the first watches the front, the second the back, the third the left side. Walls from before 1.3.0 get the new look with **Repair & renovate** (their old ladders go: use Wall Stairs or a tower to get up).

Archers are villagers with crossbows who stand at gaps in the parapet and don't move or run away. Every second and a half each one fires at the nearest monster it can see on its side of the tower. Their arrows can't be picked up. Iron golems from the barracks wander the whole claim and fight whatever comes in; patch them up with iron ingots as usual.

**If you can't pay wages, the guards strike too**: archers hold their fire and golems stand still until you can pay.

## With the other LIB Pack mods

All of these are optional. Colonycraft finds the other mods when they're installed; without them it works the way it always has.

### Warehouse: storehouses are warehouses

Every storehouse has a **Warehouse Core** (the cartography table against the back wall) with **Storage Racks** (barrels) beside it: 2 at tier 1, 6 at tier 2, 12 at tier 3, so about 10,000, 26,000 and 51,000 items. With the Warehouse mod installed:

- Everything the colony gathers goes onto the shelves. Workshops take their raw goods from there, and autosell sells from there.
- Right-click the core or any barrel in the storehouse, or click **Open the warehouse** on its Town Hall page, for the warehouse screen: tabs, search, sorting.
- It's locked: anyone can bring things, only you take them out.
- **Networks:** in the warehouse's settings, link it to a **central warehouse** of yours (say, the big one at home). The central one opens its branches from anywhere, and a branch can send everything it gets on to it. Your colonies fill your home warehouse while you're away.
- Older storehouses become warehouses after **Repair & renovate**. Whatever was in their 27 or 54 slots moves onto the shelves the next morning.
- Demolishing a storehouse packs its warehouse up: the core drops at the door with the stock inside.

### Ahoy and Warehouse: the harbor

The lantern at the end of the Harbor Office's pier is a Warehouse **Loading Dock**. Moor an [Ahoy](../ahoy/README.md) ship within 16 blocks and its captain's menu gets a Loading Dock button: unload the cargo into the warehouses within 48 blocks (your storehouses, say), or load up from them. Place the harbor standing on the shore, facing the water: the door faces you and the pier points away. Water in the footprint is fine, and the water beside the pier stays water. A staffed harbor adds 5% per tier to everything your storehouses auto-sell.

### Cargo Train: the train station

The station's track runs from one end of the building to the other; carry your line on from both ends. Next to the track:

- the **Drop-off Station** (a chest): whatever a train unloads there goes into the storehouses, every two seconds;
- the **Pickup Station** (a barrel): a train loads whatever is in it. Switch on **Ship goods out** on the station's page and it's kept full from the storehouses, so every train carries the colony's goods away (to a Drop-off Station at your home warehouse, say).

### Havana: the tobacco farm

Tobacco farms grow real Havana tobacco: leaves, the odd seed, and from tier 2 cured (and at tier 3 aged) tobacco out of the curing barn. Without Havana there are no tobacco farms for sale.

### Burlap Sack: bring your own workers

Bag a villager, carry them to your colony and open the building page of a building with an empty job. **Hire *name* from your Burlap Sack** puts them to work for free, and you get the empty sack back. Iron golems don't come in sacks, so it's every building but the barracks.

## Cellblock: law and order

Illagers have been burning villages for years. Time they did some time.

| Building | Price | Crew | What it is |
|---|---|---|---|
| Cellblock | ₥2,200 | 1 jailer (₥5 a day) | A brick gaol after the Gevangenpoort in The Hague: a corridor between cells behind iron bars, the jailer's office upstairs, stepped gables. **2 / 4 / 6 cells** at tier 1 / 2 / 3; the cells you haven't paid for yet are bricked up |
| Scaffold | ₥1,200 | – | The *schavot* from the market square: a stone platform up a flight of steps, a railing, a gallows beam with a bell and the headsman's block. Public executions pay **2× / 2.5× / 3×** the bounty at tier 1 / 2 / 3 |

1. **Buy Shackles** (₥50) at the Town Hall: the *Law and order* row, or the Cellblock's own page. They're reusable.
2. **Beat an illager below 40% health**, then right-click them with the shackles. Pillagers, vindicators, evokers and illusioners fit. Their weapons are confiscated; a raid captain's banner goes to you as a trophy, so no Bad Omen comes of it. Illagers without a name get one, so you know who Gary is.
3. **Lock them up.** Every cell has a **holding block**: the vault in the corridor wall. Right-click it to open it and put the shackles in the middle slot (or right-click the vault with the shackles in hand). The prisoner appears in the cell, behind the bars, and stands there: no moving, no fighting, no spells, no joining raids. Their name tag counts the days they've done.
4. **Take them out** again by taking the shackles out of the holding block: the prisoner goes back into the shackles, ready for another cell.
5. **Decide their fate** in the holding block. Every option needs two clicks, and your shackles come back empty.

| Fate | Pays | How it goes |
|---|---|---|
| **Execute in the cell** | The bounty: **₥25** pillager, **₥40** vindicator, **₥80** illusioner, **₥100** evoker | Quick and quiet. Nothing drops, so there's no totem farm here |
| **Ransom** | The bounty, plus a quarter of it for every full day you've held them, up to **2.5×** | An illager envoy pays up and takes them home. They're gone for good, so you can't catch and ransom the same illager forever. Holding out costs upkeep |
| **Public execution** | **2× / 2.5× / 3×** the bounty (bounty plus ticket sales), paid right away | Needs a **Scaffold** in the colony. The prisoner is marched up onto it, the whole server is told where, the bell tolls a six-second countdown and the crowd cheers. One show per scaffold at a time |

Details:

- Prisoners cost **₥2 a day** in bread and water, paid with the wages.
- Prisoners are invulnerable, and the colony's golems and archers leave them alone. Executions are the jailer's privilege.
- Shoved prisoners get put back where they stand. A prisoner who vanishes anyway (say the server went to Peaceful, which clears out all monsters) has escaped, and their cell is freed the next time you open it.
- A cellblock with prisoners in it can't be demolished. Empty the cells first.
- Upgrading rebuilds the cellblock with two more cells unbricked. The prisoners stay where they are.

## Repair & renovate

Every building's page at the Town Hall has a **Repair & renovate** button (10% of the building's price). It shows how many blocks are missing or out of place, and rebuilds the building exactly as designed for its tier. Use it after a creeper visit, or on buildings from before the redesign to give them the new look. It clears everything in the footprint that isn't part of the design, so it won't start while a chest or other container in there still has something in it.

## Commands

- `/colonycraft`: help
- `/colonycraft charter`: buy a Colony Charter
- `/colonycraft give <building>` (op): get a blueprint for free (`fishery`, `tobacco_farm`, `harbor_office`, `train_station`, `wall_stairs`, `wall_tower`...)
- `/colonycraft give shackles` (op): get a pair of shackles for free
- `/colonycraft payday` (op): run a payday right now

Colonies are saved in `<world>/colonycraft.json`.
