# Builds lib-pack/tutorial/LIB-Pack-Tutorial.pdf: a cover page plus one short tutorial page per mod.
# Run: python3 build.py  (needs Chromium; set CHROME to its path if it isn't on PATH)
import html, os, subprocess, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
VERSION = "1.11.0"

# Ingredient: (label in the slot, swatch colour, full name, dark text?)
I = {
 "gold": ("Au", "#e8b923", "Gold Ingot", True), "obsidian": ("Obs", "#2a1b3d", "Obsidian", False), "emerald": ("Em", "#17b85c", "Emerald", False),
 "wool": ("Wool", "#e9e9e9", "Any Wool", True), "bottle": ("Btl", "#9fd3e6", "Glass Bottle", True), "chest": ("Chst", "#a8742f", "Chest", False),
 "boat": ("Boat", "#9b7340", "Any Boat", False), "planks": ("Plk", "#b8925a", "Any Planks", False), "string": ("Str", "#d9d9d9", "String", True),
 "leather": ("Lea", "#8b4f2b", "Leather", False), "ironblock": ("FeB", "#c9c9c9", "Block of Iron", True), "log": ("Log", "#6b4f2a", "Any Log", False),
 "dispenser": ("Disp", "#6f6f6f", "Dispenser", False), "iron": ("Fe", "#bdbdbd", "Iron Ingot", True), "gunpowder": ("Gun", "#5a5a5a", "Gunpowder", False),
 "fminecart": ("FMc", "#585858", "Minecart with Furnace", False), "cminecart": ("CMc", "#8a6a3a", "Minecart with Chest", False),
 "redstoneblock": ("RsB", "#b51d12", "Block of Redstone", False), "rail": ("Rail", "#7d6a52", "Rail", False), "barrel": ("Brl", "#8d6236", "Barrel", False),
 "hay": ("Hay", "#c9a82b", "Hay Bale", True), "glass": ("Gls", "#bfe3ee", "Glass", True), "minecart": ("Mc", "#7b7b7b", "Minecart", False),
 "furnace": ("Frn", "#6a6a6a", "Furnace", False), "flint": ("Flt", "#3b3b40", "Flint", False), "copper": ("Cu", "#c86f4a", "Copper Ingot", False),
 "paper": ("Ppr", "#f2efe6", "Paper", True), "workbench": ("Wbn", "#9c6b3a", "Crafting Table", False), "nugget": ("Nug", "#d6d6d6", "Iron Nugget", True), "gravel": ("Grv", "#8a8580", "Gravel", False),
 "book": ("Book", "#7a3b2a", "Book", False), "carto": ("Map", "#7b5a3a", "Cartography Table", False), "lantern": ("Lntn", "#e0a33a", "Lantern", True),
 "lead": ("Lead", "#a58a5e", "Lead", False),
 "diamondblock": ("DiB", "#5fd8cf", "Block of Diamond", True), "piston": ("Pstn", "#9a7d4f", "Piston", False),
 "blastfurnace": ("BlF", "#4e4e55", "Blast Furnace", False), "cauldron": ("Cldn", "#3f3f44", "Cauldron", False),
 "bricks": ("Brk", "#a5533d", "Bricks", False), "stick": ("Stk", "#8a6a3a", "Stick", False),
}

def shaped(name, rows, key, out, count=None): return dict(name=name, rows=rows, key=key, out=out, count=count)
def shapeless(name, items, out, count=None): return dict(name=name, items=items, out=out, count=count)

