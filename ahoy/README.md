# Ahoy

A Fabric mod for Minecraft **26.3** that adds a big sailing ship that comes in a bottle.

- **Anchored, it's real blocks.** Walk the deck, climb down the hatch into the hold, sleep in the bunks (they skip the night like any bed), and build onto it. Anything you attach above the deck sails along.
- **At sea, it sails.** Take the wheel and the ship lifts into a moving model. Everyone on board sits down where they were standing. Drop anchor and it turns back into blocks, snapped to the grid.
- **Cargo:** two bays of 54 slots each, opened from the barrels in the hold or from the captain's menu.
- **Crew:** the captain plus 8 fixed passenger spots, and anyone already on deck when you set sail gets a spot where they stand.
- **Wind:** a slowly shifting wind per dimension. Sailing with it is full speed, into it is half speed.
- **Safety at sea:** nobody aboard can be hurt, and drowned, guardians and phantoms get zapped away from the hull.
- **No fuel.** It's a sailing ship.

It's **server-side only**. Friends join with a plain vanilla client.

## Install

Put **Fabric API** and **`ahoy-<version>.jar`** in your server's `mods/` folder. Get the jar from the [ahoy-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/ahoy-latest).

## Crafting: Ship in a Bottle

| Top | Middle | Bottom |
|---|---|---|
| Wool, Glass Bottle, Wool | Chest, any Boat, Chest | Planks, Planks, Planks |

Rename the bottle in an anvil to name your ship. The name shows on the stern. Ops can use `/ahoy give`.

## How to use it

| What | How |
|---|---|
| Launch | Right-click open water with the bottle. It needs about 7 × 20 blocks of water, 4 deep, and the ship points where you look |
| Set sail | Right-click the **wheel** (the grindstone at the back) → **Set sail** |
| Sail | **W/S** sails up/down, **A/D** rudder, **Space** rings the bell |
| Drop anchor | **Shift** (as captain), or the menu. It needs a bit of open water, not right against the shore |
| Get off at sea | **Shift** as a passenger: you jump overboard. "Man overboard!" |
| Board at sea | Right-click the ship |
| Cargo | Right-click a barrel in the hold (port side = bay A, starboard = bay B) |
| Put it away | Wheel → **Bottle it up** (owner only). Cargo and everything you built on it come along |

Items left in chests or furnaces on board move into the cargo hold when you set sail, so nothing is lost.

## Notes

- Only the owner can break the ship's blocks. Nobody can break the wheel.
- The sailing model uses one block display per visible block (about 500 for the default ship). Sailing straight is cheap. Turning re-aims every piece every other tick, which is the expensive moment. That's fine for a few ships on a small server.
- CI builds the mod, then launches a ship in a test harbour. It sets sail, sails and turns, drops anchor, bottles the ship up and checks that the cargo and all 651 blocks survive.
