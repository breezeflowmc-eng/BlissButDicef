# DiceGems

Roll a dice when you join. The number you roll decides your gem. Higher number = stronger gem.

Paper / Spigot **1.21+**, Java **21**.

## Build

```
mvn package
```
The jar ends up in `target/DiceGems-1.0.0.jar`. Drop it in `plugins/`.

## How the dice works

* First time a player joins (or every join, see `dice.roll-every-join`) a dice rolls on their screen
  (title animation that slows down like a real dice) and the gem they land on is put in their inventory.
* Gems are ranked **1 (weakest) to 18 (strongest)** - see the table below.
* **Default dice = the number of gems (d18)**, so the number you roll *is* your gem's rank.
* `/roll 100` makes it a d100. The range is split into equal slices, one per gem
  (1-5 = rank 1, 6-11 = rank 2 ... 96-100 = rank 18). `/dicegems list 100` prints the exact slices.
* Dice smaller than the gem list (`/roll 6`) use the number directly, so you can only get ranks 1-6.

## Commands

| Command | What it does |
|---|---|
| `/roll` | Re-roll yourself |
| `/roll 100` | Re-roll yourself on a d100 |
| `/roll <player>` | Re-roll one player |
| `/roll @a` | Re-roll everyone (any vanilla selector works: `@a[distance=..20]`, `@s`, ...) |
| `/roll @a 100` / `/roll <player> 100` | Same, with a custom dice |
| `/dicegems mine` | Show your gem |
| `/dicegems list [max]` | Ranking + which dice numbers give which gem |
| `/dicegems info <gem>` | Abilities of a gem |
| `/dicegems trust add/remove/list` | Friends your abilities never hurt (and do buff/heal) |
| `/dicegems give <player> <gem>` | Admin: set a gem directly |
| `/dicegems clearcd [player]` | Admin: reset cooldowns |

Permissions: `dicegems.roll` (roll yourself, op by default), `dicegems.roll.others` (op),
`dicegems.admin` (op), `dicegems.use` (use abilities, everyone).
Give `dicegems.roll` to everyone only if you want free re-rolls.

## Using a gem

Keep the gem item in your hotbar or offhand: passives work, and
**right-click** = ability 1, **shift + right-click** = ability 2.
Gems are soulbound - they can't be dropped, stored in chests/frames, and come back after death.

## The 18 gems (weakest -> strongest)

| # | Gem | Passive | Right-click | Shift + Right-click |
|---|---|---|---|---|
| 1 | Pebble | 50% less fall damage | Skip Stone: 3 dmg + slow | Stone Skin: Resistance I |
| 2 | Sprout | Regen on grass | Bloom: heal self + allies | Thorn Snare: slow + poison |
| 3 | Ripple | Water breathing + dolphin's grace | Tide Push: shove + 2 dmg | Bubble Dash: dash, extinguish |
| 4 | Lumen | Night vision | Flare: enemies glow + blind | Beacon Step: Speed II |
| 5 | Ember | Fire resistance | Cinder Shot: 5 dmg + ignite | Warm Aura: ignite nearby, heal 2 |
| 6 | Gust | Jump Boost I, no fall damage | Updraft: launch + slow fall | Gale Slam: fling target, 3 dmg |
| 7 | Gloom | Speed I in the dark | Veil: invisible 6s | Dread: blind + weaken |
| 8 | Static | Haste I, lightning immune | Arc: 4 dmg, chains for 3 | Overcharge: Speed II + Haste II |
| 9 | Venom | Poison immune, 15% poison on hit | Spit: Poison II + slow | Web Trap: cobwebs on target |
| 10 | Aegis | 8% less damage | Bastion: Absorption II | Shield Bash: 4 dmg + knockback |
| 11 | Frost | Freeze immune, 20% slow on hit | Cold Snap: AoE 3 dmg + Slow III | Ice Lance: 5 dmg + Slow II |
| 12 | Mender | +1 HP / 8s | Lifeline: heal self + allies 4 HP | Siphon: 4 dmg, heal 4 |
| 13 | Rift | Endermen ignore you | Blink: teleport 8 blocks | Swap: trade places with target |
| 14 | Prospector | Haste I, 20% double ore drops | Gold Rush: Haste III + Speed | Fool's Gold: fatigue + weakness |
| 15 | Hunter | +10% dmg vs <50% HP targets | Mark Prey: glow, +15% dmg | Frenzy: Speed I + Strength I |
| 16 | Titan | +2 hearts | Quake: AoE 5 dmg + launch | Iron Will: Resistance II |
| 17 | Wraith | 6% of attacks phase through you | Spectral Daggers: 3x3 dmg fan | Phase Shift: invisible + Speed II |
| 18 | Tempest | Speed I, lightning immune | Thunderstrike: 6 dmg AoE at aim point | Storm Surge: buff allies, slow enemies |

Cooldowns range from 20s to 90s (`cooldown-multiplier` in the config scales all of them).

## Adding or re-ranking gems

Everything is in `GemRegistry.java`. Gems are registered weakest first; the order *is* the rank,
and the dice mapping adapts automatically when you add or move a gem.

## Original plugin (BlissGems 5.2.2) - what this was modelled on

Original has 8 base gems, each with tier 1 / tier 2, energy (0-10), trust, remappable keybinds, and
three mythic gems (Gold, Auratus, Heretic) kept out of the random starter pool.
New players get `getRandomEnabledGem()`, a uniform random pick from the enabled gems.
Original ranking (from ability counts, damage, cooldowns and passives in its config):
Astra > Flux > Fire > Strength > Speed > Puff > Life > Wealth.
