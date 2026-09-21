# 31. A survey measures every change

Status: accepted

## Context

Milestones 12 and 13 were decided by numbers: turn-backs, dithering runs, close calls,
health, time to recover. Each time, the numbers came from scripts written for that
milestone and left in a scratch folder. They parsed debug logs, built the previous
commit in a separate worktree to compare against, and started agents wounded through a
one-off Java file. None of it was in the repository, it took minutes to run, and it
was quietly wrong. Every action at debug level is logged twice, so the log-parsing
scripts reported twice the real number of rests.

The agent now survives every seed without a scratch. The next milestones (memory that
changes decisions, a harder world, a balance pass) will each claim to make things
better, and each needs a before and after.

## Decision

A survey harness lives in the runner, in `dev.evoworld.runner.survey`, and runs as
`./gradlew survey`.

- **Named scenarios, not free parameters.** `baseline`, `hourly`, `wounded`,
  `hungry-world` and `sparse` each change one thing. The baseline is checked to be
  exactly the run the console builds. A fixed set means every report is comparable
  with every earlier one.
- **Observers, not logs.** `SurveyObserver` counts from events, outcomes, decisions and
  snapshots, so wording changes in the console cannot break it, and measuring cannot
  change the run.
- **Definitions that are written down.**
  - A **turn-back** is a step that undoes the step taken on the tick before.
  - A **dithering run** is two or more turn-backs in a row. This is the same rule the
    autonomous test's auditor uses.
  - A **food/water trip** starts when a decision for hunger or thirst heads somewhere.
    It is followed from the decisions themselves, because a one-step trip starts and
    ends inside a single tick and no snapshot sees it. That was a bug in the first
    version, which only compared snapshots.
  - A trip is **abandoned** if it ends short of its target, and went **to nothing** if
    the target was bare when it ended.
- **Deterministic output.** Runs go in parallel, and results are gathered in seed order.
  The report carries no timestamps, so the same code writes the same bytes; a test
  checks one thread against four.
- **A committed report.** `docs/survey.md` holds the numbers for the current code. A
  milestone regenerates it, so `git diff` on that file is the before and after, with
  no worktree needed.

## Consequences

The first survey confirms what the scratch scripts found, and corrects one number:

- `hourly` has 95 turn-backs and no dithering runs, matching milestone 12. `wounded`
  mends 20 of 20 in 176 ticks on average, matching milestone 13. Rests are half what
  the old scripts claimed, because they double-counted.
- `hungry-world` and `sparse` bring back close calls: 7 and 2 across 20 seeds, lowest
  health 77.5. Everyone survives and heals.
- **Food trips are almost never wasted.** Out of about 3,000 trips per scenario,
  abandoned trips number 0 to 6, and none end at a bare target. That changes the
  plan. Per-place reliability, the headline of the next memory milestone, has very
  little to fix in today's world. The harder world is what will give memory something
  to do.

The survey takes a few seconds, so it can run whenever behaviour changes. It is not part
of `./gradlew build`: it measures and does not judge. The autonomous tests remain the
gate.
