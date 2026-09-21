# 19. Memory is a question, not a log

Status: accepted

## Context

The simplest memory is a list of everything that happened, handed to whatever needs it.
It fails twice over. It grows without bound across a hundred simulated days, and — worse —
it makes an agent perfectly informed about everything it ever glanced at, which is not a
memory, it is a database with a view of the past.

A decision layer given the raw log would also come to depend on all of it: the order, the
duplicates, the things that should have been forgotten. "The agent recalled that" would
stop being a claim anyone could check.

## Decision

The only way into memory is `MemoryRecall.recall(query)`, which takes a question and
returns a ranked answer. `MemoryStore` implements it; the decision layer is handed the
interface and cannot iterate anything. `contents()` exists for observers and tests, and no
decision goes near it.

The store is bounded — 48 memories by default. When it fills, the least valuable memory
goes, judged as `confidence × importance` at the moment the new one arrives, so faint and
trivial things are forgotten before vivid ones.

Seeing the same bush again **replaces** the memory of it rather than adding another. A
memory is keyed by kind, place and subject, which is the difference between remembering a
thing and remembering every time you noticed it.

Relevance is three factors an agent would weigh without thinking: do I still believe it,
does it matter, and is it anywhere near here.

```
relevance = confidence(now) × importance × 1/(1 + distance/8)
```

## Consequences

- Retrieval is explainable. Every recollection carries its relevance and can print itself
  as a sentence with a timestamp, so a decision built on a memory can be traced back to
  the moment it was formed.
- The store is free to change how it holds things without anything downstream noticing,
  because nothing downstream has ever seen the inside of it.
- A memory too faint to act on is not returned, but is not deleted either. Forgetting is
  about capacity; relevance is about attention. Conflating them would mean a distant
  memory could never become useful again by the agent walking towards it.
