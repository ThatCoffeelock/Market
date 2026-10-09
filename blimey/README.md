# Blimey

A Fabric mod for Minecraft **26.3** that adds a riveted iron airship that runs on diesel, and bombs to drop out of it.

- **A metal airship:** an iron cigar of an envelope with copper ribs and tail fins, a glazed gondola slung underneath, two diesel engines on outriggers, and its name painted down both flanks.
- **Captain, bombardier + 4 passengers.** The owner takes the controls; friends take the bomb hatch and the benches.
- **Flies anywhere.** Climb to just under the build limit, cruise, hover, land on any flat-ish ground. Bail out mid-air and you get a parachute (it's a bedsheet).
- **Runs on diesel**, from a [Fossil Fool](../fossil-fool/README.md) Refinery. A bucket keeps it cruising for about five minutes. Run dry and it sinks gently to the ground.
- **Expensive.** A netherite ingot and two blocks of diamond to build, a diesel habit to fly, and refits that cost blaze rods, ghast tears and more netherite.
- **Refits** at the Engineer: faster engines, better fuel economy, and from two up to five 54-slot cargo holds.
- **Piloteering**, a new [Skills](../skills/README.md) skill: fly and bomb to level it, burn less diesel, fly faster, drop bombs for free.
- **Bombs:** small, big and huge. Unlike a [Cannon](../cannon/README.md)'s cannonballs, they wreck buildings.
- **Light on the server.** Built like an Ahoy ship: display entities only, no blocks are ever placed.

It's **server-side only**. Friends join with a plain vanilla client.

## Install

Put **Fabric API** and **`blimey-<version>.jar`** in your server's `mods/` folder. Get the jar from the [blimey-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/blimey-latest). It's also in the [LIB Pack](../lib-pack/README.md). Without Fossil Fool installed there's no diesel, so it never leaves the ground.

## Crafting

| Item | Top | Middle | Bottom |
|---|---|---|---|
| **Flat-Pack Airship** | Phantom Membrane, Netherite Ingot, Phantom Membrane | Block of Iron, Blast Furnace, Block of Iron | Block of Diamond, Piston, Block of Diamond |
| **Small Bomb** | –, String, – | Iron Ingot, TNT, Iron Ingot | –, Iron Ingot, – |
| **Big Bomb** | Iron Ingot, TNT, Iron Ingot | TNT, Block of Iron, TNT | Iron Ingot, TNT, Iron Ingot |
| **Huge Bomb** | Block of Iron, TNT, Block of Iron | TNT, End Crystal, TNT | Block of Iron, TNT, Block of Iron |

Rename the Flat-Pack Airship in an anvil to name your airship. Ops can use `/blimey give` (an airship and four of each bomb).

## How to fly it

| What | How |
|---|---|
| Unfold | Right-click the ground with the Flat-Pack Airship. It needs about 9 × 26 blocks of clear ground and 14 blocks of sky. The stern goes where you click, the bow points where you look |
| Fuel up | Sneak + right-click it → **Fuel tank**, and put Buckets of Diesel in. The engines take one at a time and hand back the empty bucket |
| Board | Right-click it. The owner gets the controls |
| Fly | **W/S** throttle, **A/D** turn (works on the spot too), **Space** climb, **Ctrl** (sprint) descend. Let go of Space/Ctrl and it holds its height |
| Get off | **Shift**. On the ground you step out; in the air you bail out with 30 seconds of slow falling |
| Menu | Right-click with an **empty hand** while aboard (or `/blimey menu`), or sneak + right-click it from outside: holds, fuel tank, switch seat, Engineer, lock, fold it up |
| Put it away | Land, then menu → **Fold it up** (owner only). Name, refits, diesel and cargo stay inside the item |

The captain's action bar shows speed, height, and the diesel left (buckets in the tank plus what's in the engines).

## Diesel

| Doing | Burn (at Fuel economy –) |
|---|---|
| Parked, nobody touching anything | Nothing |
| Hovering | 35% of cruising: a bucket lasts about 14 minutes |
| Cruising at top speed | A bucket every 5 minutes |
| Climbing | +30% on top |
| Flat out with Racing engines | 1.5× cruising: a bucket every ~3¼ minutes |

With the tank dry the engines die, you can't steer, and it sinks at a dignified 2.4 blocks a second until it lands.

