# 34. A world without edges

Status: accepted. This supersedes the fixed 16x16 world of ADR 4, and extends ADR 6 (named
streams) and ADR 15 (the exploration map).

## Context

The world was one 16x16 square, scattered with resources at startup and walled at the
edges. Atlas had seen all of it within a day or two, after which "explore" meant re-walking
known ground: the survey showed him with about ten idle hours a day and nowhere new to go.
Every part of the map was also the same part - a uniform sprinkle - so there was nothing to
prefer, nothing to remember and nothing to come back to.

The user asked for an effectively infinite world in 64x64 chunks, generated only when Atlas
walks into one, laid out differently everywhere but stable: a chunk he returns to is the
chunk he left, the same seed always builds the same world, and a different seed builds a
different one. They also asked that Atlas **learn what the country is like** - that a
berry-rich field or a stand of wood be something he remembers and uses later - that the
viewer show a chunk at a time rather than following him about, that chunks vary but are
never waterless, and that finite worlds stay available.

## Decision

**A chunk is a pure function of the seed and its own coordinates.** `Chunk(column, row)`
with `SIZE = 64`; `ChunkedTerrain` draws each chunk from its own named stream,
`SimulationRandom.seededWith(seed, "chunk 3,-2")` (ADR 6). So a chunk depends on nothing
else: not on which chunks were built before it, not on how long Atlas took to get there,
not on anything he did. Walk away and come back and it is the same chunk.

**The world is what has been met.** `World` holds a `Map<Chunk, ChunkState>` and generates
a chunk the moment an agent comes near enough to see into it (`ensureAround`, called before
perception). `resourcesNear` reads only the chunks in range, so perception no longer scans
a world that has no end.

**Ground left behind catches up.** Only chunks near an agent regrow tick by tick; a chunk
that has been away catches up in one step from the tick it was last brought up to date. The
result is identical to regrowing everything every tick - a bush picked bare and left for a
week is full when you return - without walking every node in a world thousands of chunks
wide.

**Coordinates run both ways, to a limit.** `WorldBounds` became corners, with
`LIMIT = 1 << 28` in each direction: far enough to be endless in practice, near enough that
the distance between any two places still fits in an `int`, which is what every distance in
the simulation is measured in. `WorldBounds.of(width, height)` still builds a walled world,
and the `walled` survey scenario keeps that path exercised.

**Sight and memory are windows, not maps.** Perception reaches 12 tiles (about two hours'
walk). The exploration map's familiarity, frontier and unexplored fraction are computed
over the regions within a window around the agent, not over a world that can never be
finished. Discoveries are per **chunk**: in an endless world, "berries in a new part of the
world" every four tiles is chatter, not news.

**Atlas remembers what country was like.** A sighting keeps, per resource type, the most he
saw at once in a region and when - `abundance`, faded like familiarity. `HeadForPlentyProposer`
offers a trip to a remembered rich region for the need it serves. The difference between it
and a remembered bush is the difference between "there was a bush over there" and "that
whole slope was berries": one is a meal, the other is worth the walk again tomorrow. Wood
and stone are recorded from the start, ready for crafting to want them. Somewhere he has
never been offers nothing, however rich it is: this is memory, not second sight.

**A save records the chunks met, not their contents.** Format version 5 writes which chunks
exist, each one's regrowth tick, and only the nodes that differ from what the terrain would
build. Saves stay small however far Atlas walks. Remembered abundance is saved with the
rest of his mind.

**The viewer shows the ground, not the agent.** The map is one chunk - the one Atlas is in -
and switches when he crosses into the next. It is built around a `ViewWindow(origin, across,
down)` from the start, because several agents and free scrolling are what it is for.

## What the measurements found

The survey drove every change below. Each is a finding, not a tuning knob.

1. **The same seed built a different world in a different process.** `ChunkMix` held its
   counts in a `Map.copyOf`, and the JDK's immutable maps iterate in an order they randomise
   once per JVM - so terrain laid its water down in a different order each run. It showed up
   as a survey that killed seeds 2 and 5 in one process and nobody in the next. Collections
   whose order is read must be `EnumMap`, `EnumSet` or `LinkedHashMap`; the codebase already
   said so for the inventory and the saved regions, and now says it where it counts.
2. **Water is the common thing, not the rare one.** Thirst comes about twice as often as
   hunger, so in a world you can walk out of, making water scarce is a slow death sentence:
   every death in the first endless survey was thirst. A chunk holds as much water as
   berries, and at least twelve springs whatever it rolls - enough that crossing a chunk
   brings water into view.
3. **Curiosity has legs.** A newborn walked 160 tiles past springs two steps away without
   ever trying one, because experiments were only offered for what was underfoot, and then
   met its first real thirst knowing nothing. `InvestigateProposer` now offers a walk to an
   untried kind of thing in view, valued at what there is to find out.
4. **The bush underfoot must be valued like the bush over there.** A trip was worth
   everything on the target bush; gathering was worth one armful. So from any bush another
   bush looked better, and Atlas shuttled between two of them until he died of thirst with
   his pack half full. Both are now worth what the node can do for the need - the rule
   `DrinkProposer` had followed since ADR 33, finally applied to food.
5. **Searching is not curiosity.** Curiosity is rightly silenced by a pressing need, but a
   starving agent with nothing to eat in sight, in its pack or in memory has exactly one
   thing it can do about its hunger: go and look. `GoLookingProposer` offers that journey
   under the goal of the need itself, so the ranking of goals is untouched.
6. **The frontier is the nearest unknown, not the farthest.** With every region beyond the
   frontier equally unknown, the map returned the far corner of the window every time - a
   walk so long the evaluator discounted curiosity to nothing, so a hurt agent never went at
   all. Least familiar first, and among equals the nearest.
7. **A meal is a sitting, not a mouthful.** Counting every bite made the same day score
   differently at ten minutes a tick and at sixty. Both the survey and the rhythm test now
   count sittings.

Twenty days, twenty seeds, six scenarios: 120 runs, none of them dead, none dithering, and
about 21 chunks of country - some 86,000 tiles - in a life. The walled scenario stays in the
one chunk it was given.

## Consequences

- The world is effectively unbounded, and costs only what has been walked.
- A run's world is reproducible from its seed alone, in any process, on any JVM.
- Country has character, and Atlas can tell one place from another and prefer one.
- Saves are small and stay small.
- Exploring never finishes, so curiosity has somewhere to go for the whole of a life.
- Anything that reads a collection in order must say so in its type. An immutable copy is
  not an ordered copy.
- The finite world is now a scenario rather than the default, and its tests say so.
