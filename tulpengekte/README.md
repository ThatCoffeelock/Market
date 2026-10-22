# Tulpengekte

A Fabric mod for Minecraft **26.3** about tulip mania, the 1630s Dutch craze when a single bulb could cost as much as a canal house. This version is balanced: no bubble, no crash, just a slow and slightly addictive hunt for the rare ones.

- **Tulip bulbs** in twelve historical varieties, from the humble Witte Croonen to the legendary **Semper Augustus**.
- **Plant them** on grass, dirt or farmland. A day later they flower.
- **Lift them** for the bulb back plus **offsets**. Now and then an offset **breaks** into a rarer, streaked variety.
- **The Bloemist**: a villager flower dealer who wants three varieties every day and pays over the odds for them.

It's **server-side only**. Friends join with a plain vanilla client. Bulbs are paper drawn as the tulip they'll become, beds are vanilla grass and tulips, and the Bloemist's stall is a chest screen. It needs the [Market](../README.md) mod, because the Bloemist deals in Marks (₥).

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API**, **Market** and **`tulpengekte-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `tulpengekte-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Tulpengekte** run and download the `tulpengekte-mod` artifact. You can also build it yourself: run `./gradlew build` in the repo root (for Market), then in `tulpengekte/` (needs Java 25).

## From bulb to fortune

| Step | How |
|---|---|
| **1. Find bulbs** | Break a tulip. About 1 in 4 times there's a **bulb** under it (the flower is used up). Or buy common bulbs from a **Bloemist** |
| **2. Plant** | Right-click grass, dirt, coarse dirt, podzol, rooted dirt or farmland with the bulb. A green sprout comes up |
| **3. Wait** | About one Minecraft day (20 minutes) later it flowers as the vanilla tulip of its color. **Bone meal does nothing.** Patience is the price |
| **4. Lift** | Break the flower: you get the **bulb back** plus its **offsets**. Lift it before it flowers and you only get the bulb back |
| **5. Breaking** | Each offset of a common bulb has a small chance to come up as a rarer variety (below) |
| **6. Sell** | At the Market, or to a Bloemist who wants that variety today |

Anything else that knocks a bed over (water, a piston, an explosion) leaves the bulb lying there, without offsets. A dispenser full of bone meal just gets its grass trimmed back.

### The varieties

| Tier | Varieties | Flowers as | Market price |
|---|---|---|---|
| **Common** | Witte Croonen, Switsers, Gouda, Rosen | white, orange, red, pink | ₥1 |
| **Fine** | Admirael van Enckhuysen, Generalissimo, Lack van Rijn, Paragon Liefkens | white, orange, red, pink | ₥20 |
| **Broken** | Brabanson, Admiral van der Eijck, Viceroy | white, orange, pink (they sparkle) | ₥75 |
| **Legendary** | Semper Augustus | red (it glows) | ₥300 |

### Breeding

| Parent | Offsets when lifted in flower | What an offset is |
|---|---|---|
| Common | 0-2 (about 1.2) | Same variety, or it breaks: **Fine 1 in 20**, **Broken 1 in 100**, **Semper Augustus 1 in 1,000** |
| Fine | 0 or 1 | Same variety (it breeds true), with twice the common odds of breaking into Broken or Legendary |
| Broken | 1 a quarter of the time | Half keep the variety, half **revert** to the common tulip of their color |
| Semper Augustus | 1 time in 10 | Half are another Semper Augustus, half revert to Gouda |

Broken tulips really were weaker: the streaks came from a virus, though nobody knew that in 1637. That's why you can't just fill a field with them. When someone breeds a Semper Augustus, the whole server hears about it.

## The Bloemist

Any villager with **"Bloemist"** in their name is a Bloemist, so a name tag works. Or hold a bulb and right-click a villager **without a job** (a nitwit will do: it doesn't take brains to sell flowers). They take the bulb and open a stall.

Right-click a Bloemist to open the stall:

- **Today's wants**: three varieties (one common, one fine, one broken, and one day in five the Semper Augustus). He pays the **Market price +50%**, up to **32 common, 8 fine, 2 broken or 1 legendary** per player per day. Click a want to sell, or click the emerald to sell everything he wants.
- **Bulbs for sale**: the four common varieties, ₥5 each. Click to buy one, shift-click for eight.

Every Bloemist on the server wants the same things on the same day: it's one market. A new day (20 minutes) brings new wants. `/tulpengekte wants` tells you what they are.

## Works with

- **[Market](../README.md)** (needed): bulbs sell there, and the Bloemist pays and charges in Marks.
- **[Skills](../skills/README.md)**: lifting tulips and breaking rare ones trains **Farming**, and **Green Thumb** sometimes gives an extra offset (common and fine bulbs only). Selling to the Bloemist trains **Mercantile**, and Mercantile's bonuses raise what he pays.
- **[Colonycraft](../colonycraft/README.md)**: farmers dig up a few common bulbs every day, and now and then a fine one from tier 3.
- **[Riches](../riches/README.md)**: put your Semper Augustus in a display case. The plaque does the bragging.

## Commands

- `/tulpengekte`: how it all works
- `/tulpengekte wants`: what the Bloemist wants today, what he'd pay you, and how many more he'll take
- `/tulpengekte give <variety>` (op): 8 bulbs of one variety (`semper_augustus`, `viceroy`...), or `give common|fine|rare|legendary` for 4 of each in that tier
- `/tulpengekte bloemist` (op): a Bloemist, right where you stand

## Config (`config/tulpengekte.json`)

| Key | Default | Meaning |
|---|---|---|
| `commonSellPrice`, `fineSellPrice`, `rareSellPrice`, `legendarySellPrice` | 1 / 20 / 75 / 300 | Market price of one bulb |
| `bloemistPremium` | `0.5` | What the Bloemist pays on top of the Market price for a want |
| `bloemistDailyCommon` ... `bloemistDailyLegendary` | 32 / 8 / 2 / 1 | How many of a want he takes from one player per day |
| `bloemistBulbPrice` | `5` | His price for a common bulb (never below the Market price) |
| `growTicks` | `24000` | Planting to flowering (one Minecraft day) |
| `wildBulbChance` | `0.25` | Chance a broken tulip turns up a bulb |
| `fineChance`, `rareChance`, `legendaryChance` | 0.05 / 0.01 / 0.001 | Breaking odds per common offset |
| `brokenTrueChance` | `0.5` | Share of a broken or legendary bulb's offsets that keep the variety |

### Why it's balanced

Common bulbs multiply fast, so they're cheap: a field of them earns about what a Havana tobacco planter does. The money is in the breaks, which are rare. Broken bulbs barely multiply and half their offsets revert, so nobody farms Viceroys by the acre. The Bloemist pays well, but only for three varieties a day and only a few of each, and he sells his own bulbs far above what they fetch, so he can't be looped.

## Notes

- To vanilla clients a bulb is paper drawn as a tulip, a sprout is short grass and a flowering bed is a vanilla tulip. Custom data and `<world>/tulpengekte.json` keep them apart from the real things.
- CI (`.github/workflows/tulpengekte.yml`) builds the mod, then boots a real dedicated server. It checks the bulbs and prices, runs a few hundred thousand offsets to check the breeding odds, and plants, flowers and lifts tulips. It also bone-meals and washes away a bed, checks who can become a Bloemist and what he wants, and saves and reloads it all.