MODS = [
 dict(name="Market", colour="#c8961e", tag="Sell anything for Marks (₥), buy it back at a markup, and flex with pallets of cash.",
  recipes=[shaped("Market", ["GOG","OEO","GOG"], dict(G="gold",O="obsidian",E="emerald"), "Mkt")],
  steps=["Craft a <b>Market</b> and place it. It looks like a glowing lectern.",
         "Right-click it and choose <b>Sell Items</b>. Drop your goods in the grid (or click <b>Add My Inventory</b>), then <b>Confirm Sale</b>.",
         "Check your money with <code>/balance</code>. Send some to a friend with <code>/pay &lt;player&gt; &lt;amount&gt;</code>.",
         "Choose <b>Buy Items</b> to shop by category. Left-click buys 1, right-click 8, shift-click a stack.",
         "Rich? <b>Luxury &amp; Vanity</b> sells cash stacks, gold bars and the Tycoon Trophy. They do nothing. That's the point."],
  tips=["<code>/worth</code> tells you what the item in your hand sells for.",
        "Buying costs at least twice the sell price, and rarer items cost more.",
        "<code>/withdraw &lt;amount&gt;</code> turns money into a banknote you can trade. Right-click a note to deposit it.",
        "Damaged tools sell for less; enchantments add value. Full shulker boxes are refused."],
  cmds="/balance · /pay · /baltop · /withdraw · /worth"),
 dict(name="Colonycraft", colour="#b5523b", tag="Found colonies, buy prefab buildings with Marks and let villagers gather for you.",
  recipes=[], getit="No recipe: buy a <b>Colony Charter</b> for ₥2,500 with <code>/colonycraft charter</code>.",
  steps=["Rename the charter in an anvil to name your colony, then right-click the ground. A <b>Town Hall</b> is built and claims the land around it.",
         "Right-click the Town Hall's <b>lectern</b> to shop for blueprints: Residence (beds), Farm, Lumber Camp, Mine, Workshop, Storehouse.",
         "Right-click inside your colony with a blueprint to see the outline, then click the same spot again to build it.",
         "Every morning you pay wages (₥3 a worker) and the workers deliver to your Storehouses. Can't pay? Everyone strikes.",
         "Add defences: walls, gatehouses, watchtowers with archers and a barracks with iron golems."],
  tips=["Every worker needs a bed, so build Residences first.",
        "Turn on <b>autosell</b> at a Storehouse to sell its goods to the Market automatically.",
        "<b>Law and order:</b> buy Shackles (₥50), beat an illager under 40% health and shackle it. Jail it in a <b>Cellblock</b>, then ransom it or execute it on the <b>Scaffold</b> for 2–3× the bounty.",
        "Needs the Market mod (it's in the pack)."],
  cmds="/colonycraft · /colonycraft charter"),
 dict(name="Ahoy", colour="#3e8fc7", tag="A two-masted sailing ship in a bottle: captain + 8 passengers, two 54-slot holds and wind.",
  recipes=[shaped("Ship in a Bottle", ["WGW","CBC","PPP"], dict(W="wool",G="bottle",C="chest",B="boat",P="planks"), "Ship")],
  steps=["Optional: rename the bottle in an anvil. That's your ship's name, painted on the stern.",
         "Right-click <b>open water</b> (about 7 × 20 blocks). The stern starts where you click; the bow points where you look.",
         "Right-click the ship to board. The owner takes the wheel; friends get the benches.",
         "Sail with <b>W/S</b>, steer with <b>A/D</b>, ring the bell with <b>Space</b>. <b>Shift</b> to get off.",
         "Menu → <b>Shipwright</b>: refits for speed (3 levels, up to +60%), cargo (holds C and D, up to 216 slots) and a <b>canal drill</b>. Switch the drill on in the ship menu and the ship cuts a water-filled canal through land. It never cuts chests or bedrock, but it does cut houses!",
         "Menu → <b>Gun deck</b>: slot Cannons into the four gun ports, then man one from the same menu.",
         "Menu → <b>Bunks</b>: slot a bed into one of the two berths on the foredeck, then lie down at night. Sleep while anchored; Shift gets you up.",
         "Done sailing? Open the menu and choose <b>Bottle it up</b>. Name, cargo and cannons stay inside."],
  tips=["Sailing with the wind is full speed, against it half speed.",
        "Nobody aboard can be hurt, and drowned, guardians and phantoms get zapped.",
        "Passengers can fish, shoot, throw, eat and use blocks and mobs in reach: right-click does what it does ashore.",
        "Menu: right-click with an empty hand while aboard (or <code>/ahoy menu</code>), or sneak + right-click from outside. It has the cargo holds and the lock.",
        "Moor near a Warehouse <b>Loading Dock</b> to unload in one click."],
  cmds="/ahoy · /ahoy menu"),
 dict(name="Burlap Sack", colour="#a07a45", tag="Bag villagers and wandering traders and let them out where you want them.",
  recipes=[shaped("Burlap Sack", [" S ","L L","LLL"], dict(S="string",L="leather"), "Sack")],
  steps=["Hold the sack in your main hand and right-click a villager or wandering trader.",
         "Carry them home. The sack's name tells you who's inside (\"Bob, a level 3 farmer\").",
         "Right-click a block to let them out. They climb out facing you, trades and levels intact.",
         "Give a new villager without trades a workstation nearby, or they'll drop their job."],
  tips=["Iron golems within 24 blocks will come for you when you bag someone.",
        "Captives charge you a bit more for a few days. The grudge fades.",
        "Wandering traders you move never despawn.",
        "One sack holds one captive. Drop it in lava and they're gone."],
  cmds="/burlapsack"),
 dict(name="Cannon", colour="#5c5c66", tag="An aimable cannon you build, place and man. Blasts mobs, never your base.",
  recipes=[shaped("Cannon", ["III","LDL","L L"], dict(I="ironblock",L="log",D="dispenser"), "Cnn"),
           shapeless("Cannonball", ["iron","gunpowder"], "Ball", 2)],
  steps=["Right-click the ground with the cannon. It points where you look.",
         "Right-click the cannon to man it. Keep cannonballs in your inventory.",
         "Look around to aim; the action bar shows elevation, range and ammo.",
         "Press <b>Space</b> to fire. Reloading takes 2 seconds. <b>Shift</b> to get off."],
  tips=["At 45° a ball flies about 80 blocks.",
        "The blast is creeper-sized and hurts mobs and players, but never breaks blocks.",
        "Friendly fire is on. Don't shoot the wall in front of you.",
        "Sneak + right-click to pick the cannon up again (owner only)."],
  cmds="/cannon"),
 dict(name="Cargo Train", colour="#d9b51f", tag="A self-driving locomotive with up to 4 wagons that shuttles between station chests.",
  recipes=[shapeless("Cargo Train", ["fminecart","cminecart","redstoneblock"], "Trn"),
           shapeless("Cargo Wagon", ["cminecart","iron"], "Wgn"),
           shapeless("Pickup Station", ["chest","rail"], "Stn")],
  steps=["Lay a line of rails (at least 4 straight where the train goes down).",
         "Put a <b>Pickup Station</b> chest next to the track at one end and a <b>Drop-off Station</b> at the other. Sneak + right-click a station (empty hand) to switch its mode.",
         "Right-click a rail with the Cargo Train. It drives to the end, turns around and keeps going.",
         "It stops at every station for 3 seconds to load or unload."],
  tips=["You can also rename any chest or barrel: \"Pickup Station\", \"Drop-off Station\" or \"Swap Station\".",
        "Right-click a standing train with a <b>Cargo Wagon</b> to add one (up to 4).",
        "A running train keeps its chunks loaded, so it works while you're away.",
        "Give each train its own line. Two trains drive straight through each other."],
  cmds="/train"),
 dict(name="Flintlock", colour="#8a5a3c", tag="Black-powder pistols, muskets and blunderbusses. Hit hard, reload slowly.",
  recipes=[shaped("Flintlock Pistol", ["IF ", " P ", "   "], dict(I="iron",F="flint",P="planks"), "Pstl"),
           shaped("Musket", ["I  ", " I ", " FP"], dict(I="iron",F="flint",P="planks"), "Msk"),
           shaped("Blunderbuss", ["C  ", " C ", " FP"], dict(C="copper",F="flint",P="planks"), "Blnd"),
           shapeless("Paper Cartridge", ["paper","gunpowder","nugget"], "Crt", 4),
           shapeless("Scattershot", ["paper","gunpowder","flint","gravel"], "Sct", 2)],
  steps=["Pistol and musket shoot <b>Paper Cartridges</b>; the blunderbuss shoots <b>Scattershot</b>.",
         "Right-click with an empty gun and <b>keep holding it</b> until the reload bar fills (pistol 2 s, blunderbuss 3 s, musket 4 s).",
         "Right-click to fire."],
  tips=["Musket: 18 damage, about 100 blocks. Pistol: 9. Blunderbuss: up to 20 point-blank, and it knocks you back.",
        "A gun stays loaded until fired: load four pistols before a fight, like a proper pirate.",
        "Guns wear out (one point per shot). Repair in an anvil with iron (copper for the blunderbuss). Mending works.",
        "Shields block shots.",
        "Enchant guns at a table or anvil: Power (+15% damage a level), Punch, Flame, Quick Charge, Piercing, Multishot, Infinity."],
  cmds="/flintlock"),
 dict(name="Havana", colour="#7a4a24", tag="Grow tobacco, cure it in a barrel, roll cigars and smoke them.",
  recipes=[shapeless("Curing Barrel", ["barrel","hay"], "Cure")],
  steps=["Break grass and ferns. About 1 in 12 drops <b>Tobacco Seeds</b>. Plant them on farmland.",
         "When ripe, the plant shoots up 2 blocks. Break it for 3–5 <b>Tobacco Leaves</b>.",
         "Put the leaves in a <b>Curing Barrel</b>. After one Minecraft day they're <b>Cured Tobacco</b>; two more days makes <b>Aged Tobacco</b>.",
         "Hold tobacco and right-click a crafting table: <b>3 tobacco = 1 cigar</b>. Add honey, cocoa, berries, glow berries or blaze powder for a flavour.",
         "Light it with flint and steel in your other hand (or a campfire or torch), then right-click to puff. 8 puffs per cigar."],
  tips=["Cigars give Regeneration; the Gran Reserva (aged) adds Resistance and Hero of the Village.",
        "Flavours: honey → Absorption, cocoa → Haste, berries → Speed, glow → Night Vision, blaze → Fire Resistance.",
        "Puff three times fast and you cough. Lit cigars go out underwater.",
        "Any barrel or chest named \"Curing\" or \"Humidor\" works too, and it keeps curing while you're away."],
  cmds="/havana"),
 dict(name="Mobile Home", colour="#3aa7a0", tag="Drivable camper vans and tanks with 54 slots, a workbench and a monster force field.",
  recipes=[shaped("Camper Van", ["GGG","ICI","MFM"], dict(G="glass",I="ironblock",C="chest",M="minecart",F="furnace"), "Van"),
           shaped("Tank", ["IDI","OCO","MFM"], dict(I="ironblock",D="dispenser",O="obsidian",C="chest",M="minecart",F="furnace"), "Tank")],
  steps=["Right-click the ground with the vehicle to park it.",
         "Refuel: sneak + right-click it holding fuel. Anything a furnace burns works.",
         "Right-click to get in. Drive with <b>W/S</b>, steer with <b>A/D</b>, <b>Ctrl</b> for turbo, <b>Space</b> to honk, <b>Shift</b> to get out.",
         "Menu: right-click while seated. Storage, workbench, ender stash, lock and <b>Pack up</b>."],
  tips=["One coal is about 2½ minutes of van driving; a lava bucket about half an hour.",
        "The tank turns on the spot and climbs 2-block walls, but burns twice the fuel.",
        "While seated nothing hurts you and monsters get zapped away.",
        "It floats. Nobody planned that."],
  cmds="/mobilehome"),
 dict(name="Warehouse", colour="#c07a2c", tag="Big, sorted storage: tens of thousands of items, fed by hand, hoppers, trains and ships.",
  recipes=[shaped("Warehouse Core", ["IBI","CTC","ICI"], dict(I="iron",B="book",C="chest",T="carto"), "Core"),
           shapeless("Storage Rack", ["barrel","chest","iron","iron"], "Rack"),
           shapeless("Loading Dock", ["lantern","chest","lead"], "Dock")],
  steps=["Place a <b>Warehouse Core</b> (rename it first to name the warehouse). It holds 2,048 items.",
         "Place <b>Storage Racks</b> against it, and more against those (sneak to place a rack on a rack). Each adds 4,096.",
         "Right-click the core or a rack to open it. Tabs, sorting and search are on the bottom row.",
         "Deposit: drop items on the stock, shift-click from your inventory, or click <b>Deposit your backpack</b>."],
  tips=["Hoppers into a rack, a chest named <b>Warehouse Intake</b>, or a train's Drop-off Station touching it all fill it automatically.",
        "A <b>Loading Dock</b> on your pier unloads Ahoy ships into nearby warehouses.",
        "A Fossil Fool <b>Drill Rig</b> within 16 blocks of the core unloads its ore and stone onto the shelves by itself.",
        "Make specialist warehouses (food only, ores only) in Settings.",
        "Break the core to pack it up. The stock stays inside."],
  cmds="/warehouse · /warehouse list"),
 dict(name="Skills", colour="#6a4fb3", tag="Elder Scrolls style skills: you get better at things by doing them.",
  recipes=[], getit="No recipe: just play. Type <code>/skills</code> to open the menu.",
  steps=["Do things. Mining levels Mining, fighting levels Combat, sailing levels Sailing, and so on. 14 skills, levels 0–100.",
         "Every level gives a small passive bonus. Level 100 is about twice as good as a beginner.",
         "Every 10 levels you earn a <b>perk point</b> for that skill. Open <code>/skills</code>, click the skill, click a perk.",
         "Each skill has 3 perks × 5 ranks but only 10 points, so choose."],
  tips=["Vein Miner and Timber only work while you <b>sneak</b>.",
        "Blocks you placed yourself give no XP. No cheesing.",
        "Changed your mind? <b>Forget perks</b> on a skill page (costs 5 XP levels).",
        "<code>/skills top &lt;skill&gt;</code> shows the leaderboard. Mercantile raises your Market prices.",
        "<b>Wildcatting</b> levels from Fossil Fool's drilling, pumping and refining, even while you're offline: better fuel use, faster rigs, bonus diesel."],
  cmds="/skills · /skills top"),
 dict(name="Hamlets & Horrors", colour="#4f6b3a", tag="Random cottages, castles and dungeons. Some lived in by villagers, some overrun by monsters.",
  recipes=[], getit="Nothing to craft: they generate in <b>new, unexplored terrain</b>.",
  steps=["Travel somewhere you've never been. Structures only appear in chunks generated after the mod was installed.",
         "<b>Cottages</b> and <b>castles</b> with villagers are safe: no monsters spawn inside.",
         "<b>Haunted cottages</b> and <b>ruined castles</b> are full of monsters, and bandits camp in the ruins. Bring armour.",
         "<b>Dungeons</b> start at a crypt on the surface; a spiral stair leads 16 blocks down to a prison, a crypt, a treasury and a library."],
  tips=["Press the stone button in front of a cell door to free the prisoners.",
        "Haunted cottages have a zombie villager you can cure.",
        "Cottages appear about every 20 chunks, dungeons every 28, castles every 44.",
        "Ops can find one with <code>/locate structure hamlets:castle</code> (or cottage, dungeon…)."],
  cmds="/hamlets"),
 dict(name="Overenchant", colour="#8a4fc7", tag="Raises the maximum level of every enchantment to X: Sharpness X instead of V, Protection X instead of IV.",
  recipes=[], getit="No recipe: it works on every enchanting table, anvil and book. Type <code>/overenchant</code> to see what went up.",
  steps=["Enchant as usual. Every enchantment with more than one level can now reach X: Sharpness V becomes X, Protection IV becomes X, Knockback II becomes X.",
         "The enchanting table offers the new levels. Sharpness X costs what V used to, so a level 30 table with bookshelves hands out VIII to X.",
         "To climb by hand, put two books (or items) with the same enchantment at the same level in an anvil. Two Sharpness IX give Sharpness X.",
         "Mending, Silk Touch, Infinity and other one-level enchantments stay at I.",
         "Flintlock guns can be enchanted too, so Power X, Quick Charge X and Piercing X are real."],
  tips=["The anvil still says \"Too Expensive!\" at 40 levels, so plan your books.",
        "Ops: <code>/enchant @s minecraft:sharpness 10</code> works now.",
        "Enchanted items you already have keep their levels, and can now be raised.",
        "Admins can change the levels in <code>config/overenchant.json</code>. Keep them at X or lower unless everyone has the mod on their client."],
  cmds="/overenchant"),
 dict(name="Fuck Illagers", colour="#7a1f1f", tag="Bounty hunting: illagers drop fingers, a Bounty Station buys them and posts contracts on named illager bosses.",
  recipes=[shaped("Bounty Station", ["PCP","CTC","PCP"], dict(P="paper",C="copper",T="workbench"), "Bnty")],
  steps=["Craft a <b>Bounty Station</b> (paper, copper, a crafting table) and place it. Right-click it to open it.",
         "Kill illagers. Each one a player kills drops an <b>Illager Finger</b> (pillagers and vindicators 1, evokers 2). The station pays ₥3 each: <b>Sell trophies</b>.",
         "Take a contract: <b>Easy</b> (a wagon or watchtower, ₥150), <b>Medium</b> (a war camp or fortress, ₥400) or <b>Hard</b> (a dungeon or castle, ₥1,000). You get coordinates and a <b>Wanted Poster</b>.",
         "Ride out. The target hides 1000–2000 blocks away. Hold the poster to see how far and which way. The hideout is built as you get close: guards, loot chests, sometimes a prisoner in a cage.",
         "Kill the named boss and pick up its <b>skull</b>. Sell it at any Bounty Station for the reward."],
  tips=["One contract at a time. Abandon it at the station if it's too much (no reward).",
        "Bosses are tougher than normal: 40 health on Easy, 80 on Medium, 140 on Hard. Hard bosses are evokers. Bring a shield for the vexes.",
        "The dungeon is 16 blocks down a ladder. The castle's boss waits upstairs in the keep, past a ravager.",
        "Raid farms count as illagers too. Your fingers, your call.",
        "Ops: <code>/bounty build castle</code> builds a hideout where you stand (for testing, no skull)."],
  cmds="/bounty"),
 dict(name="Fossil Fool", colour="#3a3a3a", tag="Old-timey oil: dowse for it, sink a 5×5 shaft with a Drill Rig, strike crude, tank it, refine it into diesel and burn it.",
  recipes=[shaped("Drill Rig", ["BDB","PFP","LLL"], dict(B="ironblock",D="diamondblock",P="piston",F="blastfurnace",L="log"), "Rig"),
           shaped("Oil Tank", ["I I","IUI","III"], dict(I="iron",U="cauldron"), "Tank"),
           shaped("Refinery", ["CUC","CFC","SSS"], dict(C="copper",U="cauldron",F="blastfurnace",S="bricks"), "Ref"),
           shaped("Dowsing Rod", ["S S"," S "," G "], dict(S="stick",G="gold"), "Rod")],
  steps=["Right-click with the <b>Dowsing Rod</b>. It twitches towards the nearest oil pocket: which way, how far, how deep. Walk until it points straight down.",
         "Right-click the ground with the <b>Drill Rig</b>. It sinks a <b>5×5 shaft</b> centred on that block, one block at a time, all the way to bedrock.",
         "Right-click the derrick (anyone can) and put fuel in the <b>firebox</b> (row 2). Ores land in rows 3–4, stone in rows 5–6.",
         "When it hits a pocket: <b>STRUCK OIL!</b> It pumps the pocket dry into its tank and into any <b>Oil Tank</b> within 9 blocks, then keeps drilling.",
         "Put a <b>Refinery</b> next to the tank. With fuel in its firebox, it turns 2 crude into 1 diesel and pipes the diesel back into an empty tank."],
  tips=["Fuel ladder, each better than the last: <b>coal</b> 2 blocks (0.5× speed) → <b>lava</b> 40 (0.75×) → <b>crude</b> 60 (1×) → <b>diesel</b> 200 (1.5×). A full shaft is about 16 buckets of diesel.",
        "<b>Hoppers</b> around the shaft (on the ground or one up) get the holds: ores first, then stone. Lead them into a Cargo Train <b>Pickup Station</b>.",
        "Broke into oil by hand? Right-click the black crude with an empty bucket. Mine it and it oozes away.",
        "Sell crude (₥25) and diesel (₥45) at the Market. Only the owner can pack a rig up. About one pocket in ten is a gusher."],
  cmds="/fossilfool"),
]

