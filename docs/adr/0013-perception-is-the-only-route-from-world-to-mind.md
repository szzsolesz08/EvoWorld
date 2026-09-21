# 13. Perception is the only route from the world to the mind

Status: accepted

## Context

Until now `Perception` carried a whole `WorldState`. It was immutable, so nothing could be
broken through it, but it was omniscient: a decision maker could consult a berry bush on
the far side of the map, and any behaviour built on that would have been cheating without
anyone noticing. A simulation about agents that have to *find* things cannot hand them the
answer.

## Decision

`Perception` no longer holds a `WorldState`, a `World`, or any way to reach one. It is
built by a `PerceptionSystem` from the live world and contains only immutable values,
filtered to a radius. It carries three kinds of knowledge, kept distinct on purpose:

- **self** — the agent's own body and inventory, always known in full;
- **sensed** — resources and other agents within the radius, nothing beyond it;
- **known** — standing facts, today only the size of the world. This is where the memory
  and knowledge milestones will attach.

`DecisionMaker.deliberate` takes a `Perception` and nothing else. There is no longer a
path from the decision layer to the world at all, so "the agent cannot cheat" is a
property of what it is handed rather than of what it chooses to look at.

## Consequences

- Behaviour is now bounded by what an agent can actually sense, which is the precondition
  for memory meaning anything. Remembering where water was is only interesting if you
  cannot see it from here.
- Sensed things arrive as `PerceivedResource` and `PerceivedAgent`: a snapshot plus how
  far away and in which direction. The log reads "water 5 steps west" instead of a
  coordinate pair, which makes behaviour legible without a map.
- The radius is a tuning knob with real consequences. A small world and a large radius is
  omniscience with extra steps; the default of six in a sixteen-square world leaves most
  of the map unknown at any moment.
- Perception no longer snapshots the entire world every tick to throw most of it away,
  which was the previous implementation.
