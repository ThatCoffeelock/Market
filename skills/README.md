# Skills

A Fabric mod for Minecraft **26.3** that adds an **Elder Scrolls style skill system**: you get better at things by doing them.

- **13 skills**, from Combat and Mining to Sailing and Mercantile. Each levels **0 to 100** as you use it.
- **Every level gives a small passive bonus.** Level 100 makes you about **twice as good** at that thing as a beginner. Demigod tier, not "the server blows up" tier.
- **Every 10 levels you earn a perk point** for that skill. Each skill has **3 perks with 5 ranks each**: 15 ranks, but only 10 points. Pick what suits you.
- **A long road.** Early levels come fast, then each level costs 4.5% more than the last. Level 50 is about 10% of the way to 100.
- **No cheesing:** ores, logs, stone and dirt you placed yourself give no XP. Travel and fishing XP stop when you're AFK.

It's **server-side only**. Friends join with a plain vanilla client. The menu is a chest screen and the bonuses are vanilla attributes, so nothing needs a client mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`skills-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `skills-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Skills** run and download the `skills-mod` artifact. You can also build it yourself: run `./gradlew build` in `skills/` (needs Java 25). It's in the [LIB Pack](../lib-pack/README.md) too.

## How to play

| What | How |
|---|---|
| See your skills | `/skills` opens the menu. Click a skill to see its perks |
| Learn a perk | Click the perk (or its next rank pane) when you have a free point. Glowing skills have points to spend |
| Change your mind | **Forget perks** (bottom right of a skill page), shift-click. Costs 5 XP levels by default |
| Leaderboard | `/skills top <skill>` |
| Help | `/skills help` |

Vein Miner and Timber only kick in while you **sneak**, so you don't fell a whole tree by accident.

Admin commands (op level 2): `/skills admin setlevel <player> <skill> <level>`, `/skills admin addxp <player> <skill> <xp>`, `/skills admin reset <player>`, `/skills admin reload`.

## The skills

| Skill | XP from | Passive at level 100 | Perks (per rank, 5 ranks each) |
|---|---|---|---|
| **Combat** | Melee damage dealt | +25% melee damage | **Brute** +5% melee damage · **Executioner** +6% vs targets under 30% health · **Thick Skin** -3% damage from mobs |
| **Marksmanship** | Bow, crossbow, trident and Flintlock gun damage; long shots count extra | +25% ranged damage | **Eagle Eye** +5% ranged damage · **Long Shot** +6% from 20+ blocks · **Recovery** 10% chance to get your arrow back (not with Infinity) |
| **Mining** | Natural stone (0.5) and ores (5 to 60) | +30% pickaxe speed | **Prospector** 5% double ore drops · **Vein Miner** +2 connected ores (sneak) · **Deep Delver** +6% pickaxe speed below Y=0 |
| **Woodcutting** | Natural logs (4) | +30% axe speed | **Lumberjack** 5% double logs · **Timber** +8 logs above (sneak) · **Forester** 20% chance to replant a sapling |
| **Excavation** | Natural dirt, sand, gravel, clay, soul sand, snow | +30% shovel speed | **Treasure Hunter** 0.4% chance per block of a find (flint up to diamonds) · **Bulk Dig** 5% double drops · **Mole** +6% shovel speed |
| **Farming** | Ripe crops (4), melons and pumpkins (5), breeding (15) | 30% double harvest | **Green Thumb** 20% auto-replant · **Rancher** 8% twins · **Butcher** 8% double animal drops |
| **Fishing** | Catches (20) | 25% bonus fish | **Angler** +5% bonus fish · **Treasure Sense** 1% bonus treasure · **Lucky Charm** +0.4 Luck |
| **Horseriding** | Distance ridden on any mount | +20% mount speed | **Bonded** mount takes -10% damage · **Jumper** +4% jump · **Cavalry** +5% melee while mounted |
| **Sailing** | Distance by boat, raft or Ahoy ship | +0.25 swim speed (about Depth Strider I) | **Sea Legs** -10% drowning damage · **Mariner** +5% bonus fish from a boat · **Deep Lungs** +0.2 Oxygen |
| **Enchanting** | Enchanting at a table | 30% chance to get the levels back | **Lapis Saver** 10% lapis back · **Scholar** +5% XP from orbs · **Mana Well** +6% levels-back chance |
| **Blacksmithing** | Crafting and upgrading tools, weapons and armor | 25% chance gear takes no wear | **Tempered** +5% no-wear chance (max 50% total) · **Thrifty Smith** 8% material back · **Armorer** -3% damage in 3+ armor pieces |
| **Brewing** | Potions brewed (the last player to open the stand) | 25% chance the ingredient isn't used up | **Thrifty Alchemist** +5% ingredient saving · **Potent** potions last +8% longer · **Iron Stomach** harmful effects end 10% faster |
| **Mercantile** | Villager trades (8) and Market sales and purchases | +10% Market sell prices | **Haggler** +2% sell prices · **Bulk Buyer** -2% buy prices · **Silver Tongue** -5% villager prices |

Damage reductions from all perks together are capped at 60%. Market buy prices never drop below 1.25x what the market pays, so even a maxed merchant can't print money.

Vanilla boats are steered by the player's own client, so a server-side mod can't make them faster. Sailing gives swimming, breathing and fishing bonuses instead.

## Config

`config/skills.json`, created on first start:

| Setting | Default | What it does |
|---|---|---|
| `xpMultiplier` | `1.0` | All skill XP. `2.0` = level twice as fast |
| `skillXpMultipliers` | `{}` | Per skill, e.g. `{"mining": 0.5}` |
| `respecCostLevels` | `5` | XP levels it costs to forget a skill's perks (0 = free) |
| `xpPopups` | `true` | "+4 Mining" in the action bar |
| `announceMilestones` | `true` | Tell the server when someone hits level 50 or 100 |
| `afkMinutes` | `3` | No travel or fishing XP after this long without moving the camera (0 = off) |

Skill progress is saved in `<world>/skills.json`. Player-placed blocks are remembered inside the chunks themselves.

## Works with

- **Market**: Mercantile raises your sell prices and lowers your buy prices, and selling and buying gives Mercantile XP. Neither mod needs the other.
- **Flintlock**: gun damage levels Marksmanship and gets its bonuses.
- **Ahoy**: sailing the ship levels Sailing.
- **Havana**: Green Thumb never replants potatoes while Havana is installed, so it can't mix up young tobacco plants.
