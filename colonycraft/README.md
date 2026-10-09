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
| Residence | ₥400 | – | Beds: 4/6/8 (upgrading rebuilds it with two more beds upstairs). Every worker needs a bed |
| Farm | ₥800 | 2/3/4 | Wheat, carrots, potatoes, beetroot, pumpkins, melons |
| Lumber Camp | ₥900 | 2/3/4 | Oak, spruce and birch logs, sticks, saplings, apples |
| Mine | ₥2,500 | 3/4/5 | Cobble, coal, iron, copper, gold, redstone, lapis, the odd diamond or emerald |
| Workshop | ₥1,500 | 2/3/4 | Processes 64 items per worker per day into better goods |
| Fishery | ₥1,000 | 2/3/4 | Cod, salmon, the odd tropical fish and pufferfish, ink sacs, kelp, now and then a nautilus shell. The workshop cooks the fish |
| Tobacco Farm | ₥1,200 | 2/3/4 | Tobacco leaves (needs the [Havana](../havana/README.md) mod). From tier 2 the curing barn cures two leaves in five, at tier 3 it ages one in six too |
| Storehouse | ₥500 | 1/1/2 | 27 slots, 54 at tier 2, and +10% on auto-sales at tier 3. Right-click any barrel to open it. With the [Warehouse](../warehouse/README.md) mod it's a real warehouse (see below) |
| Harbor Office | ₥2,500 | 1/2/3 | A customs house with a pier out the back. The lantern at the end of the pier is a Warehouse **Loading Dock**. While it's staffed, auto-sales pay +5% / +10% / +15% |
| Train Station | ₥1,500 | 1 | Straight tracks through a platform: **1 / 2 / 3 tracks** at tier 1 / 2 / 3, each with its own **Pickup Station** and **Drop-off Station** for [Cargo Trains](../cargo-train/README.md) (see below) |
| Barracks | ₥2,000 | 1/2/3 iron golems | Golems patrol the colony's land and fight monsters. ₥15 a day each, no beds needed |
| Ranch | ₥1,000 | 2/3/4 | Beef, pork, mutton, chicken, leather, eggs, feathers and wool. Cows, sheep, pigs and chickens in the paddock. The workshop cooks the meat |
| Apiary | ₥900 | 1/2/3 | Honeycomb and bottles of honey from the hives in its flower garden |

**The town street** (since 1.4.0): shops and public buildings, in their own row at the Town Hall. See [The town street](#the-town-street).

| Building | Price | Crew (tier 1/2/3) | Does |
|---|---|---|---|
| Trading Post | ₥2,000 | 1/2/3 master traders | A master toolsmith, armorer and librarian (one per tier) sell for emeralds. **Earns ₥25 / ₥55 / ₥100 a day** |
| Bank | ₥3,000 | 1/1/2 clerks | A walk-in vault **anyone** can pay into or take from. With Riches, the money piles up in gold. **Earns ₥25 / ₥50 / ₥90 a day** |
| Museum | ₥2,500 | 1 curator | 4 / 8 / 12 Riches display cases and pedestals for your relics. **Earns ₥20 / ₥40 / ₥75 a day, +₥10 per relic on show**. Needs [Riches](../riches/README.md) |
| Chapel | ₥1,500 | 1 priest | Heals anyone inside. Replacing a worker who died costs 25% / 50% / 75% less |
| Library | ₥1,800 | – | A level-30 enchanting table: fifteen bookshelves (and then some) around it. No staff, no upgrades |
| Fuel Depot | ₥2,000 | 1/1/2 | 2 / 4 / 6 [Fossil Fool](../fossil-fool/README.md) tanks (crude and diesel), a refinery and a pipe manifold. Needs Fossil Fool |

The Town Hall counter has the farms, fishery, tobacco farm, mine, workshop and storehouse in the blueprint row, the harbor, station, barracks, shackles and charter along the top, the fortifications and law-and-order buildings in the row below, and the town street below that. Your own buildings are listed under them, nine to a page. The ranch and apiary are in the town street row too.

Since 1.3.0 the buildings are bigger and more detailed: two-storey brick houses with shuttered windows and overhanging roofs, 13 × 13 farms and lumber camps with timber barns and sawmills, a mine in a hill with a timbered portal, a merchant's warehouse with a hoist, and so on. Buildings from older versions keep their old size and look until you **Repair & renovate** them; if a neighbour stands where the bigger design needs room, renovating tells you which one is in the way.

Every building is in the same Dutch neo-renaissance style: red brick dressed in cream sandstone (quoins, string courses, lintels with keystones) under dark slate mansard roofs. The Town Hall is a three-storey mansion with a gabled centre bay, a portico and a council chamber upstairs. The Town Hall is 11 × 11 and the Residence 9 × 9; existing ones grow into the path around them when you **Repair & renovate** them (see below).

Upgrades cost 1.5× the price for tier 2 and 3× for tier 3. Each tier adds workers, and each worker produces more. Demolishing a building refunds half of everything you paid for it. The Town Hall goes last, and demolishing it disbands the colony.

Only the owner can break blocks of colony buildings. Colony villagers don't trade, they're busy. The trading post's masters are the exception: trading is their job.

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

The station's tracks run from one end of the building to the other; carry your lines on from both ends. A tier 1 station has one track, tier 2 adds a second along an island platform and tier 3 a third behind it, so up to three train lines can serve one colony. Next to each track:

- the **Drop-off Station** (a barrel): whatever a train unloads there goes into the storehouses, every two seconds;
- the **Pickup Station** (a barrel): a train loads whatever is in it. Switch on **Ship goods out** on the station's page and it's kept full from the storehouses, so every train carries the colony's goods away (to a Drop-off Station at your home warehouse, say).

### Havana: the tobacco farm

Tobacco farms grow real Havana tobacco: leaves, the odd seed, and from tier 2 cured (and at tier 3 aged) tobacco out of the curing barn. Without Havana there are no tobacco farms for sale.

### Riches: the bank's vault and the museum

With [Riches](../riches/README.md), the bank's vault money piles up in gold around the Vault Ledger in the middle of the vault, and the vault door is a Riches Vault Door that swings shut by itself (and opens for anyone: it's a public vault). The museum's cases and pedestals are Riches display cases: relics on show there count towards the Royal Society's collections, and each one adds ₥10 a day to the museum's takings. Without Riches the bank still banks (no gold pile), and there's no museum for sale.

