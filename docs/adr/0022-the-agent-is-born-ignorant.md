# 22. The agent is born ignorant

Status: accepted

## Context

Before this milestone the proposers read `ResourceType.isEdible()` and
`nourishmentPerUnit()` directly. The agent was therefore *born* knowing what berries do.
A knowledge system bolted on top of that would have been decoration: there would have been
nothing left for it to learn, and no way to tell whether it worked.

## Decision

The decision layer no longer consults `ResourceType` for value at all. What a berry is
actually worth stays in `ResourceType`, known to `EatAction` when it runs — that is
physics. What the agent *expects* it to be worth comes from `Knowledge`. The two can
disagree, and that disagreement is what it means for an agent to be wrong about something.

A brand-new agent holds a **prior**: consumable things probably help, by roughly the
amount such things usually help, held at a confidence of 0.5 — the same confidence as
exploring, because walking over to try something you have never tried *is* exploring.

Learning is four rules, each a sentence:

- ate something and the hunger moved — divide relief by units consumed, that is the value
- drank something and the thirst moved — the same arithmetic
- told permanently that a thing is not food — it is worth nothing, settled in one go
- went somewhere for a resource and it was not there — such places are less reliable

Estimates are a running mean; confidence is `n/(n+3)`. No model, just counting.

## Consequences

- The agent tries things. In an ordinary run it attempts to **drink berries** around tick
  38 and **eat water** around tick 49, is refused, and never repeats either mistake. That
  is the system working, not a bug.
- Ignorance has a real cost. Survival across ten seeds went from 10/10 to **9/10**: one
  agent does not live long enough to learn what it needed. That is an honest price for
  removing innate knowledge, and it is visible rather than tuned away.
- The prior has to be roughly the right *size*. A prior too cautious to ever win means an
  agent dies of thirst beside a spring it never tried — which is exactly what happened at
  first, and is why the untested confidence is pinned to the exploration constant rather
  than picked independently.
- Learned values converge slightly **below** the truth: after 51 meals an agent estimates
  berries at 7.8 against a real 8.0, because relief is capped by the hunger it answers and
  eating while nearly full reads as a smaller benefit. A sampling bias, correctly
  reflecting what the agent actually experienced.
