# 17. Deciding is propose, evaluate, select

Status: accepted

## Context

The obvious decision engine is a chain of conditions: if thirsty and water is near, go to
water; else if hungry and carrying food, eat; else if tired, rest. It works for three
behaviours and collapses at five, because every new action has to be woven into every
existing branch, the priorities are implicit in the order of the tests, and there is
nowhere for learning to attach.

## Decision

The decision layer is three collaborators and nothing else:

- **proposers** say what is possible. One per kind of thing an agent might do, each
  reading only a `Perception`. Adding a behaviour is adding a proposer to a list.
- **an evaluator** scores each candidate against what the agent wants.
- **a selector** takes one.

`DeliberativeDecisionMaker` holds the three and has no logic of its own beyond the order
it calls them in. There is no branch anywhere saying "if thirsty, head for water". Thirst
beating curiosity is arithmetic.

The join between wanting and doing lives on `ActionCandidate`: the proposer that builds it
declares which goal it serves. That is the only place in the system that knows drinking
relieves thirst. Goals never learn that actions exist, actions never learn that wanting
does, and the evaluator never inspects what kind of action it is scoring — everything it
needs is on the candidate, so there is no switch over action types anywhere.

## Consequences

- `ActionEvaluator` and `ActionSelector` are the seams the brief asked for. A learned
  scorer or a planner's cost estimate replaces the evaluator; an explore-sometimes policy
  replaces the selector; neither disturbs the proposers.
- Travelling works without any notion of a plan. An approach candidate is valued at
  whatever waits at the end of it, discounted by the walk. On arrival the walk becomes
  impossible and the drink is proposed undiscounted, so the handover needs no state.
- Behaviour is now a consequence of six numbers rather than of code. Tuning an agent means
  tuning `ActionSettings` and the evaluator's constants, and the tests assert behaviour
  (drinks, gathers, rests) rather than arithmetic.
