# EvoWorld

A headless, autonomous life simulation. You are not the player. You start a world and
watch an agent, Atlas, live in it: he explores, finds food and water, works out what
things are worth by trying them, remembers where he found them, gets better at what he
does, and carries on for weeks of simulated time without anyone touching anything.

```
[day 1 00:10] Atlas discovered first ever sight of water at (3,8)
[day 1 19:50] Atlas learned wood nourishment: 0.0 hunger per unit (0.25 sure after 1)
[day 1 19:50] Atlas is starving, and it is starting to hurt (health 98.5)
[day 1 21:10] Atlas learned berries nourishment: 8.0 hunger per unit (0.25 sure after 1)
[day 1 21:20] Atlas is no longer starving (health 86.5)
[day 2 01:40] Atlas has fully recovered (health 100.0)

-- day 3 00:00 -- Atlas ------------------------------------------------
   status     alive, age 2d 0h, health 100.0
   location   (6,5)
   needs      hunger 11.2, thirst 28.8, energy 81.2
   carrying   berries 3.0
   wants      satisfy thirst (0.08)
   last act   MOVE to (3,8), for satisfy thirst (recalls water 7 steps south-west, 11 ticks ago)
   intent     heading for (3,8) to satisfy thirst, 6 steps to go, set out 4 ticks ago
   remembers  found wood at (7,3); close call with hunger at (1,5); berries at (4,3);
              berries at (10,5); found berries at (4,3)
   knows      berries: not a drink, food, 8.0 a unit (73%);
              water: not food, drink, 12.0 a unit (63%); stone: not food, not a drink;
              wood: not food, not a drink
   skills     move skilled (46% less effort), gather competent (28% less effort)
   explored   16 of 16 regions
```

Nothing in that output was scripted. Atlas is born knowing nothing about berries. The
lines above are what he worked out on his own.

