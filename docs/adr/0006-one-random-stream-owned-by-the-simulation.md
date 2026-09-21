# 6. One random stream, owned by the simulation

Status: accepted

## Context

Milestone 0 seeded a `java.util.Random` in the runner and handed it to the decision maker.
That was reproducible in practice but only by convention: nothing stopped a second
collaborator from constructing its own generator, and a future save file would have had no
way to record where in the stream a run had got to.

## Decision

`SimulationRandom` is the single source of randomness. `Simulation` constructs it from
`config.seed()` and lends it to collaborators as an argument —
`DecisionMaker.deliberate(perception, random)` — rather than letting them hold one.

The wrapper exists for three reasons a bare `RandomGenerator` does not cover:

- It pins the algorithm to `java.util.Random`, whose sequence is specified by the platform,
  so a run replays on any conforming JVM rather than only on this one. A test asserts a
  known sequence to catch that guarantee breaking.
- It counts draws. The seed alone cannot resume a saved run — restoring it would rewind
  the agent's luck to the beginning — so the position in the stream is the other half of
  what a save file needs.
- It is a single choke point. "Nothing else invents randomness" becomes something you can
  check by searching for constructor calls instead of a convention people must remember.

## Consequences

- A decision maker cannot hold private randomness, so it cannot drift from the run it
  belongs to.
- `Simulation` no longer accepts a clock or a generator; it derives both from the config.
  That removes every way to construct a simulation whose behaviour disagrees with the
  configuration it reports, at the cost of needing a new factory method when loading a
  saved run — which milestone 6 will add deliberately.
- `randomDrawCount()` doubles as a cheap fingerprint: two runs that agree on the seed but
  not the draw count have diverged.
