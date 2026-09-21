# 21. Knowledge is general, memory is particular

Status: accepted

## Context

Memory and knowledge look like the same thing until you ask what fades. "I found berries
at (4,3) an hour ago" gets less reliable every tick — the bush may be picked clean by now.
"Berries are worth about eight hunger a unit" does not get less true while you are not
looking at it.

Keeping both in one store would force one decay rule on two things that decay differently,
and an agent that had not seen a spring for a week would become unsure that water quenches
thirst.

## Decision

Two systems, one event.

| | Memory | Knowledge |
| --- | --- | --- |
| Holds | one episode, at a place, at a time | one general claim |
| Example | "I found berries at (4,3)" | "berries are worth 8 hunger a unit" |
| Over time | confidence halves | confidence grows |
| Bounded | yes, 48 items, forgets the least valuable | no, one belief per claim |
| Keyed by | kind + place + subject | kind + subject |

Every action outcome goes to both. `MemoryRecorder` files what happened and where;
`KnowledgeLearner` works out what it implied in general. Neither knows about the other.

Knowledge is immutable, like `Inventory`: learning produces a new body of knowledge and
the agent replaces what it had, so what it believed before a meal is still there to
compare against — which is exactly what the tests do.

## Consequences

- An agent can be unsure the spring is still there and certain that water quenches thirst
  at the same time, which is the state of knowledge that makes going to look worthwhile.
- Belief in a *place* (memory) and belief in a *kind of place* (`LOCATION_RELIABILITY`)
  compound: a remembered spring's confidence is how well it is remembered times how often
  such places turn out to still hold anything. Two different doubts, multiplied.
- Knowledge is unbounded because the number of claims is bounded by the number of resource
  types. If agents ever learn about individual things rather than kinds, this needs the
  same treatment memory got.
