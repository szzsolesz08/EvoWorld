# 30. Healing is metabolism

Status: accepted

## Context

Health lost was lost for good. `Needs.heal` existed, but nothing called it. In the
default run (seed 42), Atlas goes hungry on day 1 while he learns what berries are, and
he carried the 13.5 health he lost there to the end of the run. Every close call lowered
the floor for the next one. With Atlas starting at health 30, the committed milestone 12
build recovered in 0 of 20 seeds.

The plan was three parts:
- healing in the metabolism, faster at rest;
- a `RECOVER_HEALTH` goal, served by resting;
- injury counting as a pressure in exploring's calm factor, so a hurt agent stays close.

## Decision

**Healing is part of `Needs.afterTick`.** After a tick's drain and damage, a body that is
well enough heals by `MetabolismRates.healingFor(activity)`: 0.2 while active and 2.0
while resting. "Well enough" means alive, hunger and thirst at most 50, and energy at
least 25. Those conditions are far from every condition that does damage, so no tick both
hurts and heals, and the auditor checks that on every tick. The existing `lastActivity`
charging gives resting its bonus, and the loop does not change. The rates live in the
metabolism and so in the save; the format goes to version 3.

**`RECOVER_HEALTH`** scores `(1 − health/100)²`, the same curve as energy.
`RestProposer` offers rest for it only when resting would heal. Its benefit is the
*extra* healing a tick at rest buys over carrying on. `RestAction` refuses only when
there is nothing to recover, so a wounded agent can rest without being tired.

A `Recovered` event reports health coming all the way back. It is rare and closes the
story a `DangerBegan` opened, so it is shown at every log level.

### What the measurements changed

A stress harness started Atlas at health 30 in 20 seeds (10-minute ticks, 2,000 ticks):

| Variant | Alive | Ticks to full health | Rests for health / all rests | Explore choices while <70 |
| --- | --- | --- | --- | --- |
| milestone 12 (no healing) | 20/20 | never | — | 16,739 |
| heal 0.1 / 0.5, injury in calm | 20/20 | 476 | 33 / 8,912 | 2,791 |
| heal 0.1 / 0.5, no injury in calm | 20/20 | 476 | 23 / 8,965 | 2,859 |
| injury counted double in calm | **17/20** | 576 | 510 / 7,338 | 1,890 |
| **heal 0.2 / 2.0, no injury in calm** | 20/20 | 176 | 20 / 6,181 | 1,242 |

- **Injury stays out of calm.** At single weight it changed nothing. At double weight it
  killed three agents, because a wounded newborn that stops exploring never finds food.
  For an agent that knows nothing yet, curiosity is how it survives.
- **The health goal is a tie-break, not a driver.** One tick of rest buys 1.8 health out
  of 100, and that rarely outscores anything else the agent could do. It wins when
  nothing presses: a badly hurt agent in a world it already knows rests rather than
  wanders. Most recovery happens during rests the agent takes for energy. A longer
  "recuperate" action that would make resting to heal a real choice was considered and
  deferred. It adds tuning and a risk of an agent that never gets up again, and the
  body heals well enough without it.
- **Faster rates** (0.2 / 2.0 rather than 0.1 / 0.5) take recovery from 30 to full
  from 476 ticks to 176 (about 29 hours at 10-minute ticks).

Considered and rejected: making healing cost extra hunger. It is more realistic, but it
is a second balance lever before there is a harness to tune it (milestone 15).

## Consequences

- Healthy agents behave exactly as before. Across 20 seeds at both 10- and 60-minute
  ticks, every figure in the sweep is identical, down to the number of rests: the new
  paths only switch on after an injury.
- The default run's day-1 injury is healed by day 2, 01:40.
- The metabolism is charged per tick, not per simulated minute, so the rates mean more
  per hour at shorter ticks. That was already true of hunger and thirst.
