# Sellswords

A Fabric mod for Minecraft **26.3**: hire **mercenaries** at a Mercenary Station. They carry crossbows and swords, follow you around or hold a spot like a dog told to stay, join your hunts, guard your walls and your villagers, ride along on your ships and airships, and climb the ranks for gold. Every one of them is Dutch. Nobody knows why.

It's **server-side only**. Friends join with a plain vanilla client: the mercenaries are vanilla **mannequins** (player models with vanilla skins), the menus are chest screens.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/server/) for Minecraft 26.3.
2. Put **Fabric API**, the **Market** mod (they're paid in Marks) and **`sellswords-<version>.jar`** in `mods/`.

Get the jars from the `sellswords-latest` release, or build it yourself: `./gradlew build` in the repo root (Market), then in `sellswords/` (Java 25). It's in the [LIB Pack](../lib-pack/README.md) too.

## Crafting: Mercenary Station

```
 _   Crossbow     _
Iron Sword  Target  Iron Sword
Gold Ingot  Gold Ingot  Gold Ingot
```

It's a target block with a gold name. Place it and right-click it. Breaking it gives the station back.

## Hiring

| What | How |
|---|---|
| Hire | Right-click the station → **Hire a Recruit**: ₥250 |
| Your squad | **3** mercenaries, plus one per 25 levels of **Leadership** (Skills): up to 7 |
| Roster | The station lists your squad: click one to call them back to that station. **Call everyone back** does them all |
| Cheaper | A Colonycraft **Guildhouse** has its own station: **20%** off, **35%** at tier 2, **50%** at tier 3 |

A recruit turns up next to the station with their own gear: a crossbow, an iron sword and an orange tunic. You never give them gear and you can't take theirs. They come with a Dutch name, a vanilla skin and a line under their name with their rank.

## Orders

Right-click a mercenary **with an empty hand** to open their menu (only you can; everyone else gets a shrug).

| Order | What they do |
|---|---|
| **Follow me** | Walk with you and fight what attacks you **and whatever you hit**: you're a hunting party. Monsters close to you too. Too far behind? They catch up. They climb aboard your **Ahoy** ship, **Blimey** airship or **Mobile Home** with you (rangers shoot from the rail), and come with you **through portals** |
| **Hold this spot** | Stay right there like a dog told to stay. Fight anything that comes within 16 blocks, **protect villagers**, then go back to the spot. Rangers don't leave it at all, so a Colonycraft **wall** makes a fine archer post |
| **Back to the station** | Go home and **patrol up to 50 blocks around the station**, protecting it and its villagers |
| **Hunting** | While following, also shoot cows, pigs, sheep, chickens and rabbits near you. Never named, leashed, baby or anyone's (a colony's ranch, say) |
| **Dismiss** | Sends them off for good. Click twice. No refunds |

A new hire idles at their station. **Log out** and your followers guard where they stood. **Die** and they guard the spot where you fell, and your things.

### The goat horn

Blow **any goat horn** (vanilla plays it as usual):

- with mercenaries following you: they all **hold** where they stand;
- with nobody following: everyone within 48 blocks **rallies** and follows you;
- **sneaking**: **Charge!** Everyone following goes for whatever you're looking at.

`/sellswords rally` and `/sellswords charge` do the same.

## Fighting

- Crossbow at range, sword up close. The Ranged path prefers the crossbow; the Melee path closes in and only shoots at things it can't reach.
- **Creepers** get shot, never hugged. Shield-bearers (no crossbow) leave them to you.
- They **never hurt** players, villagers, wandering traders, golems, tamed or named animals, or each other, not even by accident: their bolts fly straight past friends.
- Players can't hurt them either (dismiss them instead). Monsters can, and zombies and illagers go for them on sight.
- They heal slowly (1 to 3 health every 5 seconds, by rank). **Death is for good**: you get a message with their kill count, and their gear goes with them.
- They hit **illagers 25% harder** (they have history).

## Promotions

