# 33. A body in real time

Status: accepted. This supersedes the per-tick rates of ADRs 10 and 30.

## Context

Every rate that changed a body or the world was charged **per tick**, and the tick length
only moved the clock. At the default ten minutes a tick, thirst hurt after about 13 hours,
hunger after 20, and an hour of rest restored a third of the energy bar. Atlas's life was
eat, drink, rest, repeat: about 7.5 trips a day for food or water, and rest in scattered
bursts. At 60-minute ticks the same numbers ran six times faster per hour, so the survey's
`hourly` scenario was really a harsher world, not a coarser one.

The user wanted a realistic day, with room left for the gathering, building and crafting
still to come. They also wanted it to scale: a water bottle, a backpack of food and a bed
should each change something measurable later. They chose real-life rates, separate rest
and sleep, and one bar per need with an earlier appetite.

## Decision

**Rates live in simulated time.** `MetabolismRates`, resource regrowth, walking speed (six
tiles an hour) and gather time (20 minutes) are stated per hour or in minutes. They are
converted once for the run's tick length (`Rates.perTick`, `perTick(Duration)`,
`stepsPerTick`, `gatherTicks`). At longer ticks a move covers several tiles. The
conversion multiplies before it divides, so today's values came back exactly: the first
stage of this milestone was a refactor that left every 10-minute scenario byte for byte
the same, and changed only `hourly`, which was the bug.

**The rates are a person's:**

| | Per hour |
| --- | --- |
| thirst | +2.0 (hurts after about two days) |
| hunger | +0.7 (hurts after about five and a half days) |
| energy, awake / sitting still / asleep | −5 / −1.5 / +10 |
| healing, well: active / resting / asleep | 0.25 / 1 / 2 |
| damage when starving / parched / spent | 0.6 / 4 / 2 |
| regrowth: berries / water / wood | 1.5 / 30 / 0.3 |

A meal and a drink are one unit (8 hunger, 12 thirst).

**Activity decides the body's change, and `Exertion` carries comfort.** `ActivityLevel`
gains `SLEEPING`. Each tick is charged for the action's `Exertion(level, comfort)`.
Comfort multiplies recovery, meaning energy restored and healing, and never costs. It
is 1 on bare ground, and a bed will be a sleep with more comfort. Rest and sleep add
nothing in their effects: the body recovers tick by tick through the metabolism, so a
sleep cut short keeps what it gained.

**Sleep is an action.** `SleepAction(ticks, comfort)` is a multi-tick action whose benefit
counts from its first tick (`Action.ticksUntilBenefit`). Without that, an eight-hour sleep
would be discounted by its whole length and never chosen. `SleepProposer` offers it below
25 energy, sized to reach full energy. The sleep is capped at ten hours, and cut short so
hunger or thirst cannot start hurting before he wakes; a need already hurting sets no
limit. It is also offered at 50 health or less as bed rest.

**Appetite.** Hunger and thirst want `0.4 × appetite + 0.6 × urgency²`. Appetite is early
and levels off at 50 for hunger; for thirst it builds over the whole bar. A need below 5
is not felt at all.

### What the measurements changed

The survey drove every change below. Each is a finding, not a tuning knob:

1. **An offer is worth what it offers the need, measured the same everywhere.** At first
   a trip to a bush was worth one portion, so a newborn didn't taste berries until day 6
   and starved. Valuing trips at a whole meal then made standing in a spring (one gulp)
   look worse than walking to wood, and Atlas died of thirst stepping between them. The
   consistent rule: here or there, an opportunity is worth what it can do for the need,
   capped by the need.
2. **Taste before you feast.** Valued at a whole meal, every untried thing looked
   equally good to a newborn, and he walked between them. An untried thing is now worth
   a taste.
3. **A trip counts the work at the end.** A walk to food now includes the gather before
   any of it can be eaten (`ActionCandidate.workAtTarget`). Otherwise a step to the next
   bush tied with picking the one underfoot.
4. **Sitting still is not sleep.** At +2 an hour, rest replaced sleep: he rested 35% of
   the day and slept 4.5 hours. Resting now only slows the drain.
5. **Stay up, then sleep once.** Sleepy at 70, he slept 3.3 times a day. Sleepy at 25
   gives about one night.
6. **A meal stays one unit at any tick length.** Scaling bites with time made an hourly
   gulp six times any thirst. A reading that always runs out of thirst teaches nothing
   (ADR 26), so he never learned what water was and "experimented" hourly forever. At
   long ticks a meal simply takes a whole tick.
7. **Bed rest.** With realistic healing, one tick of rest is worth almost nothing, so the
   milestone 13 tie-break stopped working. Sleep, which heals fastest, is offered when
   badly hurt. For sleep, only hunger and thirst decide whether it will mend, because a
   tired body is soon rested once asleep.

## Consequences

Across 20 seeds × 20 days:

| | baseline | hourly | wounded | hungry-world | sparse |
| --- | --- | --- | --- | --- | --- |
| survived | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 |
| close calls | 0 | 0 | 0 | 0 | 0 |
| sleep, hours a day | 8.8 | 8.7 | 9.3 | 8.8 | 8.8 |
| nights a day | 1.15 | 1.08 | 1.20 | 1.15 | 1.15 |
| meals / drinks a day | 2.6 / 5.1 | 2.3 / 4.2 | 2.5 / 4.6 | 3.8 / 6.9 | 2.6 / 4.7 |
| free time | 41% | 19% | 40% | 35% | 38% |

- There are no dithering runs, and a newborn learns what food is without ever being hurt.
  The same day comes out within 25% at 10- and 30-minute ticks, and a test holds it there.
- **Free time misses the 50% target.** Water can't be carried, so he walks about 3.6
  hours a day to springs. When nothing else is worth doing, that walk is also the best
  use of idle time, so the number understates how free he is. A water bottle, or any
  work competing for the day, is what will move it.
- `hourly` keeps the rhythm, but a meal or drink takes an hour there, so its free time is
  low. The ten-minute tick is canonical.
- Cognitive horizons stay in ticks: memory and familiarity half-lives, the utility's
  patience, and intent staleness. They don't move the bars.
- The save format goes to version 4: per-hour rates, and exertion comfort. A sleep or a
  gather is saved with its length, and a move with its steps per tick.
