# Mobile Home

A Fabric mod for Minecraft **26.3** that adds drivable vehicles you can live out of:

- **Camper Van**: 4 seats, a bed, barrels everywhere, a surfboard on the roof. Fast, and steers like a car.
- **Tank**: 3 seats (the commander sticks their head out of the hatch), turns on the spot, climbs 2-block walls, and burns twice the fuel.

Both have **54 slots of storage**, a **workbench**, an **ender stash**, a **lock**, and a **monster force field**.

It's **server-side only**. Friends join with a plain vanilla client. The vehicles are built out of vanilla display entities, so they render without a client mod. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`mobile-home-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

To get the jar, open this repo's **Actions** tab, then the latest green **Mobile Home** run, and download the `mobile-home-mod` artifact. You can also build it yourself: run `./gradlew build` in `mobile-home/` (needs Java 25).

## Crafting

| Vehicle | Top row | Middle row | Bottom row |
|---|---|---|---|
| Camper Van | Glass, Glass, Glass | Iron Block, Chest, Iron Block | Minecart, Furnace, Minecart |
| Tank | Iron Block, Dispenser, Iron Block | Obsidian, Chest, Obsidian | Minecart, Furnace, Minecart |

Ops can also use `/mobilehome give van` or `/mobilehome give tank`.

## How to use it

| What | How |
|---|---|
| Park it | Right-click the ground with the vehicle item. It faces where you're looking |
| Get in | Right-click the vehicle. The owner gets the wheel, guests get a passenger seat |
| Drive | **W/S** gas and brake/reverse, **A/D** steer, **Ctrl** turbo (uses double fuel), **Space** honk |
| Get out | **Shift**. You're put down next to your door |
| Menu | Right-click while seated, or sneak + right-click from outside |
| Refuel | Sneak + right-click the vehicle holding fuel, or drop fuel in the menu's fuel slot |
| Pack up | Menu → **Pack up** (owner only). Fuel and storage go into the item |

**Fuel**: anything a furnace burns. That includes coal, charcoal, wood, blaze rods and lava buckets (you get the bucket back). One coal gives about 2½ minutes of van driving, and a lava bucket about half an hour. A full tank holds 64 coal's worth.

**Safety**: while you're seated:
- nothing can hurt you (except `/kill` and the void)
- monsters that come close get zapped and shoved away
- mobs forget they were targeting you
- you can't burn or drown

**Water and lava**: the vehicles float. It's an amphibious van now. We didn't plan it, but we're keeping it.

**Lock**: the owner can lock the vehicle from the menu. Then only the owner can drive it or open the storage. Passengers can always get in.

## Commands

- `/mobilehome`: help and recipes
- `/mobilehome give <van|tank>` (op): get a vehicle item
- `/mobilehome refuel` (op): fill up the vehicle you're sitting in

## Notes

- **Sellswords**: mercenaries following you climb aboard when you do, each taking a free passenger seat (never the driver's seat), and get off when you do. Rangers shoot from their seats.
- Driving happens on the server. On a laggy server the ride feels a bit floaty, the same as vanilla minecarts.
- The hitbox is a box around the middle of the vehicle. Click near the centre.
- CI (`.github/workflows/mobile-home.yml`) builds the mod, then boots a real dedicated server. It parks a van and a tank, refuels them, drives the van forward and up a step, spins the tank, and checks that packing up keeps fuel and storage.
