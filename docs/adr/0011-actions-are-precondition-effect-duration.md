# 11. An action is a precondition, an effect, and a duration

Status: accepted

## Context

Before this milestone an action was a single `perform` method that did whatever it liked
and reported whatever it wanted. That works for two actions and stops working the moment
an action can fail: every new action has to remember to check its own requirements first,
to leave nothing half-changed when it cannot proceed, and to describe honestly what it
did. Three things to remember, in every action anyone ever adds.

## Decision

`Action` is an abstract class with three parts and one entry point:

- `precondition(agent, world)` — why this cannot be done right now, or empty. Must not
  change anything.
- `effect(agent, world)` — what happens, run only when the precondition holds. Returns a
  description, not a status.
- `durationTicks()` — how many ticks it occupies the agent.

`execute` is **final**. It checks the precondition, applies the effect only if it passes,
and captures the agent before and after either way. A subclass cannot skip its own check,
invent its own status, or misreport what changed, because none of those are its job.

`ActionOutcome` is structured rather than a log line: a status, a human-readable detail,
and an `AgentChange` holding before and after snapshots. Its constructor rejects a
`BLOCKED` outcome that carries a change, so the type itself enforces "a blocked action
changes nothing".

## Consequences

- "Actions cannot create invalid world states" is a property of the base class and of one
  constructor check, not a rule five actions each have to honour. There is a test that
  runs every action in a state where each is blocked and asserts the agent is byte-for-byte
  unchanged.
- Actions became classes rather than records, which costs an explicit `equals`/`hashCode`
  each. Worth it: `Deliberation` compares decisions, so value equality is needed, and the
  final `execute` is the whole point.
- The outcome records what *actually* happened, so a gather that asked for nine and found
  one reports one. That is exactly what the experience and memory milestones will read.
- Failure is information, not an error. `BLOCKED` is a normal result an agent can learn
  from, which is why nothing throws.
