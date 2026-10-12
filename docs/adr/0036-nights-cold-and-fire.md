# 36. Nights, cold, and fire

Status: accepted. This extends ADR 33 (a body in real time) and ADR 35 (things worth
having).

## Context

The world had no hours. Every tick was as light and as warm as every other, Atlas slept
whenever his energy ran down rather than when it was dark, and the only thing the clock
did was count. The user asked for three things: a day and a night, an outside temperature
with night colder than day, and a fire he can build so the nights do not cost him - all of
it realistic enough that weather and winter can be added later without redoing it, and
mild enough that a spring night is uncomfortable rather than fatal.

They chose, when asked, that cold should **burn energy and spoil sleep**, and that night
should be **colder, darker, and better to sleep in**.

## Decision

**The sky is a function of the hour.** `Sky.at(time)` gives daylight and temperature from
the time of day and nothing else - no state, nothing saved, the same on every run of a
seed. Light is full between six and seven, with an hour of twilight either side. Warmth
runs on a cosine between the coldest hour (five in the morning, 5°C) and the warmest
(three in the afternoon, 16°C), which is the real shape: the ground goes on cooling after
midnight and on warming after noon. Seasons and weather will arrive as things that bend
these two numbers.

**Cold does two different things, because the body already has two currencies.**
`Exposure` turns a felt temperature and a daylight into a `chill` and a `comfort`, and
`Exertion` carries both. Comfort scales what a tick gives back - the same field a bed was
always going to use - so a cold night returns less of itself. Chill scales what a tick
costs, because shivering is work the body does whether or not the agent meant to. Darkness
enters here too, and only helps: a body rests better in the dark.

Per degree below a mild fifteen: a hundredth more energy burnt, and a sixtieth of a
night's rest lost. At the coldest hour of this spring that is a tenth more fuel and a sixth
of a night - a fire is worth the wood, and going without one costs an hour's extra sleep.

**Night is genuinely darker.** Perception's reach is scaled by daylight, from twelve steps
at noon to four in the small hours. Nothing tells the agent to stop travelling after dark;
there is simply less to be found out there, so going to look is worth less, and the
evaluator does the rest.

**A fire is the first thing an agent puts into the world.** `Fire` has a place, a radius
of three, twelve degrees of warmth at its centre fading to nothing a step beyond its
reach, and a pile of wood it burns at half a unit an hour. It keeps burning while nobody
is watching and is forgotten when it goes out. `TendFireAction` lights one or feeds the
one underfoot - the same act either way - and `KeepWarmGoal` is the want it answers.

**The want measures what the night would cost, discounted by the fire already burning.**
Not the temperature of the tile the agent is standing on: measured that way an agent at
its own fire is perfectly warm, wants nothing, never lays another log, and wakes at three
in the morning beside a heap of ash. What it is exposed to is the night; what answers it
is fuel; and the fire that counts is the one warming it, not the one exactly underfoot.

Save format 7 carries the fires and their wood.

## What the measurements found

Eleven survey runs, and every one of them changed something.

1. **A fire only counts if you can feel it.** Shelter judged by the tile underfoot, while
   warmth reaches three tiles, gave an agent that was warm and believed itself exposed. It
   walked onto its own fire and off again all night.
2. **A target is worth what it can give, not what you need.** A log with 0.2 wood left was
   worth the whole of a night's firewood right up until the agent arrived on it - full
   price from one tile, nothing from the next - so it stepped back and forth between a
   bare log and its supper for hours. Errands are now valued at what is actually there,
   and the nearest log that is bare is looked past rather than fixated on.
3. **Firewood is a supply, not an emergency.** Fetched only once the cold had bitten, it
   was fetched in the dark, a handful at a time, and went straight on the fire - so
   nothing was ever left to make a flask from, and the previous milestone's water-carrying
   quietly came undone (flasks 1.8 a run down to 0.5, water travel back up from 0.87 to
   1.57 hours a day). Treating a night's wood as something an agent keeps on it, like
   water, put both back: it now gathers wood by day, lights a fire most nights, and still
   makes flasks.
4. **A fire will take everything you give it.** Feeding it the whole armful left nothing
   for anything else; it now gets what the night needs and no more.
5. **A comfort yields to a need.** Warmth, like curiosity and providing for tomorrow, goes
   quiet when something is actually wrong. Without that an agent gathered firewood with
   its hunger climbing.
6. **The spring had to be softened twice.** At three hundredths of a night's rest per
   degree, the one scenario where wood is too scarce to burn started losing agents. A
   spring is not supposed to kill anybody.
7. **Tests that were about geometry were quietly about the hour.** Runs begin at midnight,
   so perception tests written before there were nights were measuring what an agent can
   see in the dark.

## Consequences

Twenty seeds, twenty days, six scenarios: **120 of 120 survive, no deaths**, and the
baseline has three close calls in four hundred simulated days, each recovered from.

| | before | after |
| --- | ---: | ---: |
| sleep, baseline | 8.8 h/day | **9.4 h/day** |
| free time, baseline | 51% | **50%** |
| water travel, baseline | 0.87 h/day | **0.86 h/day** |
| fires lit per run | – | **8.5** (12.1 in the walled world) |
| flasks made per run | 0.9 | **0.9** |

The extra hour of sleep is the cold, stated plainly: a night outdoors at five degrees
returns less than a night at fifteen, so the body takes longer over it. It is also the
clearest measure of what a fire is for.

Atlas now keeps a few sticks on him, lights a fire on most nights, sleeps beside it, and
wakes rested. Nothing tells him to do any of that.

**Known limitation:** in the walled sixteen-tile world he still walks back and forth more
than he should - eight dithering runs in twenty - because everything there is within a
step of everything else and the wants are near-equal. It costs him nothing measurable
(twenty of twenty survive with no close calls at all), and the open world shows none of
it.

**What this sets up:** wood is now a thing that is consumed every night rather than once
per flask, which is the recurring work an axe has been waiting for. An axe halves the
effort of a gather and takes half again as much per swing; with a fire to feed, that may
finally be worth the stone.
