# 4. A run is reproducible from its seed

Status: accepted

## Context

A simulation that cannot be replayed cannot be debugged. If an agent does something
strange at tick 4,000, the only practical way to investigate is to run it again and get
exactly the same thing.

## Decision

The same `(SimulationConfig, seed)` pair always produces the same run, tick for tick. That
requires, and is enforced by:

- One `SimulationRandom`, derived by `Simulation` from its config and lent to
  collaborators. Nothing else constructs a generator or calls `Math.random()` — see
  [ADR 6](0006-one-random-stream-owned-by-the-simulation.md).
- Agents processed in a fixed insertion order, and `World.agents()` returning a copy in
  that order.
- Deterministic tie-breaks everywhere a choice could otherwise be arbitrary — utility ties
  go to the earlier candidate, and `Location.stepTowards` prefers the x axis.
- No wall-clock time anywhere in the core, so a run cannot depend on how fast it executed
  — see [ADR 5](0005-tick-duration-is-simulated-time.md).
- Formatting pinned to `Locale.ROOT`, so logs are identical regardless of machine locale.

## Consequences

- No unordered iteration over hash-based collections anywhere a simulation outcome depends
  on the order.
- Save/load must persist the generator's position, not just the seed, or a resumed run
  will diverge from an uninterrupted one. `SimulationRandom.drawCount()` is that position.
- Parallelising agent updates would break this. If it is ever needed, it needs its own
  decision.
