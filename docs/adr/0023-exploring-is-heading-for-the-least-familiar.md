# 23. Exploring is heading for the least familiar

Status: accepted

## Context

Until now `ExploreProposer` picked a tile at random. That is wandering: an agent could
cross the same ground a dozen times and never reach a corner, and nothing it did could be
called finding out.

## Decision

An agent carries an `ExplorationMap`: how recently it has stood in each 4×4 region of the
world. It is a third kind of standing state, next to memory (episodes, which fade and get
forgotten) and knowledge (general claims, which sharpen). This one holds **coverage**, and
it is bounded by the map rather than by a capacity, because a sixteen-square world only has
sixteen regions.

Familiarity halves every 500 ticks. `ExploreProposer` offers the few least familiar
regions, each valued at `1 − familiarity` and discounted by distance like everything else.
Nothing marks a region "unexplored": the frontier is simply the regions with the smallest
numbers.

`ExploreGoal` now scales with three things: spare energy, how much of the world the agent
has lost track of, and how free it is from pressing needs,
`calm = 1 − (most pressing need)²`.

A **discovery** is the first sighting of a kind of thing, or of a known thing in a region
where the agent hasn't found it before. It is recorded as a `DISCOVERY` memory (half-life
2000), associated with its region in the exploration map, and reported to observers.

## Consequences

- Left alone in an empty world, an agent covers all sixteen regions. There is a test for
  it.
- Rounds of exploration are emergent. A swept map makes curiosity fall to its floor;
  familiarity fades; curiosity returns; the agent goes round again. Nothing schedules it.
- Nobody wrote a list of discoveries. They come from comparing what is in view with what
  the agent has already seen, so what can be found is whatever the world happens to hold.
  A test checks that every discovery corresponds to something really at that tile.
- The random stream is now drawn only by world generation and the fallback wander. In an
  empty world an explorer's route is fully determined by its coverage, so seeds only show
  through the worlds they generate. Two tests had to move to generated worlds to see a seed
  difference at all.
- `calm` fixed a real flaw. Without it, curiosity stayed just as strong in an agent dying
  of thirst, and an untried rock underfoot outscored a known spring three steps away.
