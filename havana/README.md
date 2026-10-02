# Havana

A Fabric mod for Minecraft **26.3** that lets you **grow tobacco, cure it, roll cigars and smoke them**.

- **Tobacco plants** that grow on farmland and shoot up two blocks tall when they're ripe.
- **Curing Barrels**: fresh leaves cure in a day. Leave the cured tobacco in for two more days and it ages into premium tobacco.
- **A Cigar Roller**: hold tobacco and right-click a crafting table. Add honey, cocoa, berries, glow berries or blaze powder for flavored cigars.
- **Smoking**: light up with flint and steel or on a campfire, then puff away for Regeneration and friends. Puff too fast and you cough.

It's **server-side only**. Friends join with a plain vanilla client. Everything is built from vanilla items, blocks and chest screens. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`havana-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `havana-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Havana** run and download the `havana-mod` artifact. You can also build it yourself: run `./gradlew build` in `havana/` (needs Java 25).

## From seed to stogie

| Step | How |
|---|---|
| **1. Find seeds** | Break grass and ferns. About 1 in 12 drops **Tobacco Seeds** |
| **2. Plant** | Right-click farmland with the seeds. They grow like any crop, and bone meal works |
| **3. Harvest** | When it's ripe the plant shoots up into a two-block-tall leafy plant. Break it (either half) for **3-5 Tobacco Leaves** and 1-2 seeds. Break it early and you only get your seed back |
| **4. Cure** | Put the leaves in a **Curing Barrel**. After one Minecraft day (20 minutes) they're **Cured Tobacco** |
| **5. Age** (optional) | Leave the Cured Tobacco in the barrel for two more days and it becomes **Aged Tobacco** |
| **6. Roll** | Hold tobacco and right-click a **crafting table**. The Cigar Roller opens. **3 tobacco = 1 cigar**, plus one flavor item per cigar if you want one |
| **7. Light** | Right-click with the cigar while holding **flint and steel** (or a **fire charge**) in your other hand. Or right-click a lit campfire, a torch, a lantern, a lit candle or a fire with it |
| **8. Smoke** | Right-click the lit cigar to take a puff. **8 puffs** per cigar; the durability bar shows how many are left |

### Curing Barrel

Craft one from a **Barrel + Hay Bale** (any shape), or rename any **barrel, chest or trapped chest** in an anvil to something with **"Curing"** or **"Humidor"** in it.

- Open it to see how it's coming along ("12 leaves curing (45%)").
- It works by game time, so it **keeps curing while you're away**. When you come back the barrel catches up.
- Each slot is its own batch. Topping up a half-cured stack with fresh leaves waters its progress down. No cheating.
- Hoppers work. The barrel gets noticed the first time it's opened or placed, so open a renamed barrel once.

### Cigars

| Cigar | Each puff |
|---|---|
| **Cigar** (cured tobacco) | Regeneration I, 5 s |
| **Gran Reserva** (aged tobacco) | Regeneration I, 6 s + Resistance I, 15 s. Finish it for **Hero of the Village** (2 min): villagers can smell the money on you |

| Flavor | Ingredient | Extra effect per puff |
|---|---|---|
| Honey | Honey Bottle (you get the bottle back) | Absorption, 30 s |
| Chocolate | Cocoa Beans | Haste, 30 s |
| Berry | Sweet Berries | Speed, 30 s |
| Glow | Glow Berries | Night Vision, 45 s |
| Blaze | Blaze Powder | Fire Resistance, 30 s |

- One puff a second at most. Puff three times in quick succession and you **cough** (Nausea).
- Lit cigars smoulder in your hand and **go out underwater**. A half-smoked cigar keeps its puffs; light it again later.
- Unlit cigars stack to 16. A lit one is a single item with a durability bar.

## Commands

- `/havana`: how it all works
- `/havana give seeds|leaves|cured|aged|barrel` (op)
- `/havana give cigar [honey|chocolate|berry|glow|blaze]` and `/havana give granreserva [...]` (op)

## Notes

- To vanilla clients, the seeds are beetroot seeds, the leaves are paper (drawn as a fern, a rabbit hide and leather), and a cigar is a stick. Custom data keeps them apart from the real things. Tobacco Seeds only ever plant tobacco. Using a Havana item in a vanilla recipe just turns it into the plain item, which is never worth it.
- A growing plant is a potato crop, and a ripe one is a large fern, at a spot Havana remembers (`<world>/havana.json`). If something other than a player knocks one over (trampling, water, a piston), the potatoes it would drop turn into a tobacco seed.
- Farmer villagers may harvest a ripe crop in the second before it shoots up. Fence your plantation.
- CI (`.github/workflows/havana.yml`) builds the mod, then boots a real dedicated server. It plants tobacco, ripens it, checks it shoots up and harvests it. It tramples one, washes another away and makes sure no potatoes come out. It cures leaves in a Curing Barrel, with topping up and a long absence, and checks rolling and lighting. Then it saves and reloads the field and the barrel.
