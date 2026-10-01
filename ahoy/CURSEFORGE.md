# CurseForge listing: Ahoy

## Summary (the one-liner under the title)

A sailing ship in a bottle. Server-side, no client mod needed.

## Description

# ⛵ Ahoy: A Ship in a Bottle

Craft a bottle. Throw it at the sea. Get a **two-masted sailing ship** with room for you and 8 friends.

Ahoy is **server-side only**. Your friends join with a plain vanilla client and nothing to install.

---

## ✨ Features

- **A proper ship.** Two masts with sails, a quarterdeck with glowing windows, barrels and crates on deck, and **your ship's name painted on the stern**.
- **Captain + 8 passengers.** The owner takes the wheel. Everyone else gets the benches, the bow or the quarterdeck.
- **Two cargo holds** of 54 slots each. That's 108 slots of loot, or 108 slots of dirt. We don't judge.
- **Wind.** Each dimension has a slowly shifting wind. Sail with it for full speed. Sail into it for half speed.
- **Safe at sea.** Nobody aboard can be hurt. Drowned, guardians and phantoms get zapped away from the hull.
- **No fuel.** It's a sailing ship. The wind is free.
- **Pocket-sized.** Bottle the ship back up whenever you like. The name and all the cargo stay inside the bottle.
- **Light on the server.** The ship is built from about 50 display entities and never places a single block, so it won't wreck your terrain or your TPS.

---

## 🍾 Crafting: Ship in a Bottle

| | | |
|---|---|---|
| Wool | Glass Bottle | Wool |
| Chest | Any Boat | Chest |
| Planks | Planks | Planks |

**Rename the bottle in an anvil to name your ship.**

---

## 🧭 How to sail

| What | How |
|---|---|
| **Launch** | Right-click open water with the bottle (needs about 7 × 20 blocks of water). The stern starts where you click and the bow points where you look |
| **Board** | Right-click the ship. The owner gets the wheel |
| **Sail** | **W / S** raise and lower the sails, **A / D** steer, **Space** rings the bell |
| **Disembark** | **Shift**. You're put ashore if there's land next to the ship, otherwise into the water ("Man overboard!") |
| **Menu** | Right-click while aboard, or sneak + right-click from outside: cargo, switch seats, lock the ship, ring the bell, bottle it up |
| **Pack it away** | Menu → **Bottle it up** (owner only) |

**Good to know**
- The ship only moves on water and stops when it hits land or blocks.
- With nobody at the wheel, it slowly drifts to a stop and stays put.
- You don't walk around on deck. Everyone has a seat instead, which is also how real cruises work if you're doing them right.

---

## 🛠️ Commands

- `/ahoy`: info
- `/ahoy give`: gives you a Ship in a Bottle (ops only)

---

## 📦 Requirements

- Minecraft **26.3**
- **Fabric Loader** and **Fabric API**
- Java 25

Install it on the server. Clients don't need anything. It also works in singleplayer.

---

## 🧪 Tested on a real server

Every build launches a ship in a test harbour on a real dedicated server. It sails, turns, rams the harbour wall (and stops, like a responsible captain), gets bottled up, and checks that the name and cargo survive with nothing left behind.

---

Source and issues: [github.com/ThatCoffeelock/Market](https://github.com/ThatCoffeelock/Market)
