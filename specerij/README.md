# Specerij

A Fabric mod for Minecraft **26.3** about spices, the stuff the Golden Age was built on. In 1667 the Dutch traded Manhattan for Run, a tiny island covered in nutmeg trees, and thought they'd got the better deal. In this mod they might have.

- **Six spices**: pepper, nutmeg, cloves, cinnamon, tea and saffron.
- **They only grow where they grow.** Each one is locked to its home biomes, which are usually a long way from spawn. Go and get it.
- **A Hortus** cheats the climate: plant under a glass roof with a fire going and anything grows, at half speed.
- **Perennial plants**: pick them when they're ripe and they grow back.
- **A Drying Rack** turns fresh spice into the real thing, about four times the price. Green tea left in longer turns black.
- **With Colonycraft**: a **Spice Plantation** in a faraway colony grows the local spice, and the farther from home it is, the more it brings in.

It's **server-side only**. Friends join with a plain vanilla client. Spices are paper drawn as something that looks the part, plants are vanilla blocks, and the Drying Rack is a barrel. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`specerij-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `specerij-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Specerij** run and download the `specerij-mod` artifact. You can also build it yourself: run `./gradlew build` in `specerij/` (needs Java 25).

## The spices

| Spice | Home biomes | Ripe plant looks like | Fresh → dried | Market (fresh / dried) |
|---|---|---|---|---|
| **Pepper** | jungle, sparse jungle | a sweet berry bush (it bites) | 3-5 Green Peppercorns → Black Pepper | ₥0.75 / ₥3 |
| **Nutmeg** | mangrove swamp | a flowering azalea | 2-3 Nutmeg Fruit → Nutmeg | ₥1.50 / ₥6 |
| **Cloves** | bamboo jungle | a torchflower | 3-4 Clove Buds → Cloves | ₥0.90 / ₥3.50 |
| **Cinnamon** | savanna, savanna plateau, windswept savanna | a dead bush (it's bark) | 2-4 Cinnamon Bark → Cinnamon Quills | ₥1 / ₥4 |
| **Tea** | cherry grove, windswept hills, windswept forest | a fern | 4-6 Tea Leaves → Green Tea → Black Tea | ₥0.40 / ₥1.50 / ₥3 |
| **Saffron** | meadow, flower forest | an allium | 1-2 Saffron Crocus → Saffron | ₥3 / ₥15 |

A cutting sells for ₥5, if you'd rather sell the plant than grow it.

## How it works

| Step | How |
|---|---|
| **1. Find a cutting** | Break grass, ferns and bushes in a spice's home biome. About 1 in 25 times there's a **cutting** of the local spice. `/specerij here` tells you what grows where you stand |
| **2. Plant** | Right-click grass, dirt, podzol, rooted dirt, moss or mud with the cutting, **in its home biome** or in a **Hortus**. Anywhere else it refuses |
| **3. Wait** | One Minecraft day (20 minutes) at home, two in a Hortus. **Bone meal does nothing** |
| **4. Pick** | Right-click the ripe plant: fresh spice, and the plant grows back from a sprout. Right-click an unripe one to see how far along it is |
| **5. Dry** | Put the fresh spice in a **Drying Rack**. A day later it's dried. Green tea left in two more days turns **black** |
| **6. Sell** | At the Market. Or ship it home first, since it's light and it's worth a lot |

Breaking a plant digs it up: you get the cutting back, plus the harvest if it was ripe. Anything else that knocks it over (water, a piston, an explosion) leaves the cutting lying there.

### The Hortus

The Amsterdam Hortus Botanicus is where the Dutch grew a coffee plant smuggled out of Yemen. Yours is any spot where:

- the **first block above the plant is glass** (any glass: panes, stained, tinted), up to 12 blocks up, and
- a **fire is going within 4 blocks**: a lit campfire, furnace, smoker or blast furnace, or lava or magma.

In a Hortus every spice grows, at **half speed**. Let the fire go out and the plants stop growing until it's lit again. They don't die, they just sulk. Plants in their home biome don't need a Hortus, and don't grow any faster in one.

### The Drying Rack

Craft a **Barrel + String** (any shape), or rename any barrel, chest or trapped chest with "Drying Rack" or "Spice Rack" in the name. Open it to see how it's coming along. It works by game time, so it keeps drying while you're away. Each slot is its own batch, and topping up a half-dried stack waters its progress down.

## Colonycraft: spice plantations in faraway colonies

With [Colonycraft](../colonycraft/README.md), the Town Hall sells a **Spice Plantation** (₥1,500; 2 / 3 / 4 planters at ₥3 a day). With Specerij installed it takes the top-left slot of the Town Hall menu.

- It only goes up **where a spice grows wild**, and it grows that spice.
- **The farther from home, the better.** It brings in more the farther it is from your **Headquarters** (from the middle of the world if you have none): double at **5,000 blocks**, or in another dimension.
- Every planter brings in about ₥4 of fresh spice a day at home, whichever spice it is. Tier 2's shed dries half of it (worth about four times as much), and tier 3 dries all of it and ages a third of the tea black.
- Now and then a cutting comes in, to plant in a Hortus back home.

Ship the harvest home by convoy, by Ahoy ship from a harbor, or by Cargo Train. A tier 3 plantation 5,000 blocks out earns real money; one next to your castle barely pays its planters. That's the point.

## Works with

- **[Market](../README.md)**: everything sells there, at the prices above (Specerij doesn't need the Market to run).
- **[Skills](../skills/README.md)**: picking trains **Farming** (and **Green Thumb** sometimes adds one to the pick), and finding a wild cutting gives a bit more.
- **[Colonycraft](../colonycraft/README.md)**: Spice Plantations, above.

## Commands

- `/specerij`: how it all works
- `/specerij here`: what grows where you stand, and whether it's a Hortus (and if not, why not)
- `/specerij give <spice>` (op): 4 cuttings; `give <spice> fresh|dried`: 16 of it; `give blacktea`; `give rack`

## Config (`config/specerij.json`)

| Key | Default | Meaning |
|---|---|---|
| `growTicks` | `24000` | Planting (or picking) to ripe, at home (one Minecraft day) |
| `hortusSlowdown` | `2.0` | How many times slower a Hortus grows |
| `wildCuttingChance` | `0.04` | Chance that breaking grass in a home biome gives a cutting |
| `dryTicks`, `blackTeaTicks` | `24000`, `48000` | Fresh to dried, and green tea to black |
| `priceMultiplier` | `1.0` | Multiplies every spice price |
| `cuttingSellPrice` | `5` | What the Market pays for a cutting |

## Notes

- To vanilla clients every spice is paper with another item's look, a growing plant is short grass and a ripe one a vanilla plant. Custom data and `<world>/specerij.json` keep them apart from the real things.
- A ripe pepper plant is a sweet berry bush, and yes, it pricks. That's pepper for you.
- CI (`.github/workflows/specerij.yml`) builds the mod, then boots a real dedicated server. It paints a strip of jungle next to a strip of plains and checks the biome lock, builds a Hortus (and lets its fire go out), and grows, picks and regrows plants. It undoes a dispenser's bone meal, digs plants up, knocks one over, dries pepper and turns tea black. It also checks the plantation yields, and saves and reloads it all.
