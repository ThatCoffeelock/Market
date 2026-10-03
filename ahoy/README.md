# Ahoy

A Fabric mod for Minecraft **26.3** that adds a big sailing ship that comes in a bottle.

- **A two-masted ship** with sails, a quarterdeck with glowing windows, barrels and crates on deck, and its name painted on the stern.
- **Captain + 8 passengers.** The owner takes the wheel; friends take the benches, the bow and the quarterdeck.
- **Cargo:** two holds of 54 slots each.
- **Wind:** a slowly shifting wind per dimension. Sailing with it is full speed, into it is half speed.
- **Safety at sea:** nobody aboard can be hurt, and drowned, guardians and phantoms get zapped away from the hull.
- **No fuel.** It's a sailing ship.
- **Light on the server.** It's built like the Mobile Home van: about 50 display entities, and no blocks are ever placed. You can't walk around on deck, but everyone gets a seat.

It's **server-side only**. Friends join with a plain vanilla client.

## Install

Put **Fabric API** and **`ahoy-<version>.jar`** in your server's `mods/` folder. Get the jar from the [ahoy-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/ahoy-latest).

## Crafting: Ship in a Bottle

| Top | Middle | Bottom |
|---|---|---|
| Wool, Glass Bottle, Wool | Chest, any Boat, Chest | Planks, Planks, Planks |

Rename the bottle in an anvil to name your ship. Ops can use `/ahoy give`.

## How to use it

| What | How |
|---|---|
| Launch | Right-click open water with the bottle (about 7 × 20 blocks of water). The stern starts where you click and the ship points where you look |
| Climb aboard | Right-click the ship. The owner gets the wheel |
| Sail | **W/S** sails up/down, **A/D** rudder, **Space** rings the bell |
| Get off | **Shift**. You're put ashore if there's land next to the ship, otherwise in the water ("Man overboard!") |
| Menu | Right-click with an **empty hand** while aboard (or type `/ahoy menu`), or sneak + right-click the ship from outside: cargo, switch seat, lock, bell, bottle it up |
| Everything else | Aboard, right-click does what it does ashore: **fish, shoot, throw, eat, drink**, use blocks and mobs in reach (a chest on the pier, a villager on the dock). The menu only opens when your hand is empty and there's nothing else to do |
| Put it away | Menu → **Bottle it up** (owner only). The name and cargo stay inside the bottle |

## Loading Docks

With the [Warehouse](../warehouse/README.md) mod installed, moor within 16 blocks of a **Loading Dock** and the captain's menu gets a **Loading Dock** button: unload all the cargo into the warehouses ashore, or browse a warehouse and load its stock straight into the holds.

Other mods can find ships, reach their holds and add buttons to the captain's menu through `AhoyApi`.

## Notes

- The ship stops when it hits land or blocks, and it only moves on water.
- When nobody is at the wheel, the ship slowly drifts to a stop and stays put.
- The ship's click hitbox surrounds the deck, so the game thinks a passenger is always pointing at the ship, and never uses what's in their hand while pointing at one. The server works around that: it finds what the passenger is really pointing at and does what vanilla would have done. Left-clicking (attacking, mining) from a seat still isn't passed through.
- CI builds the mod, then launches a ship in a test harbour on a real server. A fake passenger casts and reels a fishing rod, draws a bow, milks a cow and opens a crafting table from a seat, and only gets the ship's menu with an empty hand. Then it sails, turns, rams the harbour wall (and stops), bottles the ship up and checks that the name and cargo survive and that nothing is left behind.