## Engineer: refits

Menu → **Engineer**. Paid in materials from the captain's inventory (free in creative). Refits stay with the airship when it's folded up.

| Track | Level I | Level II | Level III |
|---|---|---|---|
| **Engines** (top speed) | Bigger propellers, +25%: 6 Block of Iron, 8 Piston, 32 Copper Ingot | Turbochargers, +50%: 2 Block of Diamond, 16 Blaze Rod, 8 Piston | Racing engines, +80%: 2 Netherite Ingot, 16 Phantom Membrane, 16 Blaze Rod |
| **Fuel economy** (all burn) | Tuned carburettors, –20%: 6 Block of Redstone, 4 Comparator, 16 Gold Ingot | Riveted gas cells, –35%: 12 Block of Iron, 12 Phantom Membrane, 4 Block of Gold | Helium envelope, –50%: 8 Ghast Tear, 24 Phantom Membrane, 2 Block of Diamond |
| **Cargo holds** (A and B to start: 108 slots) | Cargo C (162 slots): 8 Chest, 4 Block of Iron | Cargo D (216): 16 Barrel, 8 Block of Iron | Cargo E (270): 8 Shulker Shell, 8 Block of Iron |

Faster engines burn more at full throttle; the economy refits cut everything, hovering included.

## Piloteering (with the Skills mod)

| | |
|---|---|
| XP | 0.4 per block flown under power as captain; a bomb you dropped going off: 5 / 10 / 20 (small / big / huge), +6 per mob caught |
| Passive | Up to 30% less diesel burned at level 100 (on top of the Fuel economy refits) |
| **Ace** | +4% airship top speed per rank |
| **Bombardier** | 6% chance per rank that a dropped bomb isn't used up |
| **Payload** | +5% bomb blast per rank |

The captain's skill counts for flying; whoever drops the bomb gets the bombing XP and perks. Bombs lit on foot give no XP.

## Bombs

| Bomb | Blast | Looks like |
|---|---|---|
| Small | 4 (a block of TNT) | TNT |
| Big | 7 (a wither) | A coal-black ball |
| Huge | 12 (bring a bucket of water for the lake) | A purple-glowing respawn anchor |

- **From an airship:** hold a bomb and right-click while aboard (any seat). It drops out of the hatch under the gondola and goes off on the first block, mob or water it hits. It keeps the airship's speed, so it lands ahead of where you let go: lead your target. No bombing while parked.
- **On foot:** right-click a block to set one down with a lit fuse (4 seconds). Then run.
- Crew aboard don't take blast, fall or fire damage. Everyone and everything underneath does.

## Commands

| Command | Who | What |
|---|---|---|
| `/blimey` | everyone | Help |
| `/blimey menu` | aboard | The airship's menu |
| `/blimey tank` | aboard | The fuel tank |
| `/blimey give` | ops | A Flat-Pack Airship and four of each bomb |
| `/blimey reload` | ops | Re-read `config/blimey.json` |

## Config (`config/blimey.json`)

| Key | Default | Meaning |
|---|---|---|
| `ticksPerBucket` | `6000` | How long a bucket of diesel lasts at cruising speed (20 ticks = 1 second) |
| `hoverBurn` | `0.35` | Share of the cruising burn that hovering costs |
| `climbBurn` | `0.3` | Extra burn while climbing |
| `topSpeed` | `0.5` | Blocks per tick before refits (0.5 = 36 km/h) |
| `ceilingMargin` | `4` | How far under the build limit the top of the envelope stays |
| `smallBombPower`, `bigBombPower`, `hugeBombPower` | `4`, `7`, `12` | Explosion power (creeper 3, TNT 4) |
| `bombsBreakBlocks` | `true` | Set to `false` and bombs are as harmless to buildings as cannonballs |
| `fuseSeconds` | `4` | Fuse on a bomb set down by hand |

## Notes

- Bombs in flight aren't saved: one still falling when the server stops is gone.
- Airships stop at the edge of loaded chunks rather than flying off into nothing.
- CI (`.github/workflows/blimey.yml`) builds the mod, then boots a real dedicated server and runs a smoke test that flies an airship, runs it dry, bombs a stone floor from the air and with a fuse, buys refits and folds it up.
