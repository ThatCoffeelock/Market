# Zombie Apocalypse

A Fabric mod for Minecraft **26.3** that turns your world's zombies from a nuisance into a problem. They come in **hordes**, they move as a **herd**, they hunt by **sound**, and every seventh night they all come at once.

It's **server-side only**. Players join with a plain vanilla client. It is **not** part of the LIB Pack: you install it on its own, because not everyone wants their colony eaten.

## What changes

| Thing | What happens |
|---|---|
| **Hordes** | Every minute or so at night, a horde of 4–8 zombies rises 24–44 blocks from each survivor. Hordes get bigger every day you survive (up to 24) |
| **Light keeps them out** | Hordes follow **vanilla's spawn rules**, zombie by zombie: block light 0, a dark sky, a floor monsters can stand on, room to stand. Light up your base and nothing spawns inside your walls. They can still walk up to those walls and knock |
| **Herding** | Each horde follows a **leader**. An idle horde drifts toward the nearest survivor it can smell (80 blocks). Loose zombies near a horde join it, and two hordes that meet merge into one bigger problem (up to 40) |
| **One sees you, they all do** | When one horde member spots you, every member within 32 blocks goes after you too. If they lose you, they search where they last saw you |
| **They hunt by sound** | Breaking blocks, fighting and sprinting draw in nearby zombies. Sneaking doesn't |
| **Bells** | Ringing a bell lures **every zombie within 64 blocks** to it for 40 seconds. Ring one on the far side of town, then go loot the other side. That's how you herd a horde |
| **Doors** | Horde zombies stuck at a door while they can see you chew through **wooden doors, trapdoors, fence gates and glass**. Every extra zombie at the same door makes it faster. Iron and copper hold |
| **Horde Night** | Every **7th night**. You get a warning that morning. Hordes come three times as often, they're 75% bigger and 20% faster, and bites are twice as likely to infect |
| **Infection** | A zombie bite has a 12% chance to infect you. Over 20 minutes you get hunger, then weakness and nausea, then slowness and mining fatigue, and then you **turn**. A **golden apple cures it**. A totem of undying burns it out of you |
| **Rising** | Die infected, or get killed by a zombie, and a zombie with your name gets up where you fell. It picks up loot, so it'll put your diamond armour on. Go get your stuff back |

## Install

Put **Fabric API** and **`apocalypse-<version>.jar`** in your server's `mods/` folder. Get the jar from the [apocalypse-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/apocalypse-latest). Peaceful difficulty turns the hordes off.

## Commands

| Command | Who | What |
|---|---|---|
| `/apocalypse` | Everyone | Day count, the next Horde Night, hordes nearby, and whether you're infected |
| `/apocalypse help` | Everyone | How it all works |
| `/apocalypse admin horde [size]` | Ops | Summons a horde near you |
| `/apocalypse admin hordenight start\|stop` | Ops | Horde Night right now (at least 5 minutes), or calls it off |
| `/apocalypse admin infect\|cure <player>` | Ops | Bites or cures someone |
| `/apocalypse admin clear` | Ops | Removes every horde zombie |
| `/apocalypse admin reload` | Ops | Re-reads the config |

## Config (`config/apocalypse.json`)

Created on first start. The main ones:

| Key | Default | Meaning |
|---|---|---|
| `nightHordeSeconds` / `dayHordeSeconds` | 75 / 0 | Time between hordes per player. Daytime hordes are off: under vanilla's rules daylight is too bright for them anyway. If you turn them on, they're husks |
| `hordeSizeMin` / `hordeSizeMax` / `hordeGrowthPerDay` / `hordeSizeCap` | 4 / 8 / 0.5 / 24 | How big hordes are, and how fast they grow |
| `maxZombiesNearPlayer` | 40 | No new hordes past this many zombies within 64 blocks (70 on Horde Night) |
| `scentRange` / `shareAggroRange` / `joinRange` / `maxHordeSize` | 80 / 32 / 10 / 40 | Herding |
| `hordeNightEvery` | 7 | 0 turns Horde Night off |
| `noise`, `bellRadius`, `bellSeconds` | true, 64, 40 | Sound and bells |
| `infection`, `infectionChance`, `infectionMinutes` | true, 0.12, 20 | Bites |
| `riseAsZombie` | true | The fallen get back up |
| `breakDoors`, `breakPlanks`, `breakSeconds` | true, false, 10 | Door chewing. `breakPlanks` lets them through wooden walls too. Good luck |

## Notes

- Days count time survived (game time), so sleeping doesn't skip ahead to Horde Night, and it doesn't skip Horde Night either. You can still sleep through it. Coward.
- Admin hordes (`/apocalypse admin horde`) ignore the light rules. That's the point of them.
- Hordes roam the surface around you. Caves are relatively safe. Relatively.
- Horde zombies aren't persistent: they despawn like any other monster once you're far away. Risen players are persistent, so your gear doesn't vanish.
- CI builds the mod, then boots a real dedicated server. In a glowstone pen in the sky it spawns two hordes and a straggler, then checks that they merge and recruit, that one sighting turns the whole horde, that three zombies get through a window faster than one, that a bell pulls the horde across the pen, that Horde Night speeds them up, and that a fallen player gets back up. It also checks the light rule: no horde rises on a lit floor, zombies may spawn in a sealed dark room, and one torch in that room stops them.
