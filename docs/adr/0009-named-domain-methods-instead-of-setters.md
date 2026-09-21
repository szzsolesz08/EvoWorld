# 9. Named domain methods instead of setters

Status: accepted

## Context

`Needs` originally exposed `withHunger`, `withEnergy` and friends, plus `spendEnergy` and
`recoverEnergy`. Generic setters make two mistakes easy: passing a negative number and
getting the opposite of the intended effect, and writing code that reads as mechanics
(`withHunger(h - 5)`) rather than as the domain (`eat(5)`).

## Decision

Every change to an agent's state goes through a method named for what happened —
`eat`, `drink`, `exert`, `rest`, `heal`, `harm` on `Needs`, and the same vocabulary plus
`store`, `take`, `moveTo` and `live` on `Agent`. Each takes a **non-negative** amount and
moves the value in the direction its name implies; a negative argument is rejected rather
than quietly reversed.

The setters are gone entirely. Arbitrary states are still constructible through the
canonical constructor, which is what tests need, but no production call site can
accidentally build one.

`Agent` has no `setNeeds`. The only way its body changes is an action saying `exert` or
`rest`, or time passing via `live(rates, activity)`.

## Consequences

- Clamping to [0, 100] lives in one place — the `Needs` constructor — so no sequence of
  operations can produce an impossible body. There is a test that hammers a few hundred
  mixed changes and asserts the invariant after every one.
- The set of ways an agent can change is a short, readable list on one class, which is
  what makes it reviewable as milestones add to it.
- Reading a diff tells you what the simulation *means*, not just which field moved.
