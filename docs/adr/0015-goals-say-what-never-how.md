# 15. Goals say what, never how

Status: accepted

## Context

The obvious way to give an agent motives is to write them as behaviour: "if hungry, go to
the nearest berries". That collapses two questions into one — what the agent wants, and
what it should do about it — and the collapse is where scripted NPCs come from. Once
wanting and doing are the same code, a new action means editing every motive that might
use it, and learning has nothing to attach to.

## Decision

A `Goal` reports one number: how badly the agent wants it, given a `Perception`. Nothing
in the goal package knows what actions exist, names a location, or decides anything.
"Satisfy thirst" is a goal; "walk to the spring and drink" is a plan, and plans belong to
a layer that does not exist yet.

`MotivationSystem` scores every goal every tick and returns a `Motivation` — the full
ranking plus the dominant want. The losers are kept for the same reason `Deliberation`
keeps rejected options: an agent that is slightly hungry and desperately thirsty is in a
different situation from one that is only thirsty, and a log showing one number hides how
close the race was.

`Goal.assess` is final and clamps into [0, 1], so no goal can shout its way to the front
by returning a bigger number. Priorities are comparable because the scale is enforced
rather than agreed.

## Consequences

- Behaviour is not written down anywhere. There is a thirst that grows, a want that grows
  with it, and — next milestone — actions competing to serve whatever is wanted most.
- Goals are pure functions of perception, so a state maps to a ranking with no hidden
  inputs, and a test can assert the whole motivation from a `Needs` value.
- This milestone deliberately changes no behaviour. Motivation is assessed and reported to
  observers, and the decision layer still ignores it entirely: the run before and after is
  byte-identical. Wants became visible before anything started obeying them.
- Health has no goal. Nothing can heal, so a "recover health" goal would be a want no
  action could serve. It arrives with the first thing that mends an agent.
