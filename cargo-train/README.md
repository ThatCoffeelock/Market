# Cargo Train

A Fabric mod for Minecraft **26.3** that adds a little **cargo train** that runs on your rails by itself.

- **A locomotive and up to four cargo wagons** (27 slots each, 108 in total). A yellow-and-blue diesel shunter with a cab you can sit in, and open goods wagons whose crates pile up as they fill.
- **It drives itself.** Put it on a line and it runs to the end, turns around, runs back, and keeps doing that.
- **Stations are chests.** Name a chest or barrel **Pickup Station**, **Drop-off Station** or **Swap Station**, put it next to the track, and the train stops there to load, unload or both.
- **Uses vanilla rails**: normal, powered, detector and activator rails, curves, slopes, loops, and redstone-switched junctions.
- **Keeps hauling when you're away.** A running train keeps the chunks it's in loaded.

It's **server-side only**. Friends join with a plain vanilla client. The train is built out of vanilla display entities, so it renders without a client mod. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`cargo-train-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `cargo-train-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Cargo Train** run and download the `cargo-train-mod` artifact. You can also build it yourself: run `./gradlew build` in `cargo-train/` (needs Java 25).

## Crafting

| Item | Recipe |
|---|---|
| Cargo Train | Furnace Minecart + Chest Minecart + Redstone Block, anywhere in the grid |
| Cargo Wagon | Chest Minecart + Iron Ingot, anywhere in the grid |
| Pickup Station | Chest + Rail, anywhere in the grid |

You don't have to craft stations: renaming any **chest, trapped chest or barrel** in an anvil works too. The name just has to contain "station" and one of "pickup", "drop-off" or "swap" (any capitals, spaces or dashes).

Ops can also use `/train give train`, `/train give wagon` and `/train give station [pickup|dropoff|swap]`.

## How to use it

1. Lay a line of rails. It needs at least 4 rails in a row where you put the train down.
2. Put stations **right next to the track** (beside a rail, or one block lower beside it). A barrel can also go right **under** a rail.
3. Right-click a rail with the Cargo Train. The locomotive points where you're looking and the wagon goes behind it. Off it goes.

| Station | What the train does there |
|---|---|
| **Pickup Station** | Loads everything in the chest into the wagon (as much as fits) |
| **Drop-off Station** | Unloads the wagon into the chest (as much as fits) |
| **Swap Station** | Unloads its cargo into the chest, then loads whatever was in the chest before |

**Sneak + right-click a station with an empty hand** to switch it to the next mode. The chest's name changes with it, so you can always see what it is when you open it.

A typical setup: a **Pickup Station** at your mine and a **Drop-off Station** at home. Point a hopper line or a farm at the pickup chest and the train does the rest. Use **Swap Stations** at both ends if you want stuff to go both ways (ore home, food back to the mine).

| What | How |
|---|---|
| Ride along | Right-click the locomotive. You sit in the cab |
| Menu | Sneak + right-click the locomotive (or right-click it while you're in the cab): start/stop, ride, cargo, horn, lock, pick up |
| Cargo | Right-click a wagon (or use the wagon buttons in the menu) |
| More wagons | Right-click the train with a **Cargo Wagon** while it stands still (stopped at a station counts). It goes on the back. Up to 4 |
| Fewer wagons | Menu → **Uncouple the last wagon** (it has to be empty). You get the Cargo Wagon back |
| Drive it yourself | Stop the train in the menu, sit in the cab, then **W/S** to drive. No station stops while you drive |
| Horn | **Space** in the cab, or the menu. Players standing on the track get honked at; mobs get shoved off |
| Get off | **Shift** |
| Pick it up | Menu → **Pick up the train** (owner, or anyone in creative). Empty the wagons first. You get the train and every extra wagon back |

## How it runs

- **Shuttling**: it goes to the end of the line, waits a moment, and comes back. On the way back the locomotive pushes the wagons, like a real push-pull train. On a **loop** it just keeps going round.
- **Stops**: it brakes for every station on the line, in both directions, and waits 3 seconds while it loads (with a little two-tone station chime). It serves each station once per visit.
- **Wagons**: stations load the front wagon first and spill over into the next, and unload them front to back. Each wagon needs about 2¼ rails, so a full train is about 11 rails long. Make sure your line (and your end-of-line stations) have room for it.
- **Speed**: up to 8 blocks a second (29 km/h), as fast as a vanilla minecart, whatever its length. Longer trains take longer to get going. It slows down for stations and the end of the line.
- **Changing the track while it runs is fine.** Break a rail and it stops at the gap and turns around. Lay more rails at the end and it keeps going. Flip a junction with redstone and it takes the new route.
- **Chunks**: while it's running (or someone is driving it) it keeps the chunks it's in and the ones just ahead loaded, with the same short-lived tickets a flying ender pearl uses. A parked train doesn't load anything. After a server restart, a train waits until someone comes near it before it carries on.
- **Lock** (owner only): nobody else can drive it, start or stop it, couple wagons or open the cargo.
- The driver can't suffocate in the cab, so low tunnels are fine.
- Trains don't collide with each other. Two trains on one line will drive straight through each other like ghosts. Give each train its own line.

## Commands

- `/train`: help and recipes
- `/train give train` (op): get a train
- `/train give station [pickup|dropoff|swap]` (op): get a station chest

## Notes

- The train item is a glowing furnace minecart. Custom data keeps it apart from the real thing, so a plain furnace minecart still works normally.
- Only single chests count: each half of a double chest is its own station (27 slots).
- CI (`.github/workflows/cargo-train.yml`) builds the mod, then boots a real dedicated server and lays an L-shaped line with a curve and a slope. A Pickup Station chest at the start gets 64 cobblestone, 10 diamonds and a named sword. The train has to refuse a second wagon where there's no track for it, load the cargo, take a second wagon at the station, survive being unloaded and loaded again (both wagons rebuilt), carry everything round the bend and up the slope to a Drop-off Station barrel (sword name intact), turn around, stop with its last wagon right at a rail that gets broken behind it, turn around again, park, and get packed up into a train and a wagon item without leaving a single entity behind. It also checks the swap logic, that a full first wagon spills over into the second, and that an ordinary chest next to the track is left alone.