### Fossil Fool: the fuel depot

With [Fossil Fool](../fossil-fool/README.md), the fuel depot's cauldrons are Fossil Fool **Tanks** (crude on the left, diesel on the right), its blast furnace is a **Refinery** and the copper rods behind the tanks are a **Pipe** manifold that comes out through both side walls. Lay your pipeline from a Drill Rig onto either end: the crude goes into the crude tanks, the refinery turns it into diesel, and the diesel fills the diesel tanks (and feeds an Industrial Oven on the same pipeline). Without Fossil Fool there's no fuel depot for sale.

### Sellswords and Fuck Illagers: the guildhouse

With [Sellswords](../sellswords/README.md) installed, the Town Hall sells a **Guildhouse** (₥2,000, top row next to the barracks): a half-timbered hall with a long table, a hearth and orange banners. Its **target block is a Mercenary Station** where hiring costs **20% less**, **35%** at tier 2 and **50%** at tier 3, and mercenaries hired there idle around the guildhouse, protecting it and the colony's villagers within 50 blocks. With [Fuck Illagers](../fuck-illagers/README.md) too, its **fletching table is a Bounty Station**: hire your squad on one side of the hall and pick their next target on the other. A guildmaster runs the place (₥4 a day). Without Sellswords there's no guildhouse for sale.

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

## The town street

Eight buildings for the main street, all in the colony style. The bank, the museum and the chapel are a little wider than the rest (15, 15 and 13 blocks).

- **Trading Post**: a market hall open to the street through three arches, a counter and a stall in every bay. Each tier puts a **master trader** in the next stall: a **Master Toolsmith** (iron and diamond tools), a **Master Armorer** (diamond armour and a shield) and a **Master Librarian** (Mending, Unbreaking III, Efficiency V and Protection IV books, and a name tag). They sell for **emeralds**, 4 of each trade, and restock every morning. They stay behind their counters. Right-click one to trade the normal way.
- **Bank**: a portico up a flight of steps, a banking hall with teller counters and a vault at the back. Right-click the **teller's lectern** (left of the hall) to **pay in or take out** ₥10, ₥100 or ₥1,000. It's a **shared vault: anyone can pay in, anyone can take out**, so it's for a town that trusts each other (or a test of character). The owner gets a message when money moves. A bank with money in the vault can't be demolished.
- **Museum**: a long gallery with glass display cases on stone plinths along the walls and pedestals down the middle, under a skylight. Right-click a case with something to put it on show (owner and the players they `/riches trust`). Empty the cases before demolishing it.
- **Chapel**: a nave with stained glass, pews and an altar, and a bell tower with a slate spire over the porch. While the priest is in, anyone inside gets Regeneration (stronger at tier 3), and replacing workers who died costs less.
- **Library**: a reading room with the enchanting table in the middle of thirty bookshelves, plus an anvil and a grindstone. No staff, no upgrades; it's just a good table.
- **Ranch** and **Apiary**: see the table above.
- **Fuel Depot**: see Fossil Fool above. Drain its tanks before demolishing it.

The trading post, the bank and the museum **earn marks every morning** while they're staffed, more than their staff cost: the building page at the Town Hall shows today's earnings next to the wages.

| Building | Tier 1 | Tier 2 | Tier 3 | Staff |
|---|---|---|---|---|
| Trading Post | ₥25 | ₥55 | ₥100 | ₥4 per master |
| Bank | ₥25 | ₥50 | ₥90 | ₥4 per clerk |
| Museum | ₥20 + ₥10/relic | ₥40 + ₥10/relic | ₥75 + ₥10/relic | ₥3 |

## Commands

- `/colonycraft`: help
- `/colonycraft charter`: buy a Colony Charter
- `/colonycraft give <building>` (op): get a blueprint for free (`fishery`, `tobacco_farm`, `harbor_office`, `train_station`, `wall_stairs`, `wall_tower`...)
- `/colonycraft give shackles` (op): get a pair of shackles for free
- `/colonycraft payday` (op): run a payday right now

Colonies are saved in `<world>/colonycraft.json`.
