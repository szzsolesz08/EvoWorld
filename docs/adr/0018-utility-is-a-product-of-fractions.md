# 18. Utility is a product of fractions

Status: accepted

## Context

Six factors have to combine into one comparable number: usefulness for the current goal,
expected benefit, distance, energy cost, risk and how much the agent actually knows.
Adding weighted terms is the usual approach and needs weights nobody can justify, and lets
a high score on one term paper over a fatal zero on another.

## Decision

Every factor is a fraction in [0, 1] and they multiply:

```
utility = want x benefit x confidence x timeliness x affordability x (1 - risk)
```

Multiplication gives the right semantics for free: any factor at zero kills the candidate
outright. No want, no point. No energy, no chance. Certain death, no thank you.

- **timeliness** is hyperbolic in ticks-to-benefit, `1/(1 + ticks/4)`, so the first few
  steps cost the most — which is what makes a close mediocre option beat a distant perfect
  one.
- **affordability** is what is left after the energy cost as a share of energy in hand, so
  the same trip is cheap when fresh and prohibitive when spent.
- **risk** projects the needs forward to when the benefit would land and charges whatever
  damage they would be doing by then, as a share of remaining health. An agent already
  dying will not walk fifteen steps for a snack.
- **confidence** is full for anything perceived and half for unseen ground. Today that is
  the only distinction there is to make; it is the hook remembered knowledge attaches to.

## Consequences

- Utilities are small numbers — a good option scores around 0.2 — because six fractions
  multiply down. Only the ordering matters, and the ordering is stable.
- The agent is **greedy**, with no lookahead. Standing in a spring with mild thirst it will
  walk away rather than top up, because a small relief now scores below curiosity and
  nothing models "this saves a walk later". That is a real limitation of scoring without
  planning, and it is visible in the test suite rather than hidden.
- Survival is emergent, not guaranteed. Across ten seeds, seven agents live past 2000 ticks
  (83 simulated days) and three die — of thirst, having wandered somewhere with no water
  inside a six-step radius. Nothing remembers where the springs were, so an agent that
  loses them has to get lucky. That is precisely what the memory milestone is for.
