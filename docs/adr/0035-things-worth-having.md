# 35. Things worth having

Status: accepted. This extends ADR 15 (knowledge), ADR 33 (a body in real time) and
ADR 34 (a world without edges).

## Context

The survey said Atlas spent about two hours of every simulated day walking to water, and
in the walled world three and a half. He could not do otherwise: `DrinkAction` drank from
a node underfoot, and its javadoc said so plainly - "food is carried, water is somewhere
you have to be". Water could be *gathered* into the pack, but nothing could ever drink it
again, so carried water was dead weight.

The milestone asked for carrying, containers, tools and crafting - but not as features.
The point was to give the agent the physical means to solve a problem it meets over and
over, and to let it find out through the ordinary perception → decision → consequence →
learning loop that the solution is worth having.

## Decision

**An item is a thing you have; a resource is a thing you measure out.** `ItemKind` has
three constants and each earns its place by changing what the agent can do: it either
*holds* something, which is what lets a resource be carried at all, or it *works on*
something, which makes gathering that thing cheaper and richer. `Item` carries how much
life is left in it; `Belongings` is what the agent has on it, and takes room in the pack.

**A container is not a place water goes, it is the reason water can be carried.**
`ResourceType.needsContainer()` is true of water, and `AgentSnapshot.roomFor` - the single
rule for "how much of this could I take" - gives it room only from a container. So
drinking from the pack is the exact mirror of eating from it (`DrinkAction.fromPack`), and
`DrinkProposer` offers both on the same measure. Nothing anywhere says "if thirsty and
carrying water, drink it"; the evaluator weighs a sip in hand against a walk to a spring
the way it weighs everything else.

**One new want: `StayProvisionedGoal`.** Every other goal answers a need the body feels
now, and that is exactly why none of them could ever fill a flask: water in a skin
relieves no thirst while you are standing in a spring. This one says "I would rather have
a day's water on me than not", the way curiosity says "I would rather see somewhere new" -
a drive, not a plan. It knows nothing of flasks, springs or distances. What answers it is
left to the proposers: filling a container (`StockUpProposer`), making one
(`CraftProposer`), and fetching what making one needs (`FetchMaterialsProposer`).

**Recipes are known; what a thing is worth is not.** Three recipes - a flask from wood, an
axe and a pick from wood and stone - as data, not conditionals. Knowing how to lash a
stone to a stick is a skill of the hands; an agent that had to discover the *idea* of an
axe would never try one. What is not given is the belief that any of it is worth the
trouble: `BeliefKind.USEFULNESS` starts as a hunch and is settled by ownership. A drink
out of the pack is evidence a flask is worth having, and the same `Knowledge`, `Belief`
and `KnowledgeLearner` that learn about berries learn about flasks - the belief store
already took string subjects.

**Tools compound with skill rather than replacing it.** A gather costs
`energy x skill x tool` and yields `amount x tool`, and using a tool wears it.

Format version 6 saves the kit and what is in it.

## What the measurements found

Nine survey runs, and almost every one of them changed the design rather than a constant.

1. **Blocking an action must not block the learning that comes from trying it.** The
   moment water needed a container, an agent could no longer taste water - and so could
   never find out it was not food. It walked between a spring and a patch of ground for
   the rest of its life hoping for a meal. `EatAction.fromGround` restores the experiment
   for things that cannot be picked up. Offering it for *everything* was worse again: the
   agent nibbled at the ground instead of eating properly, and free time fell from 51 to
   34 per cent.
2. **A gather must never take more from the world than the agent can hold**, or the
   difference simply vanishes. This was a conservation bug the container rule exposed.
3. **Measure the supply, not the capacity.** A want defined as "how full is what I can
   carry" makes an empty second flask look like a worse situation than a single full one,
   so Atlas made flask after flask and was never satisfied. Measured against a day's
   water, a full flask answers the want and a second one answers nothing.
4. **Providing for tomorrow must go quiet when today is wrong.** At a third of a full
   want, provisioning outbid hunger often enough that Atlas fetched wood while starving
   and died with a full flask on his belt - fourteen of twenty runs at hour-long ticks.
   Multiplying by the same `calm` curiosity already uses fixed it.
5. **A speculative errand deserves a speculative confidence.** Fetching wood was offered
   with the certainty of a meal; tying it to the belief about the thing it is for, and
   capping detours at six steps, was the difference between a starving forager and a
   working one.
6. **Junk is what is good for nothing, not what you cannot eat.** Water is not food, so
   the first junk rule had Atlas throwing away his own water.
7. **A flask that wears out per fill is a treadmill.** At twenty-five fills the walled
   world burned through a flask a week; the wood for it crowded out everything else.

## Consequences

Measured over 20 seeds and 20 simulated days, against the same code with the want switched
off, so the only difference is whether Atlas provides for tomorrow:

| | water walk h/day before | after | free time before | after |
| --- | ---: | ---: | ---: | ---: |
| baseline | 2.03 | **0.87** | 47% | **51%** |
| wounded | 2.05 | **0.88** | 47% | **51%** |
| hungry-world | 2.93 | **1.55** | 40% | **46%** |
| sparse | 2.10 | **1.37** | 47% | **50%** |
| walled | 3.36 | **0.57** | 41% | **51%** |
| hourly | 3.00 | **2.37** | 23% | **25%** |

120 runs of 120 survive, with no deaths and no close calls at all in the baseline, wounded
and walled worlds. Atlas makes about one flask a run in the open world and three in the
walled one, and ends believing a flask is worth having at around 90 per cent confidence.

**Tools are built, usable and never made.** An axe halves the effort of a gather and takes
half again as much per swing, and a test proves it; but wood is wanted only for the
occasional flask, energy is restored by sleeping, and so the arithmetic says an axe is not
worth the wood and the stone. That is the honest answer for this world rather than a
disappointment to be tuned away - and it is a clear signal about what would make tools
matter: work that must be done often, or materials that cannot be had without them.

The cost of all this is that Atlas is more homebound: he covers 22 chunks in twenty days
where he used to cover 21 in the baseline but only 14 in the middle of the milestone, and
the small worlds show more turn-backs. Nothing dithers itself to death, and the two
dithering runs the baseline now reports were zero before.
