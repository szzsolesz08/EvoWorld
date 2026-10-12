package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A day and a night: the light, the warmth, and the lag between them. */
class SkyTest {

    @Test
    @DisplayName("it is light between sunrise and sunset and dark in the small hours")
    void lightFollowsTheSun() {
        assertEquals(1.0, Sky.atHour(12.0).daylight(), 1e-9, "noon");
        assertEquals(1.0, Sky.atHour(7.0).daylight(), 1e-9, "after sunrise");
        assertEquals(0.0, Sky.atHour(3.0).daylight(), 1e-9, "the small hours");
        assertEquals(0.0, Sky.atHour(23.0).daylight(), 1e-9, "well after dusk");
        assertTrue(Sky.atHour(3.0).isNight());
        assertFalse(Sky.atHour(12.0).isNight());
    }

    @Test
    @DisplayName("dawn and dusk come on gradually rather than like a switch")
    void twilight() {
        double dawn = Sky.atHour(5.5).daylight();
        double dusk = Sky.atHour(19.5).daylight();

        assertTrue(dawn > 0.0 && dawn < 1.0, "half-light before sunrise: " + dawn);
        assertTrue(dusk > 0.0 && dusk < 1.0, "half-light after sunset: " + dusk);
        assertTrue(Sky.atHour(5.8).daylight() > Sky.atHour(5.2).daylight(), "getting lighter");
    }

    @Test
    @DisplayName("the coldest hour is before dawn and the warmest is mid-afternoon, not noon")
    void warmthLagsBehindTheSun() {
        assertEquals(Sky.COLDEST, Sky.atHour(5.0).temperature(), 0.01);
        assertEquals(Sky.WARMEST, Sky.atHour(15.0).temperature(), 0.01);
        assertTrue(
                Sky.atHour(15.0).temperature() > Sky.atHour(12.0).temperature(),
                "the ground goes on warming after the sun is highest");
        assertTrue(
                Sky.atHour(1.0).temperature() > Sky.atHour(5.0).temperature(),
                "and goes on cooling after midnight");
    }

    @Test
    @DisplayName("it never gets colder than the coldest hour or warmer than the warmest")
    void staysWithinTheDay() {
        for (double hour = 0.0; hour < 24.0; hour += 0.25) {
            double temperature = Sky.atHour(hour).temperature();
            assertTrue(
                    temperature >= Sky.COLDEST - 0.01 && temperature <= Sky.WARMEST + 0.01,
                    hour + "h was " + temperature);
        }
    }

    @Test
    @DisplayName("the same hour of any day is the same sky, and a run starts at midnight")
    void everyDayIsTheSameDay() {
        SimulationTime firstMorning = new SimulationTime(1L, Duration.ofHours(9));
        SimulationTime thirdMorning = new SimulationTime(1L, Duration.ofHours(9 + 48));

        assertEquals(Sky.at(firstMorning), Sky.at(thirdMorning));
        assertEquals(0.0, Sky.hourOf(Duration.ZERO), 1e-9, "a run opens at midnight");
        assertEquals(0.5, Sky.hourOf(Duration.ofMinutes(30)), 1e-9);
    }
}
