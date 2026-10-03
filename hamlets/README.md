# Hamlets & Horrors

A Fabric mod for Minecraft **26.3** that adds random structures to new terrain. Some are lived in by villagers, others have been taken over by monsters.

| Structure | Who lives there | What's inside |
|---|---|---|
| **Cottage** | A villager family, sometimes a cat | Beds, a workstation, a crafting table, a loot chest, vegetable plots and a composter |
| **Haunted cottage** | Zombies, a witch or skeleton, and a **zombie villager** you can cure | A brewing corner, cobwebs, dead gardens, dungeon loot |
| **Castle** | Six villagers and an **iron golem** | Curtain walls, four towers, a two-floor keep with seven workshops and six beds, a courtyard farm, a well and a bell |
| **Ruined castle** | Skeletons and zombies in the keep, **bandits** (pillagers + a vindicator) camping in the courtyard | Crumbling towers, a spawner, rubble, stronghold and outpost loot |
| **Dungeon** | Monsters, and **prisoners** | A crypt on the surface, a spiral stair 16 blocks down, a hub and four rooms in random order: a **prison** (always), a crypt with a spawner, a treasury and a library |

- **Materials follow the biome**: oak, spruce (taiga and snow), birch, dark oak (dark forest and swamp), acacia (savanna) and sandstone (desert and badlands). Ruins get moss, cracks, holes and cobwebs.
- **Villager homes are safe**: hostile mobs don't spawn naturally inside them. Monster homes keep spawning their kind of monster.
- **Prisoners**: press the stone button in front of a cell's iron door to let them out.
- Structures only go on flat, dry land and stay clear of vanilla villages.

It's **server-side only**. Players join with a plain vanilla client. Structures only appear in chunks that haven't been generated yet, so explore somewhere new.

## Install

Put **Fabric API** and **`hamlets-<version>.jar`** in your server's `mods/` folder. Get the jar from the [hamlets-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/hamlets-latest). It's in the [LIB Pack](../lib-pack/README.md) too.

## Commands

| Command | Who | What |
|---|---|---|
| `/hamlets` | Everyone | Help |
| `/locate structure hamlets:castle` | Ops | Find the nearest one. Also `cottage`, `haunted_cottage`, `ruined_castle` and `dungeon` |
| `/hamlets build <cottage\|castle\|dungeon> <villagers\|monsters>` | Ops | Builds one where you stand, facing where you look |

## Tuning

Everything is data-driven, so a datapack can override it:

- `data/hamlets/worldgen/structure_set/*.json`: how often each structure appears (`spacing` and `separation` in chunks) and the weights between variants.
- `data/hamlets/tags/worldgen/biome/has_structure/*.json`: which biomes each one appears in.
- `data/hamlets/worldgen/structure/*.json`: which monsters keep spawning inside (`spawn_overrides`).

Default spacing: cottages every ~20 chunks, dungeons every ~28, castles every ~44.

## Notes

- A structure is stored as one piece plus a seed. The blueprint is re-run for each chunk it touches, so a castle that spans four chunks lines up perfectly.
- CI builds the mod, then boots a real dedicated server. It builds every variant, checks the inhabitants, beds, loot, spawners and prison cells, then finds each structure in real terrain with `/locate` logic and checks it generated.
