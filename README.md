# Market

A server-side Fabric mod for Minecraft **26.3** that adds an economy you can farm:

- **Sell anything** to "the market" for a new currency, **Marks (₥)**.
- **Buy items** back at a markup. Rarer items cost more.
- **Buy luxury decorations** that do nothing except show off: stacks of cash, a pallet of cash, gold bar stacks, a pallet of gold, and a Tycoon Trophy with your name floating above it.
- **Market block**, crafted from 4 obsidian, 4 gold ingots and 1 emerald.

It's **server-side only**. Players join with a plain vanilla client and don't need to install anything. The GUIs are chest screens, the Market block is a lectern with a glow, and the luxury items are built from vanilla display entities.

> **Want all the mods?** The [LIB Pack](lib-pack/README.md) has Market, Colonycraft, Ahoy, Burlap Sack, Cannon, Cargo Train, Flintlock, Havana, Mobile Home, Warehouse, Skills, Hamlets & Horrors, Overenchant and Fuck Illagers in one jar.

## Install (plug and play)

1. Install [Fabric Loader](https://fabricmc.net/use/server/) for Minecraft 26.3 on your server.
2. Put **Fabric API** and **`market-<version>.jar`** in the server's `mods/` folder.
3. Start the server. You're done. Config files are created on first start.

Get the jar from this repo's **Actions** tab: open the latest green "Build" run and download the `market-mod` artifact. You can also build it yourself with `./gradlew build` (Java 25); the jar ends up in `build/libs/`.

## How to play

| What | How |
|---|---|
| Craft a Market | `G O G` / `O E O` / `G O G`, where G = gold ingot, O = obsidian, E = emerald |
| Open the market | Right-click a placed Market block |
| Sell | **Sell Items** → drop items into the grid (or click **Add My Inventory**) → **Confirm Sale** |
| Buy | **Buy Items** → pick a category. Left-click buys 1, right-click buys 8, shift-click buys a stack |
| Flex | **Luxury & Vanity** → buy a decoration → right-click a block to place it. Sneak + right-click it with an empty hand to pick it back up |
| Banknotes | Withdraw cash as paper you can trade or stash. Right-click a note to deposit it |

Commands: `/balance` (`/bal`), `/pay <player> <amount>`, `/baltop`, `/withdraw <amount>`, `/worth` (price of the item in your hand).

Admin commands (op level 2): `/market admin give|take|set <player> <amount>`, `/market admin block` (gives you a Market block), `/market admin reload`.

## Pricing

- Sell prices live in `config/market-prices.json`: about 470 items in shop categories, plus tag-based prices (all logs, all wool and so on).
- Items that aren't listed still sell for a small amount based on their rarity, so everything is sellable.
- Damaged tools sell for less, and each enchantment level adds value.
- Shulker boxes and bundles with items inside are refused, so nobody sells their stuff by accident.
- **Buy price = sell price × `buyMarkup` (2.0) × rarity multiplier** (Common 1, Uncommon 1.5, Rare 2.5, Epic 4).
- Emeralds aren't buyable by default. Otherwise villager trading halls would turn them into infinite money.
- Creative-mode players can't sell, so nobody can spawn in diamonds and cash out.

## Config (`config/market.json`)

| Key | Default | Meaning |
|---|---|---|
| `currencyName` / `currencySymbol` | `Marks` / `₥` | Rename the currency |
| `startingBalance` | `100` | What new players start with |
| `buyMarkup` | `2.0` | Keep it ≥ 2 to stop buy-then-resell loops |
| `rarityBuyMultiplier` | 1 / 1.5 / 2.5 / 4 | Extra markup by rarity |
| `sellUnlistedItems`, `unlistedSellPrice` | `true`, tiny | Fallback prices for items that aren't listed |
| `enchantmentValuePerLevel` | `3.0` | Bonus per enchantment level when selling |
| `vanityPrices` | 5k to 250k | Price of each luxury item |
| `vanityResaleFactor` | `0.5` | Share of the price you get back when selling a luxury item |
| `sellPriceOverrides`, `buyPriceOverrides` | `{}` | e.g. `{"minecraft:diamond": 150}` |
| `notSellable`, `notBuyable` | creative-only blocks, emeralds | Blacklists |
| `allowCreativeSelling` | `false` | |
| `marketCommandEnabled` | `false` | Let `/market` open the shop from anywhere |

Run `/market admin reload` after editing. Balances, Market block locations and placed decorations are saved to `<world>/market-data.json`.

## Notes

- If a Market block is destroyed by an explosion, it drops a plain lectern.
- CI (`.github/workflows/build.yml`) builds the mod, then boots a real dedicated server and runs a smoke test that checks prices, custom items and every luxury decoration.
