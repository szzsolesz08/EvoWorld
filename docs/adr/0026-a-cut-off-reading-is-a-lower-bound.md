# 26. A cut-off reading is a lower bound

Status: accepted. This replaces the "sampling bias" explanation in ADR 22.

## Context

ADR 22 noted that learned values sat below the truth. After 51 meals an agent believed
berries were worth 7.8 against a real 8.0, and I called that a sampling bias that correctly
reflected experience. That was wrong, and the integration test showed how wrong. With the
loop reordered, the agent ate more often while only a little hungry, and its belief about
berries sank to **5.4**.

The cause is a measurement error. If four berries take hunger from twenty to zero, the
agent has not learned that berries are worth five. It has learned they are worth *at
least* five, because whatever else they had to give went nowhere. Every meal that left it
full was read as a low measurement. An agent that looks after itself eats before it is
starving, so it is exactly the well-kept agent that learned the worst values.

## Decision

A reading that ran out of need is treated as the lower bound it is:

- **nothing tested yet**: a floor is not a value, so wait for a reading that didn't run out
- **floor at or below the current belief**: it agrees, so count it as confirmation. That
  adds one observation of the same estimate, giving more confidence without moving it.
- **floor above the current belief**: the thing is worth more than thought, so the
  estimate moves up to the floor

A cut-off reading can raise an estimate or firm it up. It can never pull it down.

A first attempt simply ignored cut-off readings. That fixed the values but starved
confidence: an agent that drinks until it isn't thirsty produces almost nothing *but*
cut-off readings, so after twenty days it was only 0.57 sure what water does. That made
walking to water look riskier than it was, and one seed died of thirst. Treating the
reading as a bound keeps the information that ignoring it threw away.

Separately, only `EatAction` now teaches nourishment. Putting something down also empties
the hands and, read naively, says "that food was worth nothing".

## Consequences

- Twenty days in, an agent believes berries are worth **8.0** (0.98 sure after 123 meals)
  and water **12.0** (0.98 sure after 117 drinks), exactly the true values.
- The integration test asserts the learned value is within 1.0 of the truth, so this can't
  quietly regress.
- Hydration still divides by the gulp asked for rather than the gulp received, so a spring
  with less than a gulp left would read low. Springs refill fast enough that this has not
  shown up, but it is the same kind of error and would need the same treatment.
