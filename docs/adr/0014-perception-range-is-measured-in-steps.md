# 14. Perception range is measured in steps, not sight lines

Status: accepted

## Context

A radius on a grid can mean Euclidean distance (a circle), Chebyshev distance (a square),
or Manhattan distance (a diamond). Sight is a circle, and a square is the usual cheap
approximation, so a diamond looks like the odd choice.

## Decision

Range is Manhattan distance: a radius of six means six *moves* away. Movement is four-way,
so Manhattan distance is exactly travel cost, and "four steps north" is directly
comparable to what four steps will cost in energy and time. A distance that means
something to a decision is worth more than one that models optics.

`Direction.from` reports a bearing in eight compass points, with a cardinal winning when
one axis is more than twice the other — so ten east and one south reads as "east" rather
than "south-east".

## Consequences

- A field of view is a diamond, which is physically odd and would be wrong the moment
  eyesight matters. It does not yet: there is no terrain, no elevation and nothing to
  occlude a view, so there is no sight line to model in the first place.
- Distance in a perception is immediately usable: a future utility function can weigh
  "berries four steps away" against "water nine steps away" without converting anything.
- If terrain and line of sight ever arrive, this is the decision to revisit, and the place
  to revisit it is one method on `PerceptionSystem`.
