# 8. World generation draws from its own named stream

Status: accepted

## Context

Two subsystems now need randomness: generating the world, and the agent deciding where to
explore. Drawing both from one stream couples them. Give the agent one more decision to
make and every later draw shifts, so the world generated from seed 42 quietly becomes a
different world — the map moves because the mind changed.

That is a bad property for a project whose whole point is watching behaviour evolve. You
want to change how an agent thinks and see it act differently *in the same world*.

## Decision

`SimulationRandom.seededWith(seed, "worldgen")` derives an independent stream from the run
seed by mixing the name in with the ordinary string-hash recurrence, whose result is
specified by the platform. `WorldGenerator` draws only from that stream; the agent draws
only from the simulation's main one.

Placement takes locations from a shrinking pool of free tiles rather than guessing a tile
and retrying on a collision. It cannot loop, and the number of draws depends only on the
plan, never on luck.

## Consequences

- A run is still reproducible from one seed, and the world is now reproducible
  *independently* of the agents in it. There is a test that drains the main stream and
  shows the world does not move.
- Subsystems added later — weather, animals, mutation — should each take their own named
  stream for the same reason, and the naming convention is the whole mechanism.
- Two streams are two positions to record when saving a run, not one.
- The mix is not a strong hash. It does not need to be: streams must be independent for
  practical purposes, not cryptographically unrelated.