**Contents:** [Vision](#vision) · [MVP features](#mvp-features) ·
[Architecture](#architecture) · [The simulation loop](#the-simulation-loop) ·
[AI architecture](#ai-architecture) · [Persistence](#persistence) ·
[Running it](#running-it) · [Testing](#testing) ·
[Known limitations](#known-limitations) · [Future directions](#future-directions)

## Vision

EvoWorld is meant to grow into a living procedural world: agents, animals, resources,
weather, societies, economies, possibly several worlds. The long-term interest is in
behaviour that **emerges**: agents that perceive, want, decide, act, remember and learn,
rather than NPCs following a script.

The design principle that follows from that runs through the whole codebase: **nothing
says "if hungry, go to berries".** There is a hunger that grows, a want that grows with
it, a set of things the agent could do, and a score for each. Behaviour is whatever
wins. That is what lets new behaviour come from new options rather than new branches,
and what leaves room for learning.

The MVP is the smallest honest version of that: one world, one agent, four resources,
and the full pipeline from perception to learning, running unattended and reproducibly.

## MVP features

| Area | What exists |
| --- | --- |
| World | a seeded 16×16 world; berries, water, wood and stone; resources that regrow at their own rates (stone never does) |
| Time | discrete ticks of configurable simulated length (default 10 minutes); days and hours in all output |
| Body | hunger, thirst, energy and health; activity-dependent energy use; unmet needs that do damage; healing when looked after, faster at rest |
| Actions | move, gather, eat, drink, rest, discard; explicit preconditions, structured outcomes, multi-tick work |
| Perception | a six-step field of view; nothing beyond it is known except by memory |
| Goals | four wants (thirst, hunger, energy, curiosity) scored from the body, never from a rule |
| Decisions | propose, evaluate, select: six-factor utility, no if/else behaviour anywhere |
| Memory | six kinds of memory with decaying confidence, bounded, recalled by relevance |
| Knowledge | general beliefs learned from outcomes, distinct from memory; the agent starts ignorant |
| Exploration | a coverage map, frontier-seeking, discoveries, curiosity-driven experiments |
| Skills | practice at walking and gathering makes them cheaper |
| Events | a curated stream of the moments worth mentioning |
| Persistence | save, load and continue, exactly, as JSON |
| Console | events, periodic status blocks, four log levels, configurable speed |

Across twenty seeds, every agent survives 2,000 ticks (83 simulated days) with no
intervention, and none walks back and forth between two targets. The MVP lost seed 10
that way; [ADR 29](docs/adr/0029-an-agent-finishes-what-it-sets-out-to-do.md) tells how
that was fixed.

## Architecture

```
simulation-core          the simulation: world, agents, minds, the tick loop. No I/O, no rendering.
simulation-persistence   JSON save files, via Jackson. Depends on the core.
simulation-runner        the headless command-line application. Depends on both.
```

The core depends on SLF4J and nothing else. Jackson appears only in the persistence
module, and Logback only in the runner. A graphical viewer would be a fourth module
depending on the core, and the core would not need to change for it.

Four properties the design protects, each enforced by tests rather than by convention:

- **The core never depends on presentation.** Observers receive only immutable values:
  `WorldState` snapshots, events, `AgentStatus` reports. Nothing an observer holds leads
  back to anything that can change, so watching a run cannot alter it.
- **The mind cannot see the world.** Decisions are made on a `Perception`: a filtered,
  immutable observation with no route back to the live world. An agent cannot act on a
  berry bush it has no way of knowing about.
- **Runs are reproducible.** One seeded random stream, owned by the simulation, drives
  everything stochastic. Collections that affect behaviour iterate in insertion order,
  and ties break deterministically. The same seed produces the same run, byte for byte,
  on any JVM. World generation draws from its own named stream, so changing how the agent
  thinks never moves the berries.
- **Everything is explainable.** Every decision carries its reasons, every memory its
  timestamp and confidence, every belief the count behind it.

The reasoning behind each significant decision is recorded in [docs/adr](docs/adr), 28
records in all. They include the ones where a first attempt was wrong.

## The simulation loop

Every tick runs fifteen phases in a fixed order. `Simulation.tick()` is written to read
as this list:

| # | Phase | Where |
| --- | --- | --- |
| 1 | advance world time | `SimulationClock` |
| 2 | update the environment: resources regrow | `World` |
| 3 | update needs for the tick just lived | `Agent.live` |
| 4 | perceive the environment | `PerceptionSystem` |
| 5 | retrieve relevant memories | recalled into the `Perception` |
| 6 | update goals | `MotivationSystem` |
| 7 | generate candidate actions | `ActionProposer`s |
| 8 | evaluate them | `ActionEvaluator` |
| 9 | select one | `ActionSelector` |
| 10 | execute it | `Action.execute` |
| 11 | process the consequences | `ActionOutcome` |
| 12 | create memories | `MemoryRecorder` |
| 13 | update knowledge | `KnowledgeLearner` |
| 14 | update skills | `Skills` |
| 15 | report what was worth reporting | `SimulationEvent` |

The world and the body change **before** the mind looks at them, so an agent always
decides on the world as it now is. An agent whose needs killed it overnight does not get
to act in the morning. Actions that take several ticks (gathering takes two) occupy the
agent, whose body keeps paying for every tick. The action's precondition is checked
again when it completes, in case the world moved on.

Time is **simulated** time. The core contains no wall-clock time at all; how fast a run
goes is set in the runner and cannot affect what happens.

## AI architecture

No machine learning, no language model. Every mechanism is explicit arithmetic that can
be checked by hand.

### Perception

The only route from world to mind. A `Perception` holds three distinct kinds of
knowledge:

- **self:** the agent's own body and inventory, always known in full.
- **sensed:** resources and agents within six **steps** (walking distance, so "four steps
  north" is directly comparable to what walking there will cost).
- **known:** remembered places the agent can no longer see, with faded confidence;
  everything it has worked out; how much of the world it has covered.

### Goals

Five goals each score themselves from the agent's state. A goal says **what**, never
**how**. Nothing in the goal layer knows an action exists.

| Goal | Priority |
| --- | --- |
| satisfy thirst | `(thirst / 100)²` |
| satisfy hunger | `(hunger / 100)²` |
| recover energy | `(1 − energy / 100)²` |
| recover health | `(1 − health / 100)²` |
| explore | `0.6 × energy × unexplored fraction × (1 − most pressing need²)` |

Needs accelerate: mild thirst is a murmur, severe thirst drowns everything else.
Curiosity is what is left once survival has had its say, so a comfortable agent explores
and a dying one does not. Injury does *not* quiet curiosity. Exploring is how a newborn
finds food, and when injury was counted against curiosity, three wounded newborns in
twenty starved.

**Healing** is metabolism, not an action. Each tick a body that is looked after (hunger
and thirst at most 50, energy at least 25) regains 0.2 health, or 2.0 if it spent the
tick resting. Those conditions are far from anything that does damage, so no tick both
hurts and heals. Most recovery happens on its own, during rests taken for energy. The
health goal is a tie-break: when nothing else presses, a badly hurt agent rests rather
than wanders.

### Deciding: propose, evaluate, select

There is no decision tree. `DeliberativeDecisionMaker` runs three collaborators and has
no logic of its own:

- **Proposers** (one per kind of thing an agent might do) offer candidates, reading only
  the perception. Each candidate says which goal it serves. That is the only place in the
  system that knows drinking relieves thirst.
- **The evaluator** scores each candidate with a product of fractions:

  ```
  utility = want × benefit × confidence × timeliness × affordability × (1 − risk)
  ```

  Any factor at zero kills the candidate: no want, no point; no energy, no chance.
  *Confidence* is where knowledge and memory enter: how sure the agent is that the thing
  is there, times how sure it is that the thing is worth having. The first factor is
  full for something seen and faded for something remembered. The second is a hunch
  until the agent has tried the thing. The same rule applies either way, so a
  remembered bush never outscores the same bush in view. *Risk* projects the
  needs forward to when the benefit would land and charges the damage they would do by
  then.
- **The selector** takes the best score, ties to the earlier candidate.

Travelling needs no plan. Walking toward a spring is valued at what drinking there would
be worth, discounted by the walk. On arrival the walk becomes impossible and the drink
is proposed undiscounted.

**Intent** keeps a trip going. Choosing to head somewhere sets the agent out on an intent:
a goal, a place, and when it set off. The evaluator is wrapped in a
`CommitmentEvaluator` that scores any option carrying the trip on 1.3× higher, so a
near-tie does not turn the agent round. A rest or a meal on the way is a pause, not a
change of mind. The intent ends on arrival, or when the trip has taken three times as
long as it should. The bonus multiplies, so a trip whose goal is no longer wanted still
scores nothing.

`ActionEvaluator`, `ActionSelector` and `DecisionMaker` are interfaces. A learned scorer,
an exploration-sampling selector or a planner can replace any of them without touching
the proposers or the loop.

### Memory

Memory is **a question, not a log**. The decision layer can only ask `recall(query)` and
receive a ranked answer; it cannot iterate anything. The store is bounded at 48 memories
and forgets the least valuable (`confidence × importance`) first.

| Kind | Half-life | Why |
| --- | --- | --- |
| resource location | 4,000 ticks | a spring does not wander off |
| observed resource | 200 ticks | but it does empty, and refill |
| successful / failed action | 600 ticks | what worked here, for a while |
| danger | 1,500 ticks | you remember nearly dying |
| world event | 800 ticks | something changed |
| discovery | 2,000 ticks | finding something is worth keeping |

Relevance is `confidence(now) × importance × 1/(1 + distance/8)`. An agent ends up sure
where the water is and unsure whether there is any left, which is the state of knowledge
that makes going to look worthwhile. Routine successes, like a step taken, are not
remembered; they crowded discoveries out of the store before that rule existed.

### Knowledge

| | Memory | Knowledge |
| --- | --- | --- |
| Holds | one episode, at a place and time | one general claim |
| Example | "I found berries at (4,3)" | "berries are worth 8 hunger a unit" |
| Over time | confidence halves | confidence grows |

**The agent is born ignorant.** The decision layer never reads what a resource is actually
worth. That is physics, known only to the action when it runs. What the agent expects
comes from its own beliefs, each a running mean with confidence `n/(n+3)`. It learns from
four kinds of experience:

- **ate something and hunger fell:** relief divided by units eaten is what a unit is worth
- **drank something and thirst fell:** the same arithmetic
- **told permanently that a thing isn't food:** it is worth nothing, settled in one go
- **went somewhere for something and it wasn't there:** such places are less reliable

A meal that leaves the agent completely full only proves the food is worth *at least*
that much, so it is treated as a lower bound. It can confirm a belief or raise it, never
pull it down. Twenty days in, Atlas believes berries are worth 8.0 and water 12.0, the
true values, at 98% confidence.

### Exploration and experiment

A coverage map records how recently the agent stood in each 4×4 region, with familiarity
halving every 500 ticks. The frontier is simply the least familiar regions. A swept map
makes curiosity fall; familiarity fades; curiosity returns and the agent goes round
again. Nothing schedules it.

A **discovery** is the first sighting of something, or of something somewhere new. No
list of discoveries exists anywhere; they come from comparing what is in view with what
the agent has seen before.

When comfortable, an agent **experiments**: it tastes things it has never tried, for
curiosity's sake rather than hunger's. The reward is what is left to learn. The risk is
a tick, a mouthful, and what its needs do meanwhile. Samples it has found useless are put
down when they are taking up room it needs.

## Persistence

A simulation can be saved between any two ticks and carried on from exactly that point.
**Exactly** means identical, not similar: the same decisions on the same ticks, the same
events, the same random draws. A 20-day run and a 10+10 run split across two separate
processes end with byte-identical save files.

What is saved: the configuration, simulated time, the random generator's full state,
every resource node, and for each agent its body, inventory, age, memories (in order,
because order decides what is forgotten next), beliefs, skills, coverage and what it
found where, any job it is part-way through, and which of its needs were already doing
damage.

What is not saved, deliberately: goals, which are recomputed from needs at every
decision; the standard decision maker, which is stateless; and observers.

Three design choices make exactness possible
([ADR 27](docs/adr/0027-saves-are-exact-and-the-format-is-not-the-domain.md)):

- **The random generator is written out.** `SimulationRandom` implements the generator
  specified for `java.util.Random` (and is tested against it draw for draw), so its whole
  state is one number that can be saved.
- **The save format is not the domain.** `core.save` holds flat records of numbers,
  strings and enums, translated by one class. Refactoring a domain class cannot silently
  change what an old save means, and saves carry a format version that is checked.
- **Jackson lives in its own module.** The core still has no I/O. Reading is strict:
  unknown fields and missing numbers are errors, not silent defaults. Writing goes to a
  temporary file and is moved into place, so a run killed mid-save keeps its last save.

Saves are readable JSON, around 19 KB for a 20-day run.

## Running it

Requires JDK 21 or newer. No Gradle install is needed; use the wrapper.

```bash
./gradlew :simulation-runner:run                                        # ten simulated days, seed 42
./gradlew :simulation-runner:run --args="--seed 7 --ticks 14400"        # a hundred days
./gradlew :simulation-runner:run --args="--seed 7 --speed 20"           # watch at 20 ticks a second
./gradlew :simulation-runner:run --args="--seed 7 --log-level quiet"    # just the story

# save, then carry on later
./gradlew :simulation-runner:run --args="--seed 7 --ticks 1440 --save saves/atlas.json"
./gradlew :simulation-runner:run --args="--load saves/atlas.json --ticks 1440 --save saves/atlas.json"

# a long unattended run that survives being stopped
./gradlew :simulation-runner:run --args="--seed 7 --ticks 144000 --save saves/atlas.json --autosave-every 144"
```

Or build a standalone distribution:

```bash
./gradlew :simulation-runner:installDist
./simulation-runner/build/install/simulation-runner/bin/simulation-runner --seed 7
```

| Option | Default | Meaning |
| --- | --- | --- |
| `--seed <n>` | 42 | seed for a new world |
| `--ticks <n>` | 1440 | ticks to run, or with `--load`, how many more |
| `--tick-minutes <n>` | 10 | simulated minutes per tick, for a new world |
| `--speed <ticks/s>` | 0 | real-time pacing; 0 runs as fast as possible |
| `--log-level <level>` | normal | `quiet`, `normal`, `verbose` or `debug` |
| `--trace` | | the same as `--log-level debug` |
| `--log-every <n>` | 1 | at debug level, trace every n ticks |
| `--save <file>` | | save the run to this file when it ends |
| `--autosave-every <n>` | | also save to that file every n ticks |
| `--load <file>` | | carry on from a save instead of starting fresh |
| `--script` | | run a hand-written action list instead of deciding (cannot be saved) |

A run stops when its ticks run out or nobody is left alive. With `--load`, the seed and
tick length come from the save.

### Log levels

Each level includes everything below it.

| Level | Shows | Ten simulated days |
| --- | --- | --- |
| `quiet` | first discoveries, lessons learned, danger and recovery, death | ~20 lines |
| `normal` | every event, a status block each simulated day, the map at start and end | ~200 lines |
| `verbose` | a status block four times a day, and every action the agent completes | ~1,900 lines |
| `debug` | every perception, want, candidate and score, and the core's own diagnostics | ~10,500 lines |

**Events** are the moments a narrator would mention: arrival, discoveries, lessons
learned (the first time only), danger beginning and passing, skills improving, and
death. **Status blocks** show how things stand: day and time, the agent's body and
location, the goal behind its latest decision, what it is doing (or last did), its most
relevant memories, what it has worked out, its skills and how much of the world it has
seen.

## Testing

```bash
./gradlew test     # all modules, 518 tests
./gradlew build    # compile, test and assemble
```

| Module | Tests | What they cover |
| --- | --- | --- |
| core | 467 | every rule, action, goal, proposer and learner; memory, knowledge, exploration, skills; intent and commitment; healing; long-run integration; save and continue |
| persistence | 14 | JSON round trips, save-to-file-and-continue, byte stability, strict reading, atomic writes |
| runner | 37 | options, status report, log levels, autosave, pacing, scripted runs |

Tests assert behaviour, not arithmetic, wherever they can: "a desperate agent does not
stop to taste a rock", "having learned stone is not food, it stops trying to eat stone".

The integration tests worth knowing about:

- **`AutonomousSimulationTest`** runs twenty simulated days for eight seeds and checks
  every invariant on **every tick**. It asserts that needs stay in range, nothing leaves
  the map, no bag or bush overfills, the dead stay dead, and memory stays bounded. It also
  checks that the agent survives, forages, drinks, rests, explores, learns the right
  values and improves, and that it never repeats a lesson it has been taught. It fails
  any run where the agent walks back and forth between two places, and it holds seed 10,
  the MVP's one death, to a full 2,000 hourly ticks. Agents that start at health 30 must
  mend and live on, the default run's day-1 injury must be healed within three days, and
  health may only rise on a tick when the body is well looked after.
- **`DitheringTest`** sets up the scene seed 10 died in, two bushes with one just out
  of sight, and requires every step to bring the agent closer to the bush it chooses. It
  also tests each of the two faults behind the old oscillation on its own. The MVP fails
  all three tests.
- **`SaveAndContinueTest`** and **`SaveFileTest`** save at nine points, including mid-job,
  mid-journey and mid-recovery,
  through memory and through real JSON files, and require the continuation to be
  identical to a run that never stopped. That includes a ten-leg relay of save and
  reload.
- **`LearningChangesBehaviourTest`** runs the same situation twice, changing nothing but
  what the agent has been through, and shows the choice come out differently.

HTML reports are written to `<module>/build/reports/tests/test/index.html`.

## Known limitations

Honest ones, most important first.

- **The first day is the dangerous one.** A newborn agent does not know berries are food
  until it tries them, and in the default run it goes hungry enough to lose some health
  before it does. It heals within a few hours. Once it has learned, it stays out of
  danger: across 20 seeds × 2,000 hourly ticks, no need does any damage at all.
- **Healing is free.** Mending costs no extra food or water, and a hurt agent rarely
  chooses to stop and rest for it. Recovery is the body's doing, not a decision the agent
  weighs.
- **Greedy, one step at a time.** The agent scores the next action, never a sequence.
  Standing in a spring while mildly thirsty, it walks away rather than topping up, because
  nothing models "this saves a walk later".
- **A small, simple world.** 16×16 tiles, four resource types, no terrain, weather,
  day and night, or line of sight. The field of view is a diamond measured in steps.
- **Wood and stone have no use.** They are only ever gathered as experiments, and put
  down once the agent learns they are not food.
- **One agent.** Perception of other agents exists and is tested, but nothing reacts to
  them.
- **Hand-picked balance.** The constants (need rates, curiosity, confidence priors) were
  tuned by watching runs, and survival is sensitive to them. Skills also progress fast:
  an agent is a skilled walker within a few simulated hours.
- **Hydration is read against the gulp asked for, not the gulp received**, so a spring
  with less than a gulp left would read low. Springs refill fast enough that it has not
  shown up in practice.
- **Save format version 3 has no migration path.** A save from a different format version
  is refused rather than converted. Scripted runs cannot be saved at all.
- **After loading, the status block shows "(not decided yet)"** until the agent's next
  decision. The latest want and decision exist only for display and are not saved.

## Future directions

Roughly in the order they would pay off:

1. **Learning about actions, not just things.** Feed outcomes back into the evaluator's
   weights, so an agent that keeps finding a place empty stops walking to it. The
   `ActionEvaluator` seam exists for this.
2. **A richer world:** terrain that affects movement, day and night, weather, seasons, and
   larger or procedurally generated maps.
3. **Uses for wood and stone:** crafting or building, giving gathering a purpose beyond
   food and the inventory a real trade-off.
4. **More agents**, then what they need: noticing each other, competing for a bush,
   eventually sharing knowledge.
5. **A graphical viewer** as another observer, for example with LibGDX. The core needs no
   changes for it: everything a viewer would draw already arrives as immutable snapshots
   and events.
6. **Save migrations**, so older saves can be carried forward rather than refused.

## Technology

Java 21, Gradle 9 (Kotlin DSL, version catalog), JUnit 5, SLF4J with Logback, and
Jackson for save files. No web framework, no machine learning, no external services.
