# Cannon

A Fabric mod for Minecraft **26.3** that adds an **aimable cannon** you build, place and man, plus **cannonballs** made from iron and gunpowder.

It's **server-side only**. Friends join with a plain vanilla client. The cannon is built out of vanilla display entities, so it renders without a client mod. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`cannon-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `cannon-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Cannon** run and download the `cannon-mod` artifact. You can also build it yourself: run `./gradlew build` in `cannon/` (needs Java 25).

## Crafting

| Item | Recipe |
|---|---|
| Cannon | Top row: Iron Block, Iron Block, Iron Block. Middle row: Log, Dispenser, Log. Bottom row: Log, empty, Log |
| Cannonball ×2 | 1 Iron Ingot + 1 Gunpowder, anywhere in the grid |

Any kind of log works. The recipes unlock in the recipe book once you pick up a dispenser and an iron block, or gunpowder.

Ops can also use `/cannon give cannon` and `/cannon give cannonballs [count]`.

## How to use it

| What | How |
|---|---|
| Place it | Right-click the ground with the cannon. It points where you're looking |
| Man it | Right-click the cannon. You sit behind the breech |
| Aim | Just look around. The cannon turns (6°/tick) and the barrel tilts (3°/tick, from -10° to 60°) to follow your view |
| Fire | **Space**. Uses one cannonball from your inventory (free in creative) |
| Get off | **Shift** |
| Pick it up | Sneak + right-click the cannon (owner only, or anyone in creative) |

While you're manning it, the action bar shows the elevation, the estimated range over flat ground, how many cannonballs you have left, and the reload bar. Reloading takes 2 seconds.

**Ballistics**: the ball leaves the barrel at 48 blocks per second and drops under gravity. At 45° it flies about 80 blocks. It explodes on the first block or mob it touches, with the power of a creeper (3), and breaks blocks like TNT. If it lands in water it splashes and sinks. If it flies into unloaded chunks it vanishes quietly instead of loading them.

**Friendly fire is on.** If you fire at the wall right in front of you, you'll be standing in the explosion. That's between you and your respawn point.

## Commands

- `/cannon`: help and recipes
- `/cannon give cannon` (op): get a cannon
- `/cannon give cannonballs [count]` (op): get cannonballs (16 by default)

## Notes

- The cannon item is a glowing dispenser and the cannonball is a grey firework star, so vanilla clients can show them. Custom data keeps them apart from the real thing.
- The hitbox is a box around the carriage. Click near the middle.
- CI (`.github/workflows/cannon.yml`) builds the mod, then boots a real dedicated server. It places a cannon, swings and elevates it with a fake gunner, checks the elevation limit, fires at a dirt wall and checks there's a hole in it, then packs the cannon up and checks every entity is gone.
