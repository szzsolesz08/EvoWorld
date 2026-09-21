# 16. Urgency accelerates, curiosity does not

Status: accepted

## Context

Four wants have to be comparable on one scale, and the shape of each curve decides how the
agent behaves long before any action selection exists. Linear urgency would leave an agent
at death's door only twice as motivated as a comfortable one, and every want would matter
a little all the time.

## Decision

The three need-driven goals square their pressure: `(need/100)²`. Mild thirst is a
background murmur, severe thirst drowns out everything. Hunger, thirst and tiredness use
the identical curve, so they are comparable by construction — thirst still tends to win a
long run, but because the body dehydrates faster than it starves, not because this layer
says it matters more.

`ExploreGoal` is deliberately the odd one out: flat curiosity scaled by available energy,
answering no need and relieving no pressure.

## Consequences

- Exploring wins by default and loses once a need grows past it. The crossover is at
  `√0.45 ≈ 67` on the need scale — a consequence of where the curves meet, not a threshold
  anyone wrote down. There is a test asserting exactly that, which would fail loudly if
  someone later "fixed" it with a rule.
- An agent tolerates being two-thirds hungry before food outranks wandering. That is the
  intended reading of "urgency accelerates", and it is a balance dial: change the curiosity
  constant and the crossover moves, with no other code touched.
- Full need reaches priority 1.0 exactly where health starts draining, so maximum want and
  real danger coincide without a separate mechanism coupling them.
- Registration order breaks ties, so identical wants resolve as survival, then comfort,
  then curiosity. The sort is stable, which is what makes that a guarantee rather than an
  accident.
