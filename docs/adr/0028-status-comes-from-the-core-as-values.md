# 28. Status comes from the core, as values

Status: accepted

## Context

The console needed to show an agent's goal, current action, important memories and what
it has worked out, alongside the day, its body and its location. Only the body and
location are in a `WorldState`. Memory, knowledge and skills live on the agent, and ADR 2
says observers never touch live simulation objects.

## Decision

`Simulation.status()` assembles an `AgentStatus` per agent: the snapshot, the goal behind
its latest decision, that decision, how much of any job is left, its most relevant
memories, what it has tested, its skills and its coverage of the world. Every field is an
immutable value. The runner's `StatusReport` asks for it on a schedule of simulated time
and prints a block. Asking reads only, and a test holds it to that: a run that is asked
for its status every tick saves identically to one that is never asked.

"Important memories" are `MemoryStore.highlights`: recall's own relevance ranking with
the routine left out. How full a bush was an hour ago, and actions that went fine, are
most of what memory holds and nothing a reader wants to see.

Verbosity is one `--log-level` flag, each level a superset of the one below:

| Level | Shows | 10 days |
| --- | --- | --- |
| `quiet` | first discoveries, lessons, danger, death | 20 lines |
| `normal` | every event, a status block per simulated day | ~200 lines |
| `verbose` | status four times a day, every completed action | ~1,900 lines |
| `debug` | every perception, want, option and score; core diagnostics | ~10,500 lines |

The level decides which observers are attached. It also sets the core's own logger, whose
few messages mostly repeat events, so it stays at WARN unless asked. `--trace` remains, as
another name for `debug`.

## Consequences

- The status row says **doing** only while a multi-tick job is under way, and **last
  act** otherwise. The report prints at the end of a tick, when the last decision has
  already been carried out, so "doing" would have described something finished.
- The runner now compiles against Logback rather than only running with it, because it
  sets log levels from the command line. That coupling sits in the runner, which already
  owns presentation, and the core still depends only on SLF4J.
