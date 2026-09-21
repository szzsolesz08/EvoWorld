# 27. Saves are exact, and the save format is not the domain

Status: accepted

## Context

A simulation meant to run for weeks unattended has to survive being stopped. "Survive"
has a strict meaning here. A resumed run that only looked plausible would be a different
simulation that happened to start in a similar place, and every guarantee the project has
made about determinism would quietly stop applying across a restart.

Three things stood in the way:

- **The random generator could not be restored.** A seed plus a count of draws does not
  recover `java.util.Random`'s position: `nextInt(bound)` sometimes rejects a value and
  draws again, so replaying the count with different bounds lands elsewhere.
- **Some state existed only inside the running `Simulation`**: work in progress, which
  dangers were already active, how hard each agent worked last tick, whether arrivals had
  been announced. All of it changes what the next tick does.
- **Serializing domain classes directly would couple the file to the code.** Records like
  `Needs` have derived accessors (`isStarving`) a serializer would write out, and a
  refactor of any domain class would silently change what an old save means.

## Decision

**The generator is written out.** `SimulationRandom` now implements the linear
congruential generator specified for `java.util.Random` itself: same multiplier, increment
and rejection loop. Its whole state is a single 48-bit `long`, saved and restored exactly.
A test compares it against `java.util.Random` over 40,400 draws, 101 seeds and nine
bounds, including the huge non-power-of-two bounds where the rejection loop actually runs.

**The save format is its own set of records**, in `core.save`, holding only numbers,
strings, enums and each other. `SaveFormat` is the single class that knows both the
domain and the format, and the only switch over every action kind. `Simulation.save()`
adds the simulation's private state; `Simulation.load()` rebuilds it. The format carries a
version number, and a mismatched version is refused rather than misread.

**Jackson lives in its own module**, `simulation-persistence`, which depends on the core
and turns a `SavedSimulation` into JSON and back. The core still has no I/O and no
Jackson. Reading is strict: an unknown field or a missing number is an error, because a
save that loaded with a silent zero where the agent's health should be would be worse than
one that refused to load. Writing goes to a temporary file and is moved into place, so a
run killed mid-save keeps its previous save.

**Not saved, deliberately:** goals, which are recomputed from needs at every decision and
would come out the same; the standard decision maker, which is stateless and is rebuilt
from the configuration; and observers. The latest want and decision kept for the status
display are not saved either. A resumed run shows "(not decided yet)" until the agent's
next decision, which changes nothing it does. A scripted run cannot be saved at all: the
script's place in its list is state the format doesn't hold, and the runner refuses rather
than resuming a different run.

## Consequences

- Save, load and continue is identical to never stopping, not merely similar. Tested at
  nine save points, including mid-way through a two-tick gather. Also tested through real
  JSON files for four seeds, and as a ten-leg relay of save and reload that ends
  byte-identical to an unbroken run.
- Through the command line, a 20-day run and a 10+10 run split across two separate
  processes produce byte-identical save files.
- Save files are byte-stable: the same state always writes the same text. That needed one
  fix. The JDK's immutable sets iterate in an order randomised per JVM, so coverage
  records hold ordered lists instead.
- Adding an action kind now means adding it to `SaveFormat` too. Forgetting fails loudly
  the first time a simulation using it is saved, not silently on load.