def item(k, big=False):
    lab, col, name, dark = I[k]
    style = f"background:{col};" + ("color:#1d1d1f;text-shadow:none;" if dark else "")
    return f'<span class="it" style="{style}" title="{html.escape(name)}">{lab}</span>'

def recipe(r):
    used = []
    if "rows" in r:
        cells = []
        for row in r["rows"]:
            for c in row:
                k = r["key"].get(c)
                if k: used.append(k)
                cells.append(f'<span class="sl">{item(k) if k else ""}</span>')
        grid = f'<div class="grid">{"".join(cells)}</div>'
        kind = ""
    else:
        used = list(r["items"])
        grid = f'<div class="grid row" style="grid-template-columns:repeat({len(used)},26px)">' + "".join(f'<span class="sl">{item(k)}</span>' for k in used) + "</div>"
        kind = '<span class="any">any shape</span>'
    count = f'<b class="cnt">{r["count"]}</b>' if r["count"] else ""
    legend = " · ".join(f"<b>{I[k][0]}</b> {I[k][2]}" for k in dict.fromkeys(used))
    return (f'<div class="rc"><div class="rn">{html.escape(r["name"])} {kind}</div>'
            f'<div class="cr">{grid}<span class="ar">→</span><span class="sl out"><span class="it res">{r["out"]}</span>{count}</span></div>'
            f'<div class="lg">{legend}</div></div>')

