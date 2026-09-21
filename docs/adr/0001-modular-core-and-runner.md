# 1. Split the simulation core from the runner

Status: accepted

## Context

The long-term goal includes a LibGDX visualization. The usual way that goes wrong is that
rendering concerns leak into simulation logic — a class reaches for a sprite, a tick gets
tied to a frame, and from then on the simulation cannot run without a window.

## Decision

Two Gradle modules:

- `simulation-core` — the simulation. Depends on SLF4J and nothing else.
- `simulation-runner` — the headless entry point: CLI parsing, console output, wiring.

`simulation-core` must never depend on `simulation-runner`, and must never depend on any
rendering library. A `simulation-viewer` module would depend on the core, exactly as the
runner does.

## Consequences

- The console logger is presentation, so it lives in the runner. A viewer would replace
  it without the core noticing.
- Anything the core needs to tell the outside world has to go through
  `SimulationObserver`, which keeps that surface deliberate and small.
- Two modules is slight overhead for the current amount of code. It is worth paying now
  because the boundary is far cheaper to keep than to introduce later.
