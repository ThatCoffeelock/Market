# Overenchant

A Fabric mod for Minecraft **26.3** that raises the maximum level of enchantments. Sharpness goes to **X**, Unbreaking to **VI**, Protection to **VIII**.

It's **server-side only**. Friends join with a plain vanilla client. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`overenchant-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `overenchant-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Overenchant** run and download the `overenchant-mod` artifact. You can also build it yourself: run `./gradlew build` in `overenchant/` (needs Java 25). It's in the [LIB Pack](../lib-pack/README.md) too.

## What changes

Every enchantment's maximum level is multiplied (by 2 by default) and capped (at X by default). That's all the anvil, the enchanting table, `/enchant` and villagers ever look at, so they all go higher.

| Enchantment | Vanilla | With Overenchant |
|---|---|---|
| Sharpness, Smite, Bane of Arthropods, Efficiency, Power, Impaling, Density | V | **X** |
| Protection, Fire, Blast and Projectile Protection, Piercing, Breach | IV | **VIII** |
| Unbreaking, Fortune, Looting, Sweeping Edge, Thorns, Respiration, Depth Strider, Soul Speed, Swift Sneak, Quick Charge, Riptide, Loyalty, Lure, Luck of the Sea, Wind Burst | III | **VI** |
| Knockback, Fire Aspect, Punch, Frost Walker | II | **IV** |
| Mending, Silk Touch, Infinity, Flame, Multishot, Channeling, Aqua Affinity | I | I (unchanged) |

Type `/overenchant` in game to see the list for your server.

## How to get the higher levels

- **Anvil**: combine two items with the same enchantment at the same level and you get the next level, now past the old maximum. Two Sharpness V books make Sharpness VI. It takes a lot of books and a lot of XP. The anvil still says "Too Expensive!" at 40 levels.
- **Enchanting table**: the cost curves carry on past the old maximum too, so a few of the new levels can turn up there (Unbreaking IV, for one). Most cost more than a level 30 table can offer.
- **Ops**: `/enchant @s minecraft:sharpness 10` now works.

The effects keep growing with the level, because the enchantments' own formulas carry on past the old maximum. Sharpness X adds 5.5 damage (V adds 3), Efficiency X digs very fast, and Protection is still held back by vanilla's cap of 80% damage reduction.

## Config (`config/overenchant.json`)

| Key | Default | Meaning |
|---|---|---|
| `multiplier` | `2.0` | New maximum = old maximum × this |
| `bonusLevels` | `0` | Levels added on top, after multiplying |
| `cap` | `10` | No enchantment goes above this |
| `raiseSingleLevel` | `false` | Also give one-level enchantments (Mending, Silk Touch...) a second level. It does nothing useful |

Run `/overenchant reload` (op level 2) after editing.

**Keep `cap` at 10 or lower** unless everyone has this mod on their client. Vanilla only has names for levels I to X, so without the mod a vanilla client shows "enchantment.level.11" for level 11. With the mod installed, the client shows XI, XII and so on.

## Guns

[Flintlock](../flintlock/README.md) guns can be enchanted too (Power, Punch, Flame, Quick Charge, Piercing, Multishot, Infinity). With Overenchant they climb as well: Power X is +150% damage, Piercing VIII goes through eight targets.

## Notes

- Level-based effects that vanilla caps itself stay capped: Protection never takes off more than 80% (20 protection points), Quick Charge never reloads a crossbow faster than instantly.
- Enchanted items you already have keep their levels. A Sharpness V sword is still a V, and can now be raised.
- CI (`.github/workflows/overenchant.yml`) builds the mod, then boots a real dedicated server. It checks the new maximums (Sharpness X, Unbreaking VI, Protection VIII, Mending still I), that `/enchant` gives Sharpness X and refuses XI, that the config rules (multiplier, bonus, cap, one-level enchantments) work, and that the level names past X are in the jar.
