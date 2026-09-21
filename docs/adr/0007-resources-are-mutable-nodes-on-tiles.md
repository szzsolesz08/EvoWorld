# 7. Resources are mutable nodes on tiles

Status: accepted

## Context

The world needs berries, water, wood and stone that an agent can find, use up and come
back to later. Two questions had to be settled: whether a resource is a value or an
entity, and how much of the world a tile can hold.

## Decision

A `ResourceNode` is an **entity**, mutable through exactly two operations — `harvest` and
`regenerate` — in the same way `Agent` is. A bush that is picked is the same bush, not a
replacement bush, and an agent that remembers "there were berries here" is remembering an
identity. Everything leaving the world is an immutable `ResourceSnapshot`.

Quantities are fractional. Regrowth of a quarter berry per tick has to accumulate
somewhere; rounding it to whole units would mean a slow-growing resource never recovered
at all.

A tile holds at most one node, enforced by `World.addResource`. That makes "what is here?"
one map lookup, and nothing yet needs a bush and a stream on the same square.

The type's numbers — capacity and regeneration rate — live on `ResourceType` rather than in
`SimulationConfig`, because they are what the type *means*: a berry bush that held a
thousand berries and regrew instantly would not be a berry bush. They move into
configuration if a run ever needs to tune them, which nothing does yet.

## Consequences

- Regeneration is what separates a resource you can live off from one you can only use up.
  Berries return within a day, wood over several, water almost immediately, stone never.
  That distinction, not the quantities, is what will make resource choice interesting.
- `harvest` returns what was actually obtained rather than throwing when a node is empty.
  Finding out the hard way that a bush is bare is an experience, and experiences are what
  later milestones will record.
- Resources live in a `LinkedHashMap`, not a `HashMap`. Iteration order has to be
  insertion order or regeneration order would depend on hash codes and the run would stop
  being reproducible.
- The one-node-per-tile rule is the first thing to revisit if terrain arrives, since
  terrain is a property of a tile rather than a thing standing on it.
