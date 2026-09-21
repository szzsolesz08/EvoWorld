# 32. The viewer plays the run it watches

Status: accepted

## Context

A run could not be followed. `./gradlew run` computes ten simulated days in under a
second and then prints them all at once. Even the default `normal` level is about 210
lines, and `verbose` and `debug` give one to seven lines per tick. `--speed` slowed the
scroll down, but a scrolling log is still a log. The user wanted to watch Atlas live,
and chose a LibGDX window over a terminal dashboard or a browser page.

ADRs 2 and 28 had prepared for this. Observers receive only immutable values, so
something that draws is just another observer.

## Decision

- **A fourth module, `simulation-viewer`.** It depends on the core, persistence (to save
  and load) and the runner (to reuse its world building, the survey's scenarios, its log
  levels and its status wording). LibGDX 1.14.2 with the LWJGL3 desktop backend appears
  nowhere else. It uses no assets: shapes come from `ShapeRenderer`, and text from
  LibGDX's built-in font.
- **The simulation runs on the render thread.** Each frame asks for as many ticks as
  the elapsed time is worth at the current speed, at most 20. Backlog beyond that is
  dropped, so a stalled window slows the picture down instead of lurching to catch up.
  The simulation isn't thread-safe and doesn't need to be. The run is still the seed's
  run: a test drives the model with uneven frame lengths and requires the same save as
  `Simulation.run(300)`.
- **`Simulation.start()`** readies a run for stepping by hand: it notifies observers and
  announces arrivals, as `run()` does before its loop. Only the first call does
  anything. `run()` is unchanged, and the survey's numbers are identical before and
  after.
- **The model is separate from the drawing.** `ViewerModel` (pacing, pausing,
  stepping, the event feed) and `MapLayout` (tile to pixel) contain no LibGDX types and
  are unit-tested without a GPU. `ViewerScreen` only draws them and reads keys.
- **The panel reuses the console's words.** Its rows come from `StatusReport.lines`, and
  the feed uses each event's `describe()`, so the window and the log never disagree
  about what happened.
- **`--screenshot`** saves one frame as a PNG after a given number of ticks and quits.
  The README picture is made that way, and it let the layout be checked without anyone
  watching.
- **The viewer uses a `view` task, not the `application` plugin.** From the repository
  root, `./gradlew run` runs every project's `run` task, so an `application` viewer
  would open a window whenever someone wanted the console.

## Consequences

- `./gradlew view` opens a window at 4 ticks a second. `Space`, `Right`, `+`/`-`, `S` and
  `Esc` control it. `--scenario wounded` shows healing as it happens.
- The drawing code itself isn't covered by unit tests. It is checked by screenshots.
- The panel shows the single strongest want, because that is all `AgentStatus` carries.
  Showing the full ranking would mean adding it to the status value.
- The viewer is the natural place to show what the next milestones add, such as day and
  night, terrain, and what the agent remembers of a place.
