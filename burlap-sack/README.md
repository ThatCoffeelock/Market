# Burlap Sack

A Fabric mod for Minecraft **26.3** for moving villagers to where *you* want them. They don't get a say.

- **Bag a villager or wandering trader**, carry them home in your inventory, let them out.
- Trades, level, XP, name, skin and inventory all come along.
- **Iron golems object.** Kidnapped villagers **hold a grudge**.

It's **server-side only**. Friends join with a plain vanilla client. The sack is a vanilla bundle with some custom data, so it renders without a client mod.

## Install

Put **Fabric API** and **`burlap-sack-<version>.jar`** in your server's `mods/` folder. Get the jar from the [burlap-sack-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/burlap-sack-latest), or build it with `./gradlew build` in `burlap-sack/` (needs Java 25).

## Crafting: Burlap Sack

```
 _  String  _
Leather  _  Leather
Leather Leather Leather
```

It's reusable. One sack holds one captive.

## How it works

| What | How |
|---|---|
| Bag someone | Hold the sack in your main hand and right-click a villager or wandering trader |
| Let them out | Right-click a block. They climb out facing you |
| Check who's inside | The sack's name and tooltip say who and what they are ("Bob, a level 3 farmer") |

Details:

- **Jobs:** a villager who has ever traded keeps their profession forever. A fresh one with no trades needs a workstation in their new home or they'll drop the job.
- **Old village:** their bed and workstation in the old village are freed up, so nobody's left waiting for a ghost.
- **Wandering traders** that you move never despawn. They live here now.
- **Iron golems** within 24 blocks go for you when you bag someone (not in creative).
- **Grudge:** the captive charges *you* a bit more for a few in-game days. It fades on its own. Stockholm syndrome is included.
- **Colonycraft workers** can't be bagged. Poaching staff from a colony is beneath you.
- **Sellswords mercenaries** can't be bagged either. They're armed, they're Dutch, and they've read their contract.
- **Bringing staff to a colony** is fine, though: with [Colonycraft](../colonycraft/README.md), a villager in a sack fills an empty job at any colony building for free (the building's page at the Town Hall). You get the sack back.
- The captive only exists inside the item. **If you drop the sack in lava, that's on you.**

## Commands

- `/burlapsack`: help
- `/burlapsack give` (op): get a sack
