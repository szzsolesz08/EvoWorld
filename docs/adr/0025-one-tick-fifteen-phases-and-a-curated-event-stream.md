# 25. One tick, fifteen phases, and a curated event stream

Status: accepted

## Context

Each milestone added its stage to the tick where it happened to fit. By milestone 10 the
loop worked but no longer read as a design: the environment regrew *after* agents acted,
the body paid for a tick *after* the mind had used it, and the only way to watch a run was
a log line for every perception, every candidate and every score. Ten simulated days
produced over eight thousand lines, and a death was one of them.

## Decision

`Simulation.tick()` runs fifteen phases in a fixed order and is written to read as that
list:

1. advance world time
2. update the environment
3. update each agent's needs for the tick just lived
4. perceive
5. recall relevant memories, as part of what is perceived
6. update goals
7. to 9. generate, evaluate and select a candidate, which is the decision maker's job
10. execute
11. process the consequences
12. create memories
13. update knowledge
14. update skills
15. report

The world and the body change **before** the mind looks at them. An agent decides on the
world as it now is, and an agent whose needs killed it overnight does not get to act in the
morning. Phase 3 charges the effort of what the agent was doing through the tick it just
lived, so the cost of an action still lands on the tick it was performed.

Reporting is split in two. The existing observer callbacks remain the **trace**: every
perception, deliberation and outcome, for debugging a decision. On top of them sits a
sealed set of **events**, the moments a narrator would mention: born, discovered, learned
(first time only), danger began, danger passed, skill improved, died, and one daily
summary. The runner shows events by default and the trace only with `--trace`.

Speed is set in the runner (`--speed <ticks/s>`) by a `Pacer` that waits between ticks,
aiming at a schedule so the rate does not drift. The core still contains no wall-clock time
(ADR 5).

## Consequences

- Ten simulated days read as 113 lines. The trace for the same run is 8,656 lines.
- A paced run and an unpaced run are the same run. There is a test for it, and the event
  streams diff clean.
- Because the environment regrows before agents act, a quarter-berry is back before a
  two-tick gather finishes. One test that emptied a bush mid-job to make it stale now
  fills the agent's bag instead: the world can regrow a bush, but it can't empty a bag.
- The event set is sealed, so adding an event is a compile error in every renderer that
  switches over them. That is deliberate: "what the simulation thinks is worth reporting"
  has a single answer in one file.
- Moving phases changed every agent's route. Survival across ten seeds is 9/10. The one
  death is the dithering failure described in ADR 24, and persistent intent is still the
  fix for it.
