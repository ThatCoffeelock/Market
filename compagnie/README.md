# De Compagnie

A Fabric mod for Minecraft **26.3** that lets you run a **shady 17th-century trading company**, in the spirit of the Dutch East India Company (the VOC), from a war table like Dragon Age: Inquisition's.

- **A Company Desk**: hire agents, bankroll jobs, lend money, collect the spoils.
- **Agents** with three stats (**Force**, **Coin**, **Guile**), a trait or two, a daily wage, and a nasty habit of getting captured.
- **32 jobs** in four kinds: **trade**, **raids**, **exploring** and **shady business**. Every job can be done three ways, by three advisors.
- **Minecraft days**: jobs take one to six days and come home at dawn, while you're away too.
- **Telegrams** from the road: pay up, press on or gamble.
- **The Ledger**: lend Marks (or gold) to dubious borrowers. Send a collector after the ones who don't pay.
- **Grow** from a Charter to a Trading House, a Compagnie, and finally the VOC.

It's **server-side only**. Friends join with a plain vanilla client. It needs the **Market** mod (Marks are the money).

## Install

Put **Fabric API**, **`market-<version>.jar`** and **`compagnie-<version>.jar`** in your server's `mods/` folder. Both jars are on the [compagnie-latest release](https://github.com/ThatCoffeelock/Market/releases/tag/compagnie-latest). Or build it yourself: `./gradlew build` in the repo root, then in `compagnie/` (needs Java 25).

## The desk

Craft a **Company Desk** from a **Cartography Table + Gold Ingot + Book** (any shape), place it and right-click it. Anyone's desk opens *their own* company: it's an office, not a safe. Breaking it gives the desk back.

The desk has tabs along the top:

| Tab | What's there |
|---|---|
| **The Company** | Your rank, reputation, profit, what's in your favour, the latest news |
| **Job Board** | Offers. New ones come in at dawn; old ones are withdrawn after a few days |
| **Under Way** | Jobs your agents are on, when they're back, the odds right now |
| **Agents** | Who's on the books, and how they're doing |
| **Hiring Board** | Three candidates, new ones every two days |
| **The Ledger** | Borrowers, loans, and the ones in default |
| **Telegrams** | News from the road, waiting for an answer |
| **Strongbox** | The spoils. Click a stack to take it, or take everything |

## Agents

| Stat | The advisor who uses it | Good for |
|---|---|---|
| **Force** | **De Kapitein** | Raids, escorts, debt collecting. Fast and dangerous |
| **Coin** | **De Koopman** | Trade, bribes, contracts. Safe, but pricey |
| **Guile** | **De Spion** | Smuggling, spying, exploring. Forged papers and back doors |

Stats go from 1 to 10 (better candidates as the company grows). Each point above or below 5 is 4% on the odds.

| Trait | What it does |
|---|---|
| Lucky | +8% odds on every job |
| Veteran | +5% odds, asks 20% more pay |
| Drunkard | 40% cheaper, -6% odds, may start a mutiny |
| Greedy | Pockets 15% of the Marks a job brings in; may start a mutiny |
| Coward | -10% the Kapitein's way, but never dies: runs, and comes back wounded |
| Navigator | +10% at sea, and voyages take a day less |
| Silver-tongued | +10% the Koopman's way |
| Butcher | +10% the Kapitein's way, and 10% more loot |
| Shadow | +10% the Spion's way |

