package dev.evoworld.core.world;

import dev.evoworld.core.SimulationTime;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * What the sky is doing at a given hour: how light it is, and how warm.
 *
 * <p>A pure function of the time of day, so it needs no state, saves nothing
 * and is the same on every run of the same seed. Weather and seasons will
 * arrive here later as things that bend these two numbers; today there is one
 * season, a mild spring, and every day is the same day.
 *
 * <p>The shape is the real one. Light comes up at dawn and goes at dusk with
 * twilight either side. Warmth does <em>not</em> follow the sun exactly: the
 * ground goes on losing heat after midnight and goes on gaining it after noon,
 * so the coldest hour is around five in the morning and the warmest around
 * three in the afternoon. That lag is why the small hours are the ones worth
 * having a fire for.
 */
public record Sky(double daylight, double temperature) {

    /** A mild spring: five degrees before dawn, sixteen in the afternoon. */
    public static final double COLDEST = 5.0;

    public static final double WARMEST = 16.0;

    /** The hour the day is coldest; dawn has not warmed anything yet. */
    private static final double COLDEST_HOUR = 5.0;

    /** The hour the day is warmest, some way after the sun is highest. */
    private static final double WARMEST_HOUR = 15.0;

    /** First light and last light, which in spring are about thirteen hours apart. */
    private static final double SUNRISE = 6.0;

    private static final double SUNSET = 19.0;

    /** How long the light takes to come up and go down. */
    private static final double TWILIGHT = 1.0;

    public static final int HOURS_IN_A_DAY = 24;

    public Sky {
        if (daylight < 0.0 || daylight > 1.0) {
            throw new IllegalArgumentException("daylight must be within [0, 1], was " + daylight);
        }
    }

    /** The sky at a moment. */
    public static Sky at(SimulationTime time) {
        Objects.requireNonNull(time, "time");
        return atHour(hourOf(time.elapsed()));
    }

    /** The sky at a given hour of the day, from 0 to 24. */
    public static Sky atHour(double hour) {
        return new Sky(lightAt(hour), warmthAt(hour));
    }

    /** What o'clock it is, as a fraction of an hour. */
    public static double hourOf(Duration elapsed) {
        double hours = elapsed.getSeconds() / 3600.0;
        return hours - Math.floor(hours / HOURS_IN_A_DAY) * HOURS_IN_A_DAY;
    }

    /**
     * Full daylight between sunrise and sunset, nothing at night, and an hour
     * of half-light either side.
     */
    private static double lightAt(double hour) {
        if (hour <= SUNRISE - TWILIGHT || hour >= SUNSET + TWILIGHT) {
            return 0.0;
        }
        if (hour >= SUNRISE && hour <= SUNSET) {
            return 1.0;
        }
        return hour < SUNRISE
                ? (hour - (SUNRISE - TWILIGHT)) / TWILIGHT
                : ((SUNSET + TWILIGHT) - hour) / TWILIGHT;
    }

    /**
     * A cosine between the coldest and warmest hours, which is what a day's
     * temperature actually looks like once the ground's lag is allowed for.
     */
    private static double warmthAt(double hour) {
        double sinceColdest = hour - COLDEST_HOUR;
        if (sinceColdest < 0.0) {
            sinceColdest += HOURS_IN_A_DAY;
        }
        // Stretched so that the peak lands on the warmest hour rather than
        // exactly twelve hours after the trough.
        double swing = WARMEST_HOUR - COLDEST_HOUR;
        double phase = sinceColdest <= swing
                ? Math.PI * sinceColdest / swing
                : Math.PI + Math.PI * (sinceColdest - swing) / (HOURS_IN_A_DAY - swing);
        double middle = (COLDEST + WARMEST) / 2.0;
        return middle - (WARMEST - COLDEST) / 2.0 * Math.cos(phase);
    }

    /** Whether it is dark enough that seeing is hard. */
    public boolean isNight() {
        return daylight <= 0.0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.1f C, %.0f%% light", temperature, daylight * 100);
    }
}
