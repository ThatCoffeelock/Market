# Warehouse

A Fabric mod for Minecraft **26.3** that adds warehouses: big, sorted storage you build yourself.

- **Tens of thousands of items.** A **Warehouse Core** holds 2,048 items. Every **Storage Rack** touching it (or touching a rack that's already connected) adds 4,096 more, up to 64 racks: about 264,000 items.
- **It counts, it doesn't stack.** Items aren't kept in slots, they're counted ("cobblestone: 48,213"). Enchanted, named and damaged items are kept apart, so nothing loses its data.
- **Sorted.** Category tabs (ores & minerals, food, gear, building blocks, everything else), sorting by stock or by name, and a search.
- **As many warehouses as you like.** Every core is its own warehouse. A rack that touches two warehouses counts for neither, so building between them never quietly merges them.
- **Specialist warehouses.** Make one take only food, another only ores. Docks send each kind of cargo to its specialist first.
- **Fill it any way you like:** by hand, with hoppers, through an intake chest, with a [Cargo Train](../cargo-train/README.md), or from an [Ahoy](../ahoy/README.md) ship at a **Loading Dock**.

It's **server-side only**. Friends join with a plain vanilla client. The blocks are vanilla blocks (a cartography table, barrels and a lantern) and the screens are chest screens.

## Install

Put **Fabric API** and **`warehouse-<version>.jar`** in your server's `mods/` folder. Get the jar from the [warehouse-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/warehouse-latest), or get everything at once with the [LIB Pack](../lib-pack/README.md).

Ahoy and Cargo Train are optional. With them installed (version 1.1.0 or newer), ships and trains can unload into warehouses.

## Crafting

| Item | Recipe |
|---|---|
| Warehouse Core | `I B I` / `C T C` / `I C I`, where I = iron ingot, B = book, C = chest, T = cartography table |
| Storage Rack | Barrel + Chest + 2 Iron Ingots, anywhere in the grid |
| Loading Dock | Lantern + Chest + Lead, anywhere in the grid |

Rename the core in an anvil before you place it to name the warehouse. Ops can use `/warehouse give core|rack|dock`.

## Building one

1. Place a **Warehouse Core**. That's a new warehouse.
2. Place **Storage Racks** against the core, and more racks against those. Each one tells you whether it counted. To put a rack against a rack, **sneak** while placing it (right-clicking a rack opens the warehouse).
3. Want a second warehouse? Place another core somewhere that doesn't touch the first building.

## Using it

| What | How |
|---|---|
| Open it | Right-click the core or any of its racks |
| Take things out | Left-click: a stack. Right-click: one. Shift-click: as much as fits in your inventory |
| Put things in | Drop the item you're holding on the stock, shift-click items in your inventory, or click **Deposit your backpack** (everything except your hotbar) |
| Quick deposit | Sneak + right-click the core with an item in your hand |
| Tabs, sorting, search | Bottom row. Search: click, then type in chat |
| Settings | What it accepts, lock, rename, and how many racks count |
| Pack it up | Break the core. The stock stays inside the core item; place it anywhere to unpack |
| Your warehouses | `/warehouse list` |

**Locked** warehouses: anyone can bring things in (by hand, hopper, train or ship), but only the owner can take things out or break the core and its racks.

## Filling it automatically

| From | How |
|---|---|
| Hoppers | Point them into any counted Storage Rack. Its barrel gets emptied onto the shelves every second |
| Chests | Name a chest or barrel **Warehouse Intake** and put it against the core or a rack. Emptied every second |
| Trains | A Cargo Train **Drop-off Station** touching the core or a rack: the train unloads straight onto the shelves, no 27-slot limit |
| Ships | Build a **Loading Dock** on your pier (see below) |

## Loading Docks

A **Loading Dock** serves every warehouse whose core is within 48 blocks of it. Sail an Ahoy ship within 16 blocks and the captain's menu gets a **Loading Dock** button. You can also right-click the dock itself.

- **Unload all cargo:** specialist warehouses get their kind of cargo first, then the rest goes to the nearest general warehouses. Whatever doesn't fit stays aboard.
- **Load the ship:** click a warehouse in the dock screen. Everything you take out goes straight into the ship's holds.

## Notes

- If a rack is removed and the warehouse ends up over capacity, nothing is lost: it only lets things out until there's room again.
- If a core disappears without being broken by a player (an explosion, a piston), it drops as a packed-up core on the spot, with the stock inside.
- Everything is saved in `<world>/warehouses.json`. Sizes and distances are in `config/warehouse.json`.
- CI builds the mod, then boots a real server and builds two warehouses side by side. It checks that racks join the right one, that a rack between them is disputed and that a second core in the same building is refused. Then it fills them by hand, through an intake chest, a rack's barrel, a Cargo Train Drop-off Station and a Loading Dock (bread to the food warehouse, the rest to the general one), saves and reloads, packs a warehouse up and unpacks it somewhere else, and checks that a core that vanished drops its stock instead of losing it.
