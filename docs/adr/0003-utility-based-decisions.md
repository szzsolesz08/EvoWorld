# 3. Decisions are scored, not scripted

Status: accepted

## Context

The project explicitly rejects the scripted NPC — `if hungry then find berries`. That
shape does not extend: every new need multiplies the branches, priorities become implicit
in the order of the ifs, and there is nowhere for learning to attach.

## Decision

Every tick the decision maker produces a list of candidate `Decision`s, each a
`(action, utility, rationale)` triple scored from the same `Perception`. The highest
utility wins. Ties go to the earlier candidate, so the outcome is deterministic.

`deliberate(Perception)` returns a `Deliberation` holding both the candidates and the
winner. Scoring therefore happens exactly once per tick — an earlier split into separate
"consider" and "decide" calls drew fresh random numbers each time, so the logged
alternatives were not the ones actually chosen.

## Consequences

- New behaviour is a new candidate, not a new branch in a decision tree.
- The losing options are available to observers, which is what makes behaviour
  explainable in the log rather than mysterious.
- Utility functions are pure functions of perception, so they are directly unit testable
  and the choice under any given internal state can be asserted.
- Learning has an obvious attachment point later: adjust the weights that produce
  utilities, leaving the loop unchanged.
