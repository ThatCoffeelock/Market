# Flintlock

A Fabric mod for Minecraft **26.3** that adds black-powder guns: a **Flintlock Pistol**, a **Musket** and a **Blunderbuss**. They hit much harder than a bow, but every shot needs special ammo and a slow reload.

It's **server-side only**. Friends join with a plain vanilla client. For singleplayer, install it like any other Fabric mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Put **Fabric API** and **`flintlock-<version>.jar`** in your `mods/` folder (the server's, for multiplayer).

Get the jar from this repo's **Releases** page (the `flintlock-latest` pre-release always has the latest green build), or from the **Actions** tab: open the latest green **Flintlock** run and download the `flintlock-mod` artifact. You can also build it yourself: run `./gradlew build` in `flintlock/` (needs Java 25).

## The guns

| | Flintlock Pistol | Musket | Blunderbuss |
|---|---|---|---|
| Damage | 9 (4.5 hearts) | **18** (9 hearts) | 8 pellets × 2.5, up to 20 point-blank |
| Reload | **2 s** | 4 s | 3 s |
| Ammo | Paper Cartridge | Paper Cartridge | Scattershot |
| Accuracy | Decent | Dead straight | It's a cone |
| Range | ~45 blocks | ~100 blocks | ~12 blocks, weaker with distance |
| Knockback | A nudge | A shove | **Sends things flying**, and kicks you backwards |

For comparison, a fully drawn bow does about 6 and a netherite sword does 8.

## Crafting

| Item | Recipe |
|---|---|
| Flintlock Pistol | 2×2: Iron Ingot, Flint / empty, Planks |
| Musket | Iron Ingot in the top-left and the center, Flint bottom-middle, Planks bottom-right |
| Blunderbuss | Like the musket, with Copper Ingots instead of iron (the bell-mouth is brass, obviously) |
| Paper Cartridge ×4 | Paper + Gunpowder + Iron Nugget, anywhere in the grid |
| Scattershot ×2 | Paper + Gunpowder + Flint + Gravel, anywhere in the grid |

Any planks work. The gun recipes unlock in the recipe book when you pick up flint, the ammo recipes when you pick up gunpowder.

Ops can also use `/flintlock give pistol|musket|blunderbuss` and `/flintlock give cartridges|scattershot [count]`.

## How to use them

| What | How |
|---|---|
| Reload | Right-click with an empty gun. A bar fills up in the action bar. **Keep holding that gun** until it's done: switch away and the reload is interrupted (the ammo is only used up at the end) |
| Fire | Right-click with a loaded gun |
| Check | A loaded gun looks like a loaded crossbow and says "Loaded" on its tooltip |

A gun stays loaded until you fire it. So you can load four pistols before a fight and swap between them, like a proper pirate. Holding right-click fires, reloads, fires again, and so on.

**Under the hood**: balls are simulated by the server with a swept collision check, so they can't skip through a wall or a mob at speed. They stop at the first block or mob they touch, never break blocks, and quietly vanish if they fly into unloaded chunks. The damage is arrow damage, so **shields block it** and Projectile Protection helps. All the blunderbuss pellets that hit a mob in the same tick are added up into one hit, so they all count. Knockback respects knockback resistance (ravagers barely budge), and creative players don't get shoved.

## Commands

- `/flintlock`: help and recipes
- `/flintlock give pistol|musket|blunderbuss` (op)
- `/flintlock give cartridges|scattershot [count]` (op): 16 by default

## Notes

- The guns are carrots on a stick with their durability removed, and they look like crossbows. The ammo is paper that looks like a candle (cartridges) or a bundle (scattershot). Custom data keeps them apart from the real thing, so vanilla clients can show them.
- CI (`.github/workflows/flintlock.yml`) builds the mod, then boots a real dedicated server. It checks every item and that each recipe makes the same item as `/flintlock give`. Then it lines up three villagers on a shooting range and shoots each one with a different gun. It checks the musket took 18 health off, the pistol 9, and that the blunderbuss pellets added up and sent their villager flying further than the other two. Last, it fires into the floor and checks the ball stops there.
