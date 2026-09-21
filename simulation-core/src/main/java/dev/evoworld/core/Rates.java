package dev.evoworld.core;

import java.time.Duration;

/**
 * Turns a rate per simulated hour into a rate per tick.
 *
 * <p>Every rate that changes a body or the world is stated per hour of
 * simulated time, because that is what makes it mean something: thirst that
 * rises two points an hour hurts after two days, whatever the tick length.
 * The simulation still moves in ticks, so each rate is converted once, here,
 * for the tick length of the run.
 */
public final class Rates {

    private static final double SECONDS_PER_HOUR = 3600.0;

    private Rates() {
    }

    /**
     * {@code perHour} for one tick of {@code tick}.
     *
     * <p>Multiplied before it is divided, so a rate written as a decimal - 4.8
     * an hour - comes back exactly as the per-tick number it stands for (0.8 at
     * ten minutes), rather than one rounding error away from it.
     */
    public static double perTick(double perHour, Duration tick) {
        return perHour * tick.getSeconds() / SECONDS_PER_HOUR;
    }
}
