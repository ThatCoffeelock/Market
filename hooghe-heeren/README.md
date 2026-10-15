# De Hooghe Heeren

A Fabric mod for Minecraft **26.3** that gives your [De Compagnie](../compagnie/README.md) **rivals**: three to five houses of lords and merchant princes, each with a name and a face, a seat out in the world, a treasury, regiments and a long memory. Trade with them, sign pacts, feast them in your hall, spy on them, rob them, set them against each other, or go to war and ride out at noon to lead your regiments in the field.

They play by **the same rules you do**: their income pays their regiments, and **no treasury means no army**.

- **The houses**: randomized heads of house (mostly Dutch patricians, with the odd Englishman, Spaniard, Frenchman or Dane), each with a **temperament** and a **quirk**. Houses fall (old age, bankruptcy, your assassin) and new ones take their seat.
- **Opinion**: what each house thinks of you, from Blood Feud to Sworn Ally, built from what they **remember** (and memories fade; betrayals don't). The houses think about each other, too, and trade and fight without you.
- **Honour and Fear**: your standing at court. Honour wins you pacts; Fear makes demands stick. Play the Benevolent Diplomat, the Iron Ruler, the Cutthroat... or end up the Snake.
- **Diplomacy**: gifts, trade pacts, peace pacts, alliances, tribute, war and peace.
- **Errands**: spy, sabotage, bandits, burglary, bribery, forged letters and assassination, run by your **Compagnie agents**.
- **War**: abstract regiments that skirmish every dawn, and **pitched battles at noon you can ride out to and fight in** (Bannerlord style: you tip the odds).
- **In person**: ride to a rival's **seat** (built in the world the first time you get there) and talk to them in their great hall, or hang an **Audience Bell** in your own hall and invite them over. Lay on a feast. Or bar the doors.

It's **server-side only**, single player or on a server. Friends join with a plain vanilla client: the lords, guests, guards and soldiers are vanilla **mannequins**, the menus are chest screens. It needs the **Market** mod (Marks are the money) and **De Compagnie** (1.2.0 or newer).

## Install

Put **Fabric API**, **`market-<version>.jar`**, **`compagnie-<version>.jar`** and **`hooghe-heeren-<version>.jar`** in your `mods/` folder. All three are on the [hooghe-heeren-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/hooghe-heeren-latest). It's in the [LIB Pack](../lib-pack/README.md) too. Or build it yourself: `./gradlew build` in the repo root, then in `compagnie/`, then in `hooghe-heeren/` (Java 25).

## The court

Open your **Company Desk** and click the **banner tab** in the top-right corner. That's your seat among the Hooghe Heeren.

| Tab | What's there |
|---|---|
| **The Court** | Your Honour, Fear and reputation, the chronicle of what happened, your hall, a battle if one's coming |
| **The Houses** | The rival houses. Click one for their **dossier** |
| **Dispatches** | Letters that want an answer: offers, demands, caught spies, calls to arms, peace terms |
| **Errands** | Your agents' covert errands under way |
| **Your Army** | Your regiments, the wars you're in |

Everything runs on De Compagnie's clock: at every **dawn** (a Minecraft day) each house collects its income, pays its regiments, does the **one thing** it planned the day before, and plans the next. What happened goes in the chronicle and the Compagnie's news.

## The houses

Each house has the same numbers you do:

| | |
|---|---|
| **Treasury** | Their Marks. Your treasury is your Market balance |
| **Income** | Marks a day. Trade pacts raise it, sabotage cuts it |
| **Regiments** | ₥500 to raise, **₥25 a day** to keep. Can't pay? The unpaid ones **desert**. Same for you |

At first you only hear rumours ("*a fat treasury and a few regiments*"). **Spy** on them, or meet them in person, to see their real numbers, their temperament, their quirk and **what they plan to do next**. Some plans leak out anyway: "*Rumour has it Van Houten is buying muskets.*"

| Temperament | They... |
|---|---|
| **Merchant Prince** | love trade pacts and partners who keep their word; hate wars that cost money |
| **Warlord** | respect strength and nothing else: raise regiments, raid, demand tribute, declare war |
| **Schemer** | trust nobody: spy, sabotage, whisper lies about you to the other houses |
| **Miser** | hoard gold, love gifts of money, hate paying for an army |
| **Zealot** | keep every pact and never forgive a broken one; frown on drink |
| **Spendthrift** | throw money around: feasts, gifts, regiments. Easy to like, easy to bankrupt |

| Quirk | |
|---|---|
| Drunkard | Spirits at a feast count double |
| Pious | Spirits at a feast count against you |
| Vain | Gifts given in person count double |
| Greedy | Gifts of Marks count half again |
| Paranoid | Hard to spy on (-10% on errands against them), slow to accept an invitation |
| Proud | Never pays tribute |
| Cowardly | Sues for peace early, pays up when threatened |
| Old | Won't see many more winters. An heir is waiting |

### Opinion

What a house thinks of you, -100 to +100: **Blood Feud**, Hostile, Cold, Neutral, Cordial, Friendly, **Sworn Ally**. It's their temperament's take on your Honour and Fear, plus pacts (+10 trade, +10 peace, +20 alliance), minus 25 while at war, plus everything they **remember**: "*A gift of ₥1,000 (+20)*", "*Caught your agent: rob their treasury (-30)*". The dossier shows the five that matter most. Memories fade over weeks; betrayals (a broken pact with a Zealot, a guest seized at your table) **never do**.

The houses also think about each other: they sign trade pacts, declare wars and make peace without you. A forged letter can make two of them fall out. Two houses bleeding each other dry is an opportunity.

### Rise and fall

A house **falls** when its head dies (old age, now and then; the Old any day now), when it's bankrupt for three days (no gold, no regiments), when you ruin it at the end of a war, or when your assassin gets through. Now and then a richer family buys out the weakest seat at the table.

- **An heir** (a death): same house, a new face, maybe a new temperament, and **half of every grudge** (and the war, if there is one).
- **A newcomer** (everything else): a new house moves into the seat with a clean slate and hangs its own banners.

A Charter sits with **3** houses, a Compagnie with **4**, the VOC with **5**. Empty seats fill at dawn.

## Honour and Fear

| | Low Fear | High Fear |
|---|---|---|
| **High Honour** | The Benevolent Diplomat: pacts come easily | The Iron Ruler: respected and obeyed |
| **Low Honour** | The Snake: nobody trusts you, nobody fears you | The Cutthroat: demands get paid, pacts don't get signed |

- **Honour** (0 to 100, starts at 50): up for gifts (+1 a day), sending a spy home unharmed, letting a prisoner go. Down for tearing up pacts (-5 to -20), unprovoked war (-5), getting caught on an errand (-5, -15 for murder), leaving an ally to fight alone (-10), missing a tribute payment, and seizing a guest (-40).
- **Fear** (0 to 100, starts at 10, slowly fades back): up for winning wars (+15), ruining a house (+25), hanging spies, battle victories, assassinations and seizing a guest (+30). Down for losing wars and paying tribute.

## Diplomacy

From the dossier (by letter) or face to face (at your hall or their seat: **+15% on every proposal**, and gifts count for more). One proposal per house per day.

| | What it does |
|---|---|
| **Gift** | ₥200 (or ₥1,000 with a right-click), more as your company grows. Into **their treasury**: you may be paying for their next regiment |
| **Trade pact** | Both of you earn ₥40 a day (more as you grow). Tear it up: Honour -5 |
| **Peace pact** | Neither of you attacks the other. Break it: Honour -10 (-20 and every house remembers if you attack them) |
| **Alliance** | Needs a peace pact. Your allies join your wars (and call you to theirs) |
| **Demand tribute** | A third of their income every day for ten days. Works on Fear and a bigger army. Never on the Proud |
| **Declare war** | Shift-click. Unprovoked costs Honour; if they raided, sabotaged or spied on you lately, nobody blames you |
| **Peace** | A white peace (likelier the better you're doing), or buy it outright when you're losing |
| **Invite** | To your hall (see below) |
| **Directions** | A compass to their seat |

The houses come to you, too: offers of pacts, gifts, demands for tribute (pay up, or they may march), and spies and saboteurs your people catch (hang them for Fear, turn them to read their master's letters for six days, or send them home for Honour). Unanswered dispatches get their last answer when they expire. Answer from anywhere with `/heeren dispatches` and `/heeren answer <n> <1-3>`.

**Defending yourself**: your regiments beat off raids on your convoys, and your best spy-catcher (the highest **Guile** among your Compagnie agents who are at home) catches spies and saboteurs. A saboteur who gets through makes your Compagnie's trade jobs 10% harder for three days.

## Errands

Pick an errand in a dossier, an advisor (**De Kapitein**: Force, **De Koopman**: Coin, **De Spion**: Guile) and a Compagnie agent who's in. The odds are worked out like a Compagnie job's (the agent's stat, traits, your rank, the Enterprise skill), and the agent comes home at dawn the same way: experienced, wounded, captured or not at all.

| Errand | Days | Does |
|---|---|---|
| **Spy on them** | 2 | Their real numbers, temperament, quirk and plans, for six days (ten on a triumph) |
| **Sabotage their trade** | 3 | Their income -40% for a week |
| **Pay bandits to raid them** | 2 | They lose a tenth of their treasury and a regiment |
| **Rob their treasury** | 3 | 15% of it is yours (25% on a triumph) |
| **Bribe their captains** | 2 | A quarter of their regiments desert; half of those join you |
| **Forge a letter** | 2 | Two houses fall out badly. Pick who gets the letter |
| **Assassinate** | 4 | Only against an enemy (at war, or hostile). The head of the house dies; the heir takes over. Shift-click to send |

A botched errand may be **traced back to you**: the house remembers, you lose Honour, and a hostile house with the regiments for it calls it an act of war.

## War

- A war has a **score**: +100 means you've crushed them, -100 means they've crushed you.
- Every dawn the armies **skirmish**: both sides lose regiments, the score moves (by up to 20), and the day's winner plunders the loser's purse.
- A house that's losing badly **sues for peace**: take reparations, tribute for ten days, or (at +80) **their ruin**: the house falls. A house that's winning offers you terms: pay reparations, or fight on. At ±100 the war ends on its own.
- No regiments and no gold to raise them? That's how wars end, for them and for you.

### Pitched battles

Now and then a house's army marches on you, or you **give battle** yourself from the dossier. The armies meet at **noon** at a field halfway to their seat (the Court and Army tabs give you a compass to it).

- **Be within 64 blocks at noon** and the battle is fought for real: your regiments in orange and theirs in their colours form up in two lines (a soldier for every half regiment, up to twelve a side) and charge. **Fight in it**. A boss bar shows who's still standing. What happens on the field decides the day: the score swings with who lost more, and the winner takes the loser's baggage train.
- **Stay away** and the captains fight it without you: a bigger swing than a skirmish, either way.

### Storming a seat

At war, ride up to their seat: the gate guards are gone and a **garrison** turns out to meet you (a soldier for every half regiment, up to eight). Every two of them you cut down is a regiment they lose (and +2 on the score). Once the garrison's broken, **loot the treasury chest** by the high table: 35% of their treasury (+15 on the score), once every three days. At peace, the chest stays locked.

## In person

### Their seat

Every house has a seat 700 to 1400 blocks from your desk: a walled courtyard with corner towers and a brick manor, hung with their banners. It's **built the first time a player rides up to it**. Get a compass from the dossier.

Walk into the great hall and right-click the lord, standing in front of their throne: an **audience**. Every proposal is likelier face to face; you can **accept their hospitality** (a small warmth, once a day); and meeting them reveals their temperament and quirk.

### Your hall

Craft an **Audience Bell** from a **Bell + Gold Ingot + Paper** (any shape) and hang it in your hall. Right-click it to ring it (and see who's visiting).

**Invite** a house from its dossier (₥100, more as you grow): if they don't dislike you too much (the Paranoid need to like you) and you're not at war, their coach pulls up within the minute. The head of the house and two guards stand by your bell **until the second dawn**. Right-click your guest for an audience:

| | |
|---|---|
| **Talk** | Gifts, pacts, tribute, peace: all likelier face to face |
| **Lay on a feast** | From what you carry: up to 16 food, 6 [Proost](../proost/README.md) spirits and 4 [Havana](../havana/README.md) cigars. Food is nice, spirits nicer (a Drunkard's favourite, a Pious guest's horror), cigars nicest. Once a day, up to +25 opinion |
| **See them off** | Polite, and free |
| **Bar the doors** | Click, then shift-click. Your guest is your **prisoner**. Honour -40, Fear +30, war with their house, and **every house remembers, forever** |

A prisoner can be **ransomed** (40% of their treasury), **let go** (Honour +5; it won't undo it), or **executed** (the worst thing you can do at court).

## Your army

Raise regiments in the **Your Army** tab: **₥500** each, then **₥25 a day**, paid at dawn. Can't pay? The unpaid ones desert. They beat off raids on your convoys, fight the skirmishes, and line up in pitched battles. Sellswords are yours to command personally; regiments are the Compagnie's army.

## Commands

- `/heeren`: how it works
- `/heeren court`: where you stand with every house
- `/heeren dispatches`, `/heeren answer <n> <1-3>`: letters, from anywhere
- `/heeren give bell` (op)
- `/heeren intel` (op): every house's letters are an open book for a week
- `/heeren battle` (op): moves the pending battle to today's noon

## Notes

- Courts are saved in `<world>/hoogheheeren.json`. Each player has their own table of rivals (single player and servers alike).
- Lords, guests, guards and soldiers are only there while someone's near, and aren't saved with the world (a leftover is tidied away when its chunk loads).
- Seats are built with vanilla `fill` and `setblock` commands into the live world, only in loaded chunks.
- De Compagnie 1.2.0 added the hooks this mod uses (`CompagnieHooks`): a tab on the desk, a turn at dawn, a say in the job odds, and its agents to send on errands.
- CI (`.github/workflows/hooghe-heeren.yml`) builds Market, De Compagnie and the mod, then boots a real dedicated server and runs a court without a player: a table of rivals, gifts, pacts, deserting regiments, errands (one triumphs, one is caught), a war won by reparations, a war between rivals, an heir and a newcomer, ten dawns, a seat built in the world with its lord, a garrison with one cut down, a guest seized and ransomed, and a save and load.
