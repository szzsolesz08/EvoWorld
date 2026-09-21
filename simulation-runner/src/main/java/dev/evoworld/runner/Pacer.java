package dev.evoworld.runner;

import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.world.WorldState;

/**
 * Holds a run to a fixed number of ticks per real second.
 *
 * <p>Wall-clock pacing lives here, in the runner, and never in the core (see
 * ADR 5): a simulation that knew what time it was on the machine could be made
 * to behave differently by a slow disk. The pacer waits <em>between</em> ticks
 * and touches nothing, so a paced run and an unpaced one are the same run,
 * tick for tick - only the watching is slower.
 *
 * <p>It aims at a schedule rather than sleeping a fixed amount after each tick,
 * so time spent simulating is not added on top and the rate does not drift.
 */
public final class Pacer implements SimulationObserver {

    private final long nanosPerTick;
    private long nextTickDue;

    /** @param ticksPerSecond how many ticks to allow per second of real time */
    public Pacer(double ticksPerSecond) {
        if (ticksPerSecond <= 0.0) {
            throw new IllegalArgumentException("a pacer needs a positive speed, was " + ticksPerSecond);
        }
        this.nanosPerTick = Math.max(1L, Math.round(1_000_000_000L / ticksPerSecond));
    }

    @Override
    public void onSimulationStarted(WorldState state) {
        nextTickDue = System.nanoTime() + nanosPerTick;
    }

    @Override
    public void onTickCompleted(WorldState state) {
        long wait = nextTickDue - System.nanoTime();
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException e) {
                // Stop pacing, not the run: whoever interrupted wants us to hurry.
                Thread.currentThread().interrupt();
            }
        }
        nextTickDue += nanosPerTick;
    }
}