Agents earn experience and level up to 5 (better at what they're best at, and they want a raise). **Wages** are paid at every dawn; if you can't pay, the best-paid agent who's in walks out. A **wounded** agent sits out a few days. A **captured** one waits in a cell: **pay the ransom**, or shift-click them to plan a **rescue** (cheaper, riskier: a botched rescue gets them hanged). And a dead agent is dead.

## Jobs

Pick an offer on the board, then pick an **advisor**, a **stake** and an **agent**, and send them.

- **The stake**: lean (-10% odds), standard, or lavish (+10%). What comes back scales with it.
- **The odds**: the job's base odds for that advisor, the agent's stat, the stake, your rank (+2% a rank), traits, what's in your favour, telegram answers and the Enterprise skill. Always between 5% and 95%.
- **The outcome**, at dawn on the day it's done:

| Outcome | Chance | What comes back |
|---|---|---|
| **Triumph** | a quarter of the successes | The stake times the job's payout × 1.6, and the full spoils (some only come on a triumph) |
| **Success** | the rest of the successes | The stake times the job's payout, and the spoils |
| **Partial** | 40% of the misses | 60% of the stake, half the spoils |
| **Failure** | 45% of the misses | 20% of the stake. The agent may come back wounded |
| **Disaster** | 15% of the misses | Nothing. The agent may die, be captured or come back badly wounded |

Marks go straight to your balance, the spoils to the **strongbox** (108 stacks; the overflow is sold to the Market).

### The jobs

| Kind | Jobs |
|---|---|
| **Trade** | Herring Fleet · Fur & Timber Contract · Tulip Speculation · Spice Run to the Indies · Coffee from Mocha · Underwrite a Voyage · Corner the Market · Porcelain from the East · Silk and Indigo · The Northern Passage |
| **Raid** | Escort a Convoy · Pirate Hunt · Raid an Illager Camp · Ambush the Smugglers · Letter of Marque · Burn the English Warehouse · Hire Out to a Siege · Sack a Spanish Fort |
| **Explore** | Chart the Coast · An Old Pirate's Map · The Haunted Castle · Find a Market Town · Rumours of the Trials · Relic Expedition · Expedition Beyond the Portal · The Deep Dark Ruins |
| **Shady** | Pressgang a Crew · Smuggle Past Customs · Bribe the Harbourmaster · Spread a Rumour on the Exchange · Forge a Charter · Blackmail a Burgomaster · Clip the Coin |

Some do more than pay:

- **Charts**: exploring jobs bring back a **Company Chart** to the **nearest real structure** of their kind: a shipwreck, an ocean ruin, a monument, a buried treasure, a village, the trial chambers, an ancient city, or (with [Hamlets & Horrors](../hamlets/README.md)) a ruined castle, a haunted cottage or a dungeon. The treasure's really there. Go and get it.
- **Corner the Market**: trade jobs pay 20% more for three days.
- **Burn the English Warehouse**: trade odds +10% for four days. Botch it, and the English want revenge (-10%).
- **Bribe the Harbourmaster**: sea jobs +10% for five days.
- **Forge a Charter**: a big jump in reputation.
- **Pressgang a Crew**: a free agent at the hiring board (a sailor who woke up employed).

Bigger jobs show up as the company grows.

## Telegrams

Half the jobs of two days or more get a telegram on the road: a storm, privateers, a nosy customs officer, a second buyer, a mutiny, fever, an informant. Every telegram has three answers:

1. **Pay** (15% of the stake): better odds.
2. The **default** if nobody answers by the time the job's done. Some test the agent's Force or Guile.
3. The **gamble** or the way out: turn back with 60% of the stake, dump the cargo (at best a partial, never a disaster), take the detour (+30% payout, -8% odds)...

Answer at the desk, or from anywhere with `/compagnie telegrams` and `/compagnie answer <number> <1-3>`.

## The Ledger

Borrowers turn up at dawn: *"Pieter the Baker wants ₥2,000 for 4 days at 18%, for a new oven."* Their reputation (**Reliable**, **Shaky**, **Notorious**) says how likely they are to pay; the interest matches. A quarter of them want **gold ingots** instead (bring them to the desk), and pay back in ingots, to the strongbox.

On the due day they pay up, or **default**. Then:

- **Click**: send a **collector** (a job, any advisor). A triumph brings back the debt and 20% for the trouble; a disaster means the borrower's fled the city.
- **Right-click**: sell the debt to **Leonard the Usurer** for 30%. He'll collect. Somehow.
- **Shift-click**: write it off. Very Christian of you.

## Ranks

| Rank | Reputation | Job slots | Agents | Offers | Biggest loans |
|---|---|---|---|---|---|
| Charter | 0 | 2 | 3 | 5 | ₥2,000 |
| Trading House | 1,500 | 3 | 5 | 6 | ₥5,000 |
| Compagnie | 6,000 | 4 | 7 | 7 | ₥10,000 |
| Verenigde Oostindische Compagnie | 20,000 | 6 | 10 | 8 | ₥25,000 |

Reputation comes from jobs (60 for a triumph, 40 a success, 10 a partial, plus 1 per ₥20 of profit on it), loans repaid (15) and forged charters.

## Skills

With [Skills](../skills/README.md), running the company levels **Enterprise**: +15% odds at level 100, and the perks **Broker** (cheaper agents), **Chartered** (more Marks and spoils from jobs) and **Iron Ledger** (a chance a defaulter pays after all).

## Commands

- `/compagnie`: how it works
- `/compagnie news`: the latest
- `/compagnie telegrams`, `/compagnie answer <n> <1-3>`: telegrams, from anywhere
- `/compagnie give desk` (op)
- `/compagnie dawn` (op): run a day for every company right now

## Notes

- A company day is a Minecraft day of game time (20 minutes), so it carries on while you're away, and `/time set` can't skip it. After a long time with the server off, at most a week is caught up.
- Companies are saved in `<world>/compagnie.json`.
- CI (`.github/workflows/compagnie.yml`) builds the mod, then boots a real dedicated server and runs a company without a player: it places a desk, hires an agent, checks the odds, sends a job out, answers a telegram, rolls ten thousand outcomes, brings a job home in triumph, makes a chart, overfills the strongbox, lends money (repaid, defaulted, collected, sold to the usurer), loses an agent, ransoms another, watches a coward run, forges a charter, climbs to the VOC, misses a payday, and saves and loads it all.