In the mercenary's menu, the middle rows are the promotion tree: the **Ranged path** across one row, the **Melee path** across another. Glass between the steps lights up **green** for ranks they have, **yellow** for the one they can take next, **grey** for later and **red** for the path they didn't take. Click the yellow one to pay the **gold ingots** and promote them: new armour, full health, a cheer. The **first promotion picks the path for good**, so it asks twice.

| Rank | Path | Gold | Health | Armour | Sword | Shot | Range | Special |
|---|---|---|---|---|---|---|---|---|
| Recruit | – | – | 24 | 4 | 5 | 4 | 16 | Crossbow and sword |
| Crossbowman | Ranged | 5 | 26 | 6 | 5 | 6 | 20 | |
| Marksman | Ranged | 10 | 30 | 8 | 6 | 8 | 24 | |
| Sharpshooter | Ranged | 20 | 34 | 10 | 6 | 10 | 28 | Steadier aim |
| **Musketeer** | Ranged | 40 | 40 | 10 | 7 | **20** | **36** | A musket: one huge shot every 2.5 s that goes through the first thing it hits |
| Swordsman | Melee | 5 | 32 | 9 | 7 | 4 | 14 | |
| Man-at-Arms | Melee | 10 | 40 | 13 | 9 | 4 | 14 | Full iron |
| Vanguard | Melee | 20 | 52 | 16 | 11 | – | – | Shield: blocks 40% of hits from the front |
| **Foestopper Bulwark** | Melee | 40 | 72 | 20 | 14 | – | – | Netherite, a shield that blocks 60% from the front, can't be knocked back, and bellows every monster within 10 blocks off you and onto himself |

## With the other LIB Pack mods

- **Skills**: a new skill, **Leadership**. XP for hiring (10), promotions (20 per rank) and every kill your mercenaries make (3, more for big mobs). Passive: up to +25% mercenary health and +4 squad size. Perks: **Quartermaster** (−6% promotion gold per rank), **Drillmaster** (+5% mercenary damage), **Shield Wall** (−4% damage taken by your mercenaries).
- **Colonycraft**: the **Guildhouse** (₥2,000) has a Mercenary Station where hiring is cheaper (20/35/50% by tier), and a Bounty Station with Fuck Illagers.
- **Fuck Illagers**: illagers your mercenaries kill drop fingers, and a contract boss they kill drops its skull. Their illager kills give you Bounty Hunting XP.
- **Ahoy, Blimey, Mobile Home**: following mercenaries take a free passenger seat when you board, and get off when you do.
- **Burlap Sack**: they don't fit in a sack. They've read their contract.
- **For mod makers**: `sellswords:api` in Fabric's ObjectShare (`station`, `remove`, `info`, `is_mercenary`), and the list `sellswords:board` for vehicles to seat mercenaries. Nobody needs anybody else to compile.

## Commands

- `/sellswords`: help
- `/sellswords list`: your squad, with ranks, orders, health, kills and where they are
- `/sellswords rally`, `/sellswords charge`: like the horn
- `/sellswords give station` (op): a Mercenary Station
- `/sellswords hire` (op): a free recruit at your feet, following you

## Config

`config/sellswords.json`: `hireCost` (250), `squadSize` (3), `promotionCostMultiplier` (1.0), `damageMultiplier` (1.0), `illagerBonus` (0.25), `stationRadius` (50).

## How it works

Each mercenary is two entities walking as one: an invisible, silent **wandering trader** with all its own goals taken out (the mod does its pathfinding, targeting and fighting, and monsters that hate wandering traders go for it), and a **mannequin** put where the trader is every tick, wearing the armour and holding the weapons. Hits on the mannequin land on the trader. Both carry a saved marker, so they're found again after a restart, a chunk reload or a portal. Mercenaries and stations are saved in `<world>/sellswords.json`.

CI (`.github/workflows/sellswords.yml`) boots a real server and puts a squad through it: a station placed and broken, a recruit hired (an invisible brain with no goals, a mannequin body in orange), the body keeping up, hits landing on the brain, no friendly fire, shooting a husk, both promotion paths to the top, the Bulwark's shield, the horn, following, patrolling, the API, dismissal, death for good, and saving and loading.