def page(m, n):
    rec = "".join(recipe(r) for r in m["recipes"]) if m["recipes"] else f'<p class="getit">{m["getit"]}</p>'
    steps = "".join(f"<li><span>{s}</span></li>" for s in m["steps"])
    tips = "".join(f"<li>{t}</li>" for t in m["tips"])
    return f'''<section class="mod" style="--c:{m["colour"]}">
  <header><span class="num">{n:02d}</span><h2>{html.escape(m["name"])}</h2></header>
  <p class="tag">{html.escape(m["tag"])}</p>
  <h3>What you need</h3><div class="recipes">{rec}</div>
  <h3>Get started</h3><ol class="steps">{steps}</ol>
  <h3>Good to know</h3><ul class="tips">{tips}</ul>
  <p class="cmds">Commands: <code>{m["cmds"]}</code></p>
</section>'''

toc = "".join(f'<li><span>{html.escape(m["name"])}</span><span class="dots"></span><span>{i + 2}</span></li>' for i, m in enumerate(MODS))

doc = f'''<!doctype html><html><head><meta charset="utf-8"><title>LIB Pack Tutorial</title><style>
@page {{ size: A4; margin: 16mm 16mm 14mm; }}
* {{ box-sizing: border-box; }}
body {{ font: 10.5pt/1.45 "DejaVu Sans", sans-serif; color: #1f1e1c; margin: 0; }}
code {{ font-family: "DejaVu Sans Mono", monospace; font-size: .88em; background: #ece9e2; padding: 0 3px; border-radius: 2px; }}
h1, h2, h3 {{ margin: 0; }}
.cover {{ page-break-after: always; display: flex; flex-direction: column; gap: 18px; }}
.cover .eyebrow {{ font: 9pt "DejaVu Sans Mono", monospace; letter-spacing: .12em; text-transform: uppercase; color: #6b675f; }}
.cover h1 {{ font: bold 54pt/1 "DejaVu Sans Mono", monospace; letter-spacing: -.02em; }}
.cover h1 span {{ color: #1b7f4b; }}
.cover .lede {{ font-size: 13pt; max-width: 34em; }}
.box {{ border: 2.5px solid #1d1d1f; background: #e3e1dc; box-shadow: inset 2px 2px 0 #fff, inset -2px -2px 0 #8b8b8b; padding: 12px 16px; }}
.box h2 {{ font: bold 13pt "DejaVu Sans Mono", monospace; margin-bottom: 6px; }}
.box ol {{ margin: 0; padding-left: 1.3em; }}
.toc {{ list-style: none; padding: 0; margin: 0; columns: 2; column-gap: 28px; }}
.toc li {{ display: flex; gap: 6px; padding: 3px 0; break-inside: avoid; }}
.toc .dots {{ flex: 1; border-bottom: 1px dotted #9a968d; margin-bottom: 4px; }}
.mod {{ page-break-after: always; display: flex; flex-direction: column; gap: 7px; }}
.mod:last-child {{ page-break-after: auto; }}
.mod header {{ display: flex; align-items: baseline; gap: 12px; border-bottom: 4px solid var(--c); padding-bottom: 5px; }}
.mod .num {{ font: bold 13pt "DejaVu Sans Mono", monospace; color: var(--c); }}
.mod h2 {{ font: bold 25pt/1.1 "DejaVu Sans Mono", monospace; }}
.tag {{ font-size: 11.5pt; color: #3d3a35; margin: 0; }}
h3 {{ font: bold 9pt "DejaVu Sans Mono", monospace; letter-spacing: .1em; text-transform: uppercase; color: #6b675f; margin-top: 6px; }}
.recipes {{ display: flex; flex-wrap: wrap; gap: 10px 22px; }}
.rc {{ display: flex; flex-direction: column; gap: 3px; }}
.rn {{ font: bold 9.5pt "DejaVu Sans Mono", monospace; }}
.any {{ font-weight: normal; color: #6b675f; font-size: 8.5pt; }}
.cr {{ display: flex; align-items: center; gap: 7px; }}
.grid {{ display: grid; grid-template-columns: repeat(3, 26px); gap: 2px; }}
.sl {{ width: 26px; height: 26px; background: #8b8b8b; box-shadow: inset 1.5px 1.5px 0 #373737, inset -1.5px -1.5px 0 #fff; display: grid; place-items: center; position: relative; }}
.it {{ width: 20px; height: 20px; display: grid; place-items: center; font: bold 6.5pt/1 "DejaVu Sans Mono", monospace; color: #fff; text-shadow: .5px .5px 0 #000; border: 1.5px solid rgba(0,0,0,.35); }}
.out {{ width: 32px; height: 32px; }}
.it.res {{ width: 26px; height: 26px; background: var(--c); box-shadow: 0 0 0 1.5px #b77cff; font-size: 7pt; }}
.cnt {{ position: absolute; right: -3px; bottom: -5px; font: bold 9pt "DejaVu Sans Mono", monospace; color: #fff; text-shadow: 1px 1px 0 #333; }}
.ar {{ font: bold 13pt "DejaVu Sans Mono", monospace; color: #6b675f; }}
.lg {{ font-size: 7.5pt; color: #6b675f; max-width: 25em; }}
.lg b {{ color: #1f1e1c; font-family: "DejaVu Sans Mono", monospace; }}
.getit {{ margin: 0; padding: 8px 12px; border-left: 4px solid var(--c); background: #f1efea; }}
.steps {{ margin: 0; padding-left: 0; list-style: none; counter-reset: s; display: flex; flex-direction: column; gap: 5px; }}
.steps li {{ counter-increment: s; display: grid; grid-template-columns: 24px 1fr; gap: 8px; }}
.steps li::before {{ content: counter(s); font: bold 10pt/20px "DejaVu Sans Mono", monospace; text-align: center; width: 20px; height: 20px; background: var(--c); color: #fff; }}
.tips {{ margin: 0; padding-left: 1.1em; display: flex; flex-direction: column; gap: 3px; }}
.tips li::marker {{ color: var(--c); }}
.cmds {{ margin: 6px 0 0; font-size: 9.5pt; color: #3d3a35; }}
</style></head><body>
<section class="cover">
  <p class="eyebrow">Minecraft 26.3 · Fabric · server-side · version {VERSION}</p>
  <h1>LIB <span>Pack</span></h1>
  <p class="lede">Fifteen mods in one jar, with a one-page tutorial for each. Players join with a plain vanilla client: everything is built from vanilla items, blocks and chest screens.</p>
  <div class="box"><h2>For server owners</h2><ol>
    <li>Install Fabric Loader for Minecraft 26.3.</li>
    <li>Put <b>Fabric API</b> and <code>lib-pack-{VERSION}+mc26.3.jar</code> in the server's <code>mods/</code> folder.</li>
    <li>Remove any separate jars of these mods. Worlds, balances and colonies carry over.</li></ol></div>
  <div class="box"><h2>For players</h2><ol>
    <li>Recipes unlock in your normal recipe book once you pick up an ingredient.</li>
    <li>Every mod has a help command, e.g. <code>/cannon</code> or <code>/skills</code>.</li>
    <li>Custom items look like vanilla items with a glint; their name tells you what they are.</li></ol></div>
  <h2 style="font: bold 13pt 'DejaVu Sans Mono', monospace">Contents</h2>
  <ul class="toc">{toc}</ul>
</section>
{"".join(page(m, i + 1) for i, m in enumerate(MODS))}
</body></html>'''

src = os.path.join(HERE, "tutorial.html")
with open(src, "w") as f: f.write(doc)
chrome = os.environ.get("CHROME") or shutil.which("chromium") or shutil.which("google-chrome") or "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
out = os.path.join(HERE, "LIB-Pack-Tutorial.pdf")
subprocess.run([chrome, "--headless", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
                f"--print-to-pdf={out}", "file://" + src], check=True, stderr=subprocess.DEVNULL)
print("wrote", out)
