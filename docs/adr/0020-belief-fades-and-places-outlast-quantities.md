# 20. Belief fades, and places outlast quantities

Status: accepted

## Context

A memory that stays perfectly true forever is omniscience with a delay. A memory that
expires at a fixed age throws away information that was still worth something. Neither
matches what memory is for here, which is letting an agent act on an old observation while
knowing it might be wrong.

## Decision

Confidence decays by halving. A memory's confidence at any tick is
`initial × 0.5^(age / halfLife)`, and the half-life belongs to the kind of memory.

The important split is between **where a thing is** and **how much of it there was**:

| Kind | Half-life | Why |
| --- | --- | --- |
| `RESOURCE_LOCATION` | 4000 ticks | a spring does not wander off |
| `OBSERVED_RESOURCE` | 200 ticks | but it does empty, and refill |
| `SUCCESSFUL_ACTION` / `FAILED_ACTION` | 600 ticks | what worked here, for a while |
| `DANGER` | 1500 ticks | you remember nearly dying |
| `WORLD_EVENT` | 800 ticks | something changed |

An agent therefore ends up **sure where the water is and unsure whether there is any
left** — which is precisely the state of knowledge that makes going to look worthwhile.

The decayed confidence goes straight into the utility formula as the `confidence` factor
that has been sitting there since the decision milestone. A half-forgotten spring is worth
half a remembered one, and both are worth less than water the agent can see.

## Consequences

- Memory made survival reliable rather than lucky. Across ten seeds, agents surviving 2000
  ticks went from **seven** to **ten**; the three that died all died of thirst, having
  wandered out of sight of water with no way to find it again.
- Nothing decides that a belief is "good enough" to act on. There is no confidence
  threshold anywhere — a faded memory simply scores lower and loses to better options,
  which is the same mechanism everything else in the decision layer uses.
- Halving never reaches zero, so an ancient memory of a spring still beats no memory at
  all. The relevance floor on a query, not the decay, is what stops an agent chasing
  ghosts.
- `RememberedResource` is a separate type from `PerceivedResource` on purpose: one is a
  fact and the other is a belief, and a perception that blurred them would let a belief be
  acted on as though it were a sighting.
