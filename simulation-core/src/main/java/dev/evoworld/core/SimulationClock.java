package dev.evoworld.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Counts simulation time in discrete ticks of a fixed duration.
 *
 * <p>Tick zero is the state of the world before anything has happened; the
 * first processed tick is tick one.
 *
 * <p>The tick duration is how much time passes <em>in the world</em> per tick -
 * it has nothing to do with how fast the run executes. A headless run goes as
 * fast as the CPU allows whatever the duration says, and changing the duration
 * changes the meaning of a tick, not the speed of the program. Wall-clock
 * pacing, if a live viewer ever wants it, belongs in the runner where it cannot
 * affect the simulation.
 */
public final class SimulationClock {

    private final Duration tickDuration;
    private long tick;

    public SimulationClock(Duration tickDuration) {
        this(tickDuration, 0L);
    }

    public SimulationClock(Duration tickDuration, long startTick) {
        Objects.requireNonNull(tickDuration, "tickDuration");
        if (tickDuration.isZero() || tickDuration.isNegative()) {
            throw new IllegalArgumentException("tick duration must be positive, was " + tickDuration);
        }
        if (startTick < 0) {
            throw new IllegalArgumentException("tick must not be negative, was " + startTick);
        }
        this.tickDuration = tickDuration;
        this.tick = startTick;
    }

    /** How much simulated time one tick represents. */
    public Duration tickDuration() {
        return tickDuration;
    }

    public long currentTick() {
        return tick;
    }

    /** Simulated time passed since the run began. */
    public Duration elapsed() {
        return tickDuration.multipliedBy(tick);
    }

    public SimulationTime now() {
        return new SimulationTime(tick, elapsed());
    }

    public SimulationTime advance() {
        tick++;
        return now();
    }
}
