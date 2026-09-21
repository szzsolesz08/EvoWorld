package dev.evoworld.core;

import java.time.Duration;
import java.util.Objects;

/**
 * What time it is inside the world: the tick number and how much simulated
 * time has passed since the run began.
 *
 * <p>Ticks are what the simulation counts; elapsed time is what the world
 * experiences. Keeping both together means an observer can report either
 * without having to know the tick duration.
 */
public record SimulationTime(long tick, Duration elapsed) {

    public SimulationTime {
        Objects.requireNonNull(elapsed, "elapsed");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative, was " + tick);
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed time must not be negative, was " + elapsed);
        }
    }

    @Override
    public String toString() {
        return "t" + tick + " (+" + elapsed + ")";
    }
}
