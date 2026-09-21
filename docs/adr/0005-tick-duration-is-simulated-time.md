# 5. A tick duration is simulated time, not wall-clock pacing

Status: accepted

## Context

"Configurable tick duration" has two plausible meanings: how much time a tick represents
inside the world, or how long the program waits between ticks. They pull in opposite
directions — one is a property of the simulated world, the other is a property of watching
it.

## Decision

`SimulationClock` takes a `Duration` that means **simulated** time per tick. The clock
reports `elapsed()` as tick count times that duration, and `SimulationTime` carries both
numbers so an observer can report either without knowing the conversion.

A headless run executes as fast as the CPU allows regardless of the setting. Changing the
tick duration changes what a tick *means* — a run of 120 ticks is 20 hours at 10 minutes
per tick and 5 days at one hour — and nothing else. There is a test asserting exactly
that: the same seed at two tick durations produces identical agent states and different
elapsed times.

Wall-clock pacing is not implemented. If a live viewer ever wants it, it belongs in the
runner, where a sleep cannot reach the simulation.

## Consequences

- Tick duration is safe to change: it cannot alter behaviour, so it cannot break
  reproducibility.
- The core has no notion of real time at all — no `Instant.now()`, no sleeps, nothing that
  would make a run depend on when or how fast it was executed.
- Metabolism rates are expressed per tick, not per simulated hour. If the two ever need to
  stay in step, that conversion becomes a real decision; right now nothing needs it.
