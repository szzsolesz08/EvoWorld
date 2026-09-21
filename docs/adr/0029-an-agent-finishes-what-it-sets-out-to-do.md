# 29. An agent finishes what it sets out to do

Status: accepted

## Context

The MVP had one known way to die. With seed 10, Atlas stood between two berry bushes,
seven steps apart either way, and walked back and forth between them from t280 until he
starved at t358. Seed 5 nearly went the same way. Across 20 seeds × 2,000 hourly ticks,
the MVP turned straight back on itself 1,009 times: a step from A to B, then on the very
next tick a step from B back to A. It did this in 81 runs of two or more.

The plan was **commitment**. Decisions were made fresh every tick, so a near-tie could
flip with every step. An agent that had set out for something should value carrying on a
little more than starting over.

Building it showed that commitment alone was not the fix. Two faults made each step
**reverse** the ranking, not just leave it nearly tied:

1. **A thing in sight was doubted more than the same thing remembered.** The proposer
   for visible resources charged the agent's doubt about the resource ("are berries
   really food?"). The proposer for remembered places charged its doubt about the place
   ("is it still there?"), but never the doubt about the resource. An agent that had
   eaten berries only twice was 0.5 sure of a bush it could see, and 1.0 sure of the
   same bush one step out of sight. So whichever bush was behind him looked better, and
   each step put the other one behind him.
2. **The destination could drop out of mind.** Recall returned the eight most relevant
   place memories, and places in sight were filtered out *afterwards*. An agent standing
   among stone and wood recalled eight things it could already see, and the spring it was
   walking to wasn't in its options that tick.

A 1.3× commitment bonus cannot hold against a factor-of-two error. With commitment alone,
reversals fell to 413, and seed 10 still dithered.

## Decision

Fix both faults at the source, and add commitment as well.

- **One confidence rule for anything offered for what it is.** It is how sure the agent
  is that the thing is there, times how sure it is that the thing is worth having
  (`UntestedGuess.worthIt`, never less than a guess). For a thing in sight the first
  factor is 1. For a remembered one it is memory confidence times place reliability.
  A remembered thing can therefore never outscore the same thing in view.
- **Recall leaves out what is in sight before it counts to eight.**
- **Intent.** An `Intent` (goal, target, when formed, starting distance) lives on the
  agent and in its snapshot, so perception, status and saves all see it.
  `ActionCandidate` and `Decision` carry an optional target. A `CommitmentEvaluator`
  wraps the `UtilityEvaluator` (ADR 17's seam) and multiplies by `1 + 0.3` any candidate
  that continues the current intent, meaning the same goal towards the same place. It
  adds "(keeping on)" to the rationale. Nothing inspects action types.

The lifecycle lives in `Simulation.decide`:

| When | What happens to the intent |
| --- | --- |
| before deciding, the agent is at the target | fulfilled: cleared |
| before deciding, it has run past `3 × distance + 10` ticks | stuck: given up |
| the chosen action heads for the same goal and place | kept |
| it heads somewhere else, or for another goal | replaced |
| it heads nowhere (rest, eat, gather) | kept: a pause, not a change of mind |
| the agent dies | cleared |

Staleness and arrival are settled *before* deliberation, so a spent intent cannot tip
the decision it no longer deserves to. Clearing the intent on any action that doesn't
move was rejected. It would let a single rest reset the commitment, and bring the
oscillation back after every pause.

The bonus multiplies. A trip whose goal has gone to zero want still scores zero, so the
agent is loyal, not stubborn.

The save format goes to version 2 with a nullable `intent` on each agent. Version-1 saves
are refused, as before (migration is a later milestone).

## Consequences

Across 20 seeds × 2,000 hourly ticks:

| Build | Survived | Turn-backs | Runs of 2+ |
| --- | --- | --- | --- |
| MVP | 19/20 | 1,009 | 81 (longest 44) |
| commitment only | 20/20 | 413 | |
| confidence and recall fixes only | 20/20 | 127 | 0 |
| all three | 20/20 | 95 | 0 |

The confidence and recall fixes are what cured the dithering. Commitment removes about a
quarter of the remaining single turn-backs, and it is a margin for near-ties that the
next milestones will create, such as health against hunger, or danger against distance.
Commitment between 0.2 and 0.5 made little difference (116 to 95 turn-backs); 0.3 is kept.

The remaining turn-backs are single and deliberate. Atlas walks a step past a bush, steps
back to gather from it, and carries on. The autonomous test's auditor now checks for runs
of back-and-forth on every tick. `DitheringTest` reproduces the seed 10 scene and holds
each root cause to a test. The MVP fails all three tests: the scene shows the exact
(7,9) ↔ (7,10) oscillation.

The status block gains an `intent` row. There is no new event type: setting out and
arriving happen constantly, so they belong in the trace, not in the story.
