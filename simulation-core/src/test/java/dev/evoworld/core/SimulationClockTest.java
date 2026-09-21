package dev.evoworld.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationClockTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Test
    @DisplayName("a new clock sits at tick zero with no time elapsed")
    void startsAtZero() {
        SimulationClock clock = new SimulationClock(TEN_MINUTES);

        assertEquals(0L, clock.currentTick());
        assertEquals(Duration.ZERO, clock.elapsed());
        assertEquals(new SimulationTime(0L, Duration.ZERO), clock.now());
    }

    @Test
    @DisplayName("each advance moves exactly one tick")
    void advancesOneTickAtATime() {
        SimulationClock clock = new SimulationClock(TEN_MINUTES);

        assertEquals(1L, clock.advance().tick());
        assertEquals(2L, clock.advance().tick());
        assertEquals(3L, clock.advance().tick());
        assertEquals(3L, clock.currentTick());
    }

    @Test
    @DisplayName("elapsed simulated time is the tick count times the tick duration")
    void tracksElapsedSimulatedTime() {
        SimulationClock clock = new SimulationClock(TEN_MINUTES);

        for (int i = 0; i < 6; i++) {
            clock.advance();
        }

        assertEquals(Duration.ofHours(1), clock.elapsed());
        assertEquals(new SimulationTime(6L, Duration.ofHours(1)), clock.now());
    }

    @Test
    @DisplayName("the tick duration changes what a tick means, not how many there are")
    void tickDurationIsConfigurable() {
        SimulationClock slow = new SimulationClock(Duration.ofHours(1));
        SimulationClock fast = new SimulationClock(Duration.ofSeconds(1));

        for (int i = 0; i < 10; i++) {
            slow.advance();
            fast.advance();
        }

        assertEquals(10L, slow.currentTick());
        assertEquals(10L, fast.currentTick());
        assertEquals(Duration.ofHours(10), slow.elapsed());
        assertEquals(Duration.ofSeconds(10), fast.elapsed());
    }

    @Test
    @DisplayName("a clock can resume from a tick, which save/load will need")
    void canStartFromAGivenTick() {
        SimulationClock clock = new SimulationClock(TEN_MINUTES, 100L);

        assertEquals(100L, clock.currentTick());
        assertEquals(Duration.ofMinutes(1000), clock.elapsed());
        assertEquals(101L, clock.advance().tick());
    }

    @Test
    @DisplayName("a tick has to have a real, positive duration")
    void rejectsImpossibleConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class, () -> new SimulationClock(Duration.ofMinutes(-1)));
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(TEN_MINUTES, -1L));
        assertThrows(NullPointerException.class, () -> new SimulationClock(null));
    }
}
