# 10. The chosen action decides what the tick costs

Status: accepted

## Context

"Energy decreases while active and recovers while resting" cannot be expressed by a single
per-tick drain. With one rate, resting is only ever a smaller loss offset by a separate
gain, and the body has no idea whether it spent the hour walking or asleep.

## Decision

Every `Action` declares an `ActivityLevel` — `ACTIVE` or `RESTING` — defaulting to
`ACTIVE`, since most things take effort. `Simulation` passes the chosen action's level to
`agent.live(rates, activity)`, and `MetabolismRates` carries a drain rate for each.

The body therefore charges the right amount without knowing what the action was, and a new
action declares its cost rather than editing metabolism. `MetabolismRates` rejects a
configuration where resting costs more than being active, because that would make resting
pointless and is far more likely a typo than an intention.

## Consequences

- Resting is a genuine net gain: a lower drain *and* the action's recovery. An agent that
  burned the same energy asleep as on the move could never come out ahead by stopping.
- The energy curve this produces is the first emergent behaviour in the project. Nothing
  says "rest at 50%", but rest utility `(1 - e/100)²` and explore utility `0.45 · e/100`
  cross near half energy, so the agent settles into alternating the two and its energy
  holds around 50 for days.
- Adding eating or gathering means declaring an activity level on the new action, not
  touching `Simulation` or `Needs`.
- A third level (sprinting, heavy labour) is an enum constant and a rate, which is the
  shape this was chosen for.
