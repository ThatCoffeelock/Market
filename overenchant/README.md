# Overenchant

A Fabric mod for Minecraft **26.3** that raises the maximum level of enchantments. Where vanilla stops Sharpness at V, you get **Sharpness X**, and every other enchantment with more than one level goes to **X** too: Protection IV, Unbreaking III, Knockback II.

It's **server-side only**. Friends join with a plain vanilla client. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`overenchant-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `overenchant-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Overenchant** run and download the `overenchant-mod` artifact. You can also build it yourself: run `./gradlew build` in `overenchant/` (needs Java 25). It's in the [LIB Pack](../lib-pack/README.md) too.

## What changes

Every enchantment that has more than one level can now reach level X. That's what the anvil, the enchanting table, `/enchant` and villagers look at, so they all go higher.

| Enchantment | Vanilla | With Overenchant |
|---|---|---|
| Sharpness, Smite, Bane of Arthropods, Efficiency, Power, Impaling, Density | V | **X** |
| Protection, Fire, Blast and Projectile Protection, Piercing, Breach | IV | **X** |
| Unbreaking, Fortune, Looting, Sweeping Edge, Thorns, Respiration, Depth Strider, Soul Speed, Swift Sneak, Quick Charge, Riptide, Loyalty, Lure, Luck of the Sea, Wind Burst | III | **X** |
| Knockback, Fire Aspect, Punch, Frost Walker | II | **X** |
| Mending, Silk Touch, Infinity, Flame, Multishot, Channeling, Aqua Affinity | I | I (unchanged) |

Type `/overenchant` in game to see the list for your server.

## How to get the higher levels

- **Enchanting table**: it offers the new levels. Every enchantment's cost curve is squeezed so its new top level costs what the old top level did: Sharpness X costs what V used to, and every level below it gets cheaper in proportion. A level 30 table with bookshelves now hands out Sharpness VIII to X. Enchantments that used to stop at II or III are squeezed even harder, so a decent table gives you their X. Turn this off with `compressCosts`.
- **Anvil**: combine two items with the same enchantment at the same level and you get the next level, so two Sharpness IX books make Sharpness X. The anvil still says "Too Expensive!" at 40 levels.
- **Ops**: `/enchant @s minecraft:sharpness 10` works now.

The effects keep growing with the level, because the enchantments' own formulas carry on past the old maximum. Sharpness X adds 5.5 damage (V adds 3), Efficiency X digs very fast, and Protection is still held back by vanilla's cap of 80% damage reduction.

## Config (`config/overenchant.json`)

| Key | Default | Meaning |
|---|---|---|
| `raiseTo` | `10` | Every enchantment with more than one level can reach at least this level |
| `multiplier` | `1.0` | On top of that: old maximum × this, rounded. 1.0 does nothing. With `raiseTo` 0 and `multiplier` 2.0, Sharpness V would become X and Knockback II only IV |
| `bonusLevels` | `0` | Levels added to the old maximum, after multiplying |
| `cap` | `10` | No enchantment goes above this |
| `compressCosts` | `true` | Squeeze the enchanting table's costs so the new levels are on offer (see above). `false` leaves the new levels to anvils, books and `/enchant` |
| `raiseSingleLevel` | `false` | Also give one-level enchantments (Mending, Silk Touch...) a second level. It does nothing useful |

Run `/overenchant reload` (op level 2) after editing.

**Keep `raiseTo` and `cap` at 10 or lower** unless everyone has this mod on their client. Vanilla only has names for levels I to X, so without the mod a vanilla client shows "enchantment.level.11" for level 11. With the mod installed, the client shows XI, XII and so on.

## Guns

[Flintlock](../flintlock/README.md) guns can be enchanted too (Power, Punch, Flame, Quick Charge, Piercing, Multishot, Infinity). With Overenchant they climb as well: Power X is +150% damage, Piercing X goes through ten extra targets and Quick Charge X reloads in a quarter of the time.

## Notes

- Level-based effects that vanilla caps itself stay capped: Protection never takes off more than 80% (20 protection points), Quick Charge never reloads a crossbow faster than instantly.
- Enchanted items you already have keep their levels. A Sharpness V sword is still a V, and can now be raised.
- CI (`.github/workflows/overenchant.yml`) builds the mod, then boots a real dedicated server. It checks the new maximums (Sharpness, Unbreaking, Protection, Efficiency, Knockback, Power, Quick Charge and Piercing all at X, Mending still I), that Sharpness X costs what V did at the table, that `/enchant` gives Sharpness X and refuses XI, that the config rules (multiplier, bonus, cap, one-level enchantments) work, and that the level names past X are in the jar.
