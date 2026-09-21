package dev.evoworld.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunnerOptionsTest {

    @Test
    void usesDefaultsWhenNoArgumentsAreGiven() {
        RunnerOptions options = RunnerOptions.parse(new String[] {});

        assertEquals(RunnerOptions.defaults(), options);
    }

    @Test
    void parsesSeedAndTicks() {
        RunnerOptions options = RunnerOptions.parse(new String[] {"--seed", "7", "--ticks", "250"});

        assertEquals(7L, options.seed());
        assertEquals(250L, options.ticks());
        assertEquals(RunnerOptions.DEFAULT_TICK_DURATION, options.tickDuration());
    }

    @Test
    @DisplayName("tick duration is configurable in whole minutes of simulated time")
    void parsesTickDuration() {
        RunnerOptions options = RunnerOptions.parse(new String[] {"--tick-minutes", "90"});

        assertEquals(Duration.ofMinutes(90), options.tickDuration());
    }

    @Test
    @DisplayName("a negative seed is fine, a negative tick count is not")
    void validatesValues() {
        assertEquals(-3L, RunnerOptions.parse(new String[] {"--seed", "-3"}).seed());
        assertThrows(
                IllegalArgumentException.class,
                () -> RunnerOptions.parse(new String[] {"--ticks", "-1"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> RunnerOptions.parse(new String[] {"--tick-minutes", "0"}));
    }

    @Test
    void rejectsUnknownAndIncompleteOptions() {
        assertThrows(
                IllegalArgumentException.class, () -> RunnerOptions.parse(new String[] {"--nope"}));
        assertThrows(
                IllegalArgumentException.class, () -> RunnerOptions.parse(new String[] {"--seed"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> RunnerOptions.parse(new String[] {"--ticks", "many"}));
    }

    @Test
    @DisplayName("speed is ticks per real second, and zero means as fast as possible")
    void parsesSpeed() {
        assertEquals(RunnerOptions.UNPACED, RunnerOptions.defaults().speed());
        assertEquals(false, RunnerOptions.defaults().isPaced());

        RunnerOptions paced = RunnerOptions.parse(new String[] {"--speed", "12.5"});

        assertEquals(12.5, paced.speed());
        assertEquals(true, paced.isPaced());
        assertThrows(
                IllegalArgumentException.class,
                () -> RunnerOptions.parse(new String[] {"--speed", "-1"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> RunnerOptions.parse(new String[] {"--speed", "fast"}));
    }

    @Test
    @DisplayName("the default run tells the story; the trace is opt-in")
    void traceIsOptIn() {
        assertEquals(false, RunnerOptions.defaults().trace());
        assertEquals(true, RunnerOptions.parse(new String[] {"--trace"}).trace());
    }

    @Test
    @DisplayName("an unattended run lasts ten simulated days by default")
    void defaultRunIsLongEnoughToWatch() {
        assertEquals(1440L, RunnerOptions.defaults().ticks());
    }

    @Test
    @DisplayName("in-world time reads as day and clock, counting from day one")
    void eventTimesReadAsDayAndClock() {
        assertEquals("day 1 00:00",
                EventLog.when(new dev.evoworld.core.SimulationTime(0L, java.time.Duration.ZERO)));
        assertEquals("day 3 14:20",
                EventLog.when(new dev.evoworld.core.SimulationTime(
                        346L, java.time.Duration.ofDays(2).plusHours(14).plusMinutes(20))));
    }
}
