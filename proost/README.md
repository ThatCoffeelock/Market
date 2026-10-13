# Proost

A Fabric mod for Minecraft **26.3** that lets you **ferment mash, distill spirits, age them in oak casks and drink them**.

- **Mash Tuns**: wheat, sugar cane and apples ferment into mash in half a day.
- **A still** that's just a cauldron over a fire: 8 mash and a glass bottle make a bottle of **Jonge Genever**, **Whisky**, **White Rum** or **Brandy**.
- **Oak Casks**: two days in one ages a bottle into **Oude Genever**, **Aged Whisky**, **Dark Rum** or **Brandy VSOP**.
- **Drinking**: four swigs a bottle, each with its own effects. Have a few and you're tipsy. Have a few more and the world spins. Keep going and you pass out.

It's **server-side only**. Friends join with a plain vanilla client. Everything is built from vanilla items, blocks and chest screens. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`proost-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `proost-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Proost** run and download the `proost-mod` artifact. You can also build it yourself: run `./gradlew build` in `proost/` (needs Java 25).

## From field to glass

| Step | How |
|---|---|
| **1. Ferment** | Put **wheat**, **sugar cane** or **apples** in a **Mash Tun**. After half a Minecraft day (10 minutes) each one is a **Mash**: Grain, Cane or Apple |
| **2. Build a still** | Put a **cauldron** (empty or with water) on a **lit campfire**, fire, lava, a magma block or a lit furnace |
| **3. Distill** | Hold mash and right-click the still with an **empty glass bottle** in your inventory. **8 mash = 1 bottle**. Sneak to distill up to 8 bottles at once |
| **4. Age** (optional) | Put **full** bottles in an **Oak Cask**. Two days (40 minutes) later they're aged |
| **5. Drink** | Right-click a bottle. **4 swigs** each; the durability bar shows how many are left. You get the glass bottle back |

### What comes out of the still

| Mash | Makes | Aged in a cask |
|---|---|---|
| Grain | **Whisky** | Aged Whisky |
| Grain + **sweet berries** in your other hand (one per bottle; they're the juniper, near enough) | **Jonge Genever** | Oude Genever |
| Cane | **White Rum** | Dark Rum |
| Apple | **Brandy** | Brandy VSOP |

### Mash Tun and Oak Cask

- **Mash Tun**: craft a **Barrel + Water Bucket** (any shape; you get the bucket back), or rename any barrel, chest or trapped chest to something with **"Mash"** or **"Ferment"** in it.
- **Oak Cask**: craft a **Barrel + 2 Iron Ingots**, or rename one to something with **"Cask"** in it.
- Open one to see how it's coming along ("12 fermenting (45%)").
- Like Havana's Curing Barrels, they work by game time, so they **keep working while you're away**, and topping up a half-done stack waters its progress down.
- Only full, sealed bottles age. Once you've had a swig, it stays as it is.

## Drinking

| Spirit | Each swig | Aged: each swig |
|---|---|---|
| **Genever** | Strength, 30 s. *Dutch Courage* | Strength 60 s + Resistance 30 s |
| **Whisky** | Resistance, 30 s | Resistance 60 s + Regeneration 8 s |
| **Rum** | Water Breathing, 60 s | Water Breathing 120 s + Dolphin's Grace 30 s |
| **Brandy** | Haste, 45 s | Haste 90 s + Absorption 60 s |

Every swig also makes you a bit drunker. It wears off by one point every 30 seconds. `/proost drunk` tells you how you're doing.

| Swigs (give or take) | You're | What happens |
|---|---|---|
| 1-2 | Merry | Nothing. Carry on |
| 3-4 | **Tipsy** | A warm glow. Still nothing bad |
| 5-7 | **Drunk** | The world spins (Nausea) and you stagger about |
| 8 | **Passed out** | Blindness, a few seconds on the floor, and you wake up merely drunk |

One swig a second at most. Drunk players don't stagger in creative.

## Selling

With the [Market](../README.md) installed, mash and bottles have proper prices, and aging more than doubles them:

| Item | Sells for |
|---|---|
| Mash | ₥0.25 |
| Jonge Genever / Whisky / White Rum / Brandy | ₥6 / ₥5 / ₥5 / ₥7 |
| Oude Genever / Aged Whisky / Dark Rum / Brandy VSOP | ₥16 / ₥14 / ₥14 / ₥18 |

An opened bottle sells for half its share of the swigs left. Prices are in `config/proost.json` (created on first start, read again on the next one).

## Skills

With [Skills](../skills/README.md), Proost shares **Connoisseur** with Havana: distilling and drinking give Connoisseur XP, and its bonuses work on drinks.

| | What it does |
|---|---|
| Connoisseur passive | Drink effects last longer |
| **Green Leaf** | A chance that a still run only needs half the mash |
| **Master Roller** | A chance of a bonus bottle when you distill |
| **Iron Lungs & Liver** | A chance that a swig doesn't make you any drunker |

## Colonycraft

With [Colonycraft](../colonycraft/README.md), buy a **Distillery** at your Town Hall: it turns your storehouses' wheat, sugar cane and apples into bottles every morning (and at tier 3 ages some of them), and its stills work for you too. A **Tavern** on the town street pours your bottles for the town and earns marks for it, and its barkeep sells you a drink by the glass.

## Commands

- `/proost`: how it all works
- `/proost drunk`: how drunk you are
- `/proost give mash grain|cane|apple` (op)
- `/proost give genever|whisky|rum|brandy [aged]` and `/proost give tun|cask` (op)

## Notes

- To vanilla clients, mash and bottles are paper that looks like a bowl of stew or a bottle. Custom data keeps them apart from real paper. Using one as paper in a vanilla recipe just turns it into paper, which is never worth it.
- Barrels are tracked in `<world>/proost.json`.
- CI (`.github/workflows/proost.yml`) builds the mod, then boots a real dedicated server. It checks the items and their prices, works out what the still makes from every mash (with and without sweet berries, glass and enough mash), ferments wheat in a real Mash Tun and ages bottles in a real Oak Cask (with topping up and a long absence), checks a cauldron only counts as a still over a lit fire, sobers a drunk up, and saves and reloads the barrels.
