# 2. Observers receive immutable snapshots

Status: accepted

## Context

Something outside the simulation needs to see what is happening — the console logger
today, a viewer and a save file later. Handing out live domain objects would let an
observer mutate the world, and would make a renderer reading state on another thread a
source of hard-to-reproduce bugs.

## Decision

`SimulationObserver` callbacks carry only immutable values. `World.snapshot(tick)` copies
the world into a `WorldState` of `AgentSnapshot` records, and that is what observers get.
All callbacks have default no-op implementations, so an observer subscribes only to what
it cares about.

`Perception` is built the same way, from a snapshot rather than from the live world: the
decision layer physically cannot reach around perception to inspect the world.

## Consequences

- An observer cannot perturb the simulation, so an observation bug can never become a
  simulation bug.
- Snapshots cost a copy per tick. The world is tiny and the copy is shallow over
  immutable values, so this is not worth optimizing until it measurably matters.
- The same snapshot type is the natural unit for save/load in a later milestone.
- Because every field is an immutable value, `WorldState` has value equality, which is
  what lets a determinism test assert "these two runs are the same" in one line rather
  than comparing fields by hand.
