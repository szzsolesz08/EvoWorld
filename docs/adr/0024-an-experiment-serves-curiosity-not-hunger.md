# 24. An experiment serves curiosity, not hunger

Status: accepted

## Context

Last milestone's agent already experimented in one sense: an untested thing carried a weak
prior, so a hungry agent would sometimes try it. But that made every experiment a meal.
There was no way for a comfortable agent to try something *because it did not know what it
was*, which is what investigation means.

## Decision

`InvestigateProposer` offers to eat or drink something the agent has never tried, and the
candidate serves `EXPLORE`, not hunger or thirst. Its reward is what is left to find out,
`1 − confidence`, so a complete unknown is worth the most and a tested thing is never
offered. The risk is the ordinary one the evaluator already charges: a tick, a mouthful, and
whatever the agent's needs would do to it in the meantime. Because curiosity is silenced by
pressing needs (ADR 23), a desperate agent does not experiment. There is no special case
anywhere that says so.

Two supporting changes were needed to make experiments actually teach:

- **Permanent facts come first.** Eat and Drink now check "this is not food / not a drink"
  *before* "not hungry / not thirsty". Otherwise a comfortable agent tasting a rock was told
  "not thirsty", learned nothing, and re-proposed the same taste every tick.
- **Only experiments that can show something are offered.** Drinking when not thirsty
  relieves nothing and would read as "water is worth nothing", teaching a falsehood.

## Consequences

- The full chain runs in the real simulation and is asserted end to end: perception →
  uncertainty → curiosity → experiment → permanent refusal → failed-action memory → belief
  that stone is worth nothing → a different next decision.
- **Experiments leave junk behind.** Sampling wood and stone to see if they are food fills
  the bag. One seed starved one step from a bush carrying 5 water, 10 wood and 5 stone. A
  `DiscardAction` now lets an agent put down what it has *tested* and found useless, valued
  at exactly the room it would make that is actually needed. Untested things are never
  thrown away before the agent finds out what they are.
- A dead end worth recording: I first tried shrinking untested samples to a single unit. It
  collapsed survival to 0/10, because haul size and benefit are coupled and a one-berry
  sample is scored as worth one berry, so untested food never won until too late. Reverted;
  discard handles the junk instead.
- Survival stays at 9/10. The remaining death (seed 5) is not about exploration. The agent
  **dithers** between two bushes in opposite directions, each step making the other the
  nearer target, until it starves between them. That is per-tick greedy choice without
  commitment, which is what the persistent-intent milestone is for.
