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
| Barracks | ₥2,000 | 1/2/3 iron golems | Golems patrol the colony's land and fight monsters. ₥15 a day each, no beds needed |

Every building is in the same Dutch neo-renaissance style: red brick dressed in cream sandstone (quoins, string courses, lintels with keystones) under dark slate mansard roofs. The Town Hall is a three-storey mansion with a gabled centre bay, a portico and a council chamber upstairs. The Town Hall is 11 × 11 and the Residence 9 × 9; existing ones grow into the path around them when you **Repair & renovate** them (see below).

Upgrades cost 1.5× the price for tier 2 and 3× for tier 3. Each tier adds workers, and each worker produces more. Demolishing a building refunds half of everything you paid for it. The Town Hall goes last, and demolishing it disbands the colony.

Only the owner can break blocks of colony buildings. Colony villagers don't trade; they're busy.

## Defences

Fortifications are in their own row at the Town Hall. They **don't use building slots**, they may touch each other (other buildings still need a path around them), and they **snap together**: place one near the end of another and it lines up end to end, at the same height, so the wall-walk runs straight through.

| Fortification | Price | Crew (tier 1/2/3) | What it is |
|---|---|---|---|
| Wall | ₥300 | – | 9 blocks long, 5 thick, 6 high, with a 3-wide walkway, a railing on the inside and battlements outside. The side that faces you when you place it is the inside: an arched alcove with a bench and a lantern, and a ladder up |
| Gatehouse | ₥1,200 | – | Two squat towers either side of an arched passage, with fence gates halfway. You can open them, monsters can't. Its deck joins the wall-walk |
| Watchtower | ₥1,800 | 1/2/3 archers | A tower with a door, a ladder, a bunk room at wall-walk height (open on all four sides, so walls run into it) and a battlemented top under a pointed roof. Archers shoot monsters up to 24 blocks away. ₥10 a day each |

Upgrading a fortification rebuilds it in better stone: **cobblestone** at tier 1, **stone bricks** at tier 2, **deepslate** at tier 3. Watchtowers get an archer per tier: the first watches the front, the second the back, the third the left side.

Archers are villagers with crossbows who stand at gaps in the parapet and don't move or run away. Every second and a half each one fires at the nearest monster it can see on its side of the tower. Their arrows can't be picked up. Iron golems from the barracks wander the whole claim and fight whatever comes in; patch them up with iron ingots as usual.

**If you can't pay wages, the guards strike too**: archers hold their fire and golems stand still until you can pay.

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
- `/colonycraft give <building>` (op): get a blueprint for free
- `/colonycraft give shackles` (op): get a pair of shackles for free
- `/colonycraft payday` (op): run a payday right now

Colonies are saved in `<world>/colonycraft.json`.
