# 12. Actions can take more than one tick

Status: accepted

## Context

"Actions consume simulation time where appropriate" only means something if some action
takes longer than another. Otherwise `durationTicks()` is a decorative number and picking
a bush clean costs exactly what taking one step costs.

## Decision

`Simulation` tracks what each agent is part-way through. An action with a duration above
one occupies its agent: no deliberation happens while it is under way, the effect lands on
the final tick, and metabolism runs every tick at the action's activity level throughout.
Gathering takes two ticks; everything else takes one.

The precondition is re-checked when the action completes, not only when it starts. A
two-tick gather can be overtaken by events — the node it aimed at may be empty by the time
the work finishes — and a stale action must block rather than apply. That falls out of
`execute` being the only way in, at no extra cost.

## Consequences

- An agent that dies mid-job leaves nothing behind: unfinished work is discarded with it,
  so a half-finished gather can never pay out.
- Work in progress is held in the `Simulation`, keyed by agent id in an insertion-ordered
  map so iteration can never make a run depend on hash codes. It is deliberately *not* on
  the `Agent`, which stays a body rather than a schedule — but it is state a save file will
  have to include, which is noted for the persistence milestone.
- Long actions are now a lever for balance. Making gathering slow is how foraging becomes a
  real cost rather than a free refill, and it needs no new mechanism to tune.
