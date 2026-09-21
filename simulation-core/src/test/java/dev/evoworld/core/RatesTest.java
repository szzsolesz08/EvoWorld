package dev.evoworld.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.evoworld.core.agent.MetabolismRates;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Rates live in simulated time; ticks only cut it up. */
class RatesTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Test
    @DisplayName("a rate written per hour comes back exactly as its per-tick number")
    void convertsExactly() {
        // Multiplying first is what keeps 4.8 an hour at exactly 0.8 a tick.
        assertEquals(0.8, Rates.perTick(4.8, TEN_MINUTES));
        assertEquals(0.1, Rates.perTick(0.6, TEN_MINUTES));
        assertEquals(0.05, Rates.perTick(0.3, TEN_MINUTES));
        assertEquals(2.0, Rates.perTick(2.0, Duration.ofHours(1)));
    }

    @ParameterizedTest(name = "{0}-minute ticks")
    @CsvSource({"1", "10", "30", "60"})
    @DisplayName("an hour's worth of ticks adds up to the hourly rate, whatever the tick length")
    void anHourIsAnHour(long minutes) {
        MetabolismRates perHour = MetabolismRates.defaults();
        MetabolismRates perTick = perHour.perTick(Duration.ofMinutes(minutes));
        long ticksPerHour = 60 / minutes;

        assertEquals(perHour.thirst(), perTick.thirst() * ticksPerHour, 1e-9);
        assertEquals(perHour.hunger(), perTick.hunger() * ticksPerHour, 1e-9);
        assertEquals(perHour.sleepingEnergy(), perTick.sleepingEnergy() * ticksPerHour, 1e-9);
    }

    @ParameterizedTest(name = "{0}-minute ticks: {1} step(s), a {2}-tick gather")
    @CsvSource({"10, 1, 2", "20, 2, 1", "30, 3, 1", "60, 6, 1", "5, 1, 4"})
    @DisplayName("walking and gathering take the same simulated time at any tick length")
    void actionsKeepTheirPace(long minutes, int steps, int gatherTicks) {
        ActionSettings actions = ActionSettings.defaults();
        Duration tick = Duration.ofMinutes(minutes);

        assertEquals(steps, actions.stepsPerTick(tick));
        assertEquals(gatherTicks, actions.gatherTicks(tick));
    }
}
