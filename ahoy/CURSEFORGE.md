# CurseForge listing: Ahoy

## Summary (the one-liner under the title)

A sailing ship in a bottle. Server-side, no client mod needed.

## Description

# ⛵ Ahoy: A Ship in a Bottle

Every sailor has seen one on a dusty shelf: a little ship, sails and all, sealed inside a glass bottle. Ahoy lets you craft one, walk down to the shore and uncork it. The bottle gives way to a full-sized **two-masted sailing ship**, ready to carry you and your friends across the ocean.

The best part for server owners is that Ahoy runs **entirely on the server**. Your players join with a plain vanilla client. There's nothing for them to download, no modpack to keep in sync and no version mismatches to sort out on a Friday night.

---

## The ship

She isn't a reskinned boat. Two masts carry broad white sails over a long wooden hull. At the back, a raised quarterdeck has windows that glow warmly after dark. Barrels and crates sit on the deck as if she's just come back from a trading run. **Her name is painted across the stern**, so every ship in the harbour is unmistakably someone's.

To name her, rename the bottle in an anvil before you launch it. Choose wisely. *Boaty McBoatface* has been taken many times over.

## The crew

One ship has room for **nine people**. The owner always takes the wheel and does the steering, the worrying and the blaming. Up to eight friends fill the benches, the bow and the quarterdeck. Nobody walks around on deck; everyone has a seat. That keeps the ship smooth and lag-free, and it's also how the best cruises work.

When you want to get off, press Shift. If there's land next to the ship, you're set down on the shore. If there isn't, you go in the water to the cry of *"Man overboard!"* Picking a sensible moment to disembark is left as an exercise for the passenger.

## The cargo

Below deck are **two cargo holds of 54 slots each**, 108 slots in total. That's enough for an expedition's worth of supplies, a mining trip's worth of loot, or a truly irresponsible amount of cobblestone. When you bottle the ship up, everything in the holds goes into the bottle with her. Your cargo travels in your pocket.

## The wind

Every dimension has its own wind, and it shifts slowly over time. Sailing with it gives you full speed. Sailing straight into it halves your speed. Long voyages become a small puzzle of reading the wind and choosing your heading, and taking the long way round is sometimes faster. There's **no fuel** to manage. The wind is free, and it's always blowing somewhere.

## Safety at sea

The ocean is dangerous, but not for your crew. **Nobody aboard can be hurt** while the ship is out on the water. Drowned, guardians and phantoms that come too close to the hull get zapped away. Your crew can take in the view instead of fighting off the local wildlife.

## Light on the server

The ship is built from about **50 display entities**, which are lightweight models that vanilla clients already know how to draw. **It never places a single block.** It won't carve holes in your terrain, leave ghost blocks behind or quietly eat your TPS. When you bottle it up, it's gone without a trace.

---

## 🍾 Crafting: Ship in a Bottle

| | | |
|---|---|---|
| Wool | Glass Bottle | Wool |
| Chest | Any Boat | Chest |
| Planks | Planks | Planks |

The wool becomes the sails, the chests become the cargo holds and the boat becomes the hull. It's shipbuilding by way of a crafting table.

---

## 🧭 How to sail

**Launching.** Hold the bottle and right-click open water. The ship needs about 7 × 20 blocks of it. The stern appears where you click and the bow points the way you're looking, so face the open sea, not the pier.

**Boarding.** Right-click the ship to climb aboard. If it's yours, you take the wheel.

**Sailing.** **W** and **S** raise and lower the sails, **A** and **D** turn the rudder, and **Space** rings the ship's bell for no reason other than joy.

**The ship's menu.** Right-click while you're aboard, or sneak + right-click from outside. From the menu you can open the cargo holds, switch seats, lock the ship so nobody else can board, ring the bell or bottle her back up.

**Putting her away.** Choose **Bottle it up** in the menu (owner only). The whole ship shrinks back into the bottle with her name and cargo inside, ready for the next voyage.

**Good to know.** The ship only sails on water. If she meets land or blocks, she stops. If nobody is at the wheel, she drifts slowly to a stop and waits for her captain.

---

## 🛠️ Commands

- `/ahoy`: a quick how-to guide in chat
- `/ahoy give`: gives you a Ship in a Bottle (ops only)

---

## 📦 Requirements

- Minecraft **26.3**
- **Fabric Loader** and **Fabric API**
- Java 25

Install Ahoy on the server and you're done. Players don't need to install anything. It also works in singleplayer.

---

## 🧪 Tested on a real server

Every build of Ahoy is put to sea before it's released. An automated test starts a real dedicated server, launches a ship in a test harbour and puts her through her paces. She sails and turns, then rams the harbour wall to prove she stops. Finally she's bottled up, and the test checks that her name and cargo survived and that nothing was left behind in the water.

It's probably the only shipyard in history that crashes every ship on purpose and calls it quality control.

---

Source code and issue tracker: [github.com/ThatCoffeelock/Market](https://github.com/ThatCoffeelock/Market)

Fair winds, captain. ⚓
