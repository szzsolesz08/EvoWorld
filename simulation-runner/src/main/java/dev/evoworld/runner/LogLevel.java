package dev.evoworld.runner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.util.Locale;
import org.slf4j.LoggerFactory;

/**
 * How much of a run to show.
 *
 * <p>Four settings, each a superset of the one before. The default is meant to
 * be left on for a run of weeks; debug is meant for a few ticks while working
 * out why an agent did something odd.
 *
 * <ul>
 *   <li><strong>quiet</strong> - only the moments that change the story: first
 *       discoveries, lessons, danger, death.
 *   <li><strong>normal</strong> - every event, and a status report once a
 *       simulated day.
 *   <li><strong>verbose</strong> - status four times a day, and every action
 *       the agent completes.
 *   <li><strong>debug</strong> - everything: each perception, each want, every
 *       option weighed and its score, and the core's own diagnostics.
 * </ul>
 */
public enum LogLevel {

    QUIET,
    NORMAL,
    VERBOSE,
    DEBUG;

    public static LogLevel parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "log level must be one of quiet, normal, verbose, debug; was '" + text + "'", e);
        }
    }

    public boolean atLeast(LogLevel other) {
        return compareTo(other) >= 0;
    }

    /** Ticks between status reports, or zero for none. */
    public long statusEvery(long ticksPerDay) {
        return switch (this) {
            case QUIET -> 0L;
            case NORMAL -> ticksPerDay;
            case VERBOSE, DEBUG -> Math.max(1L, ticksPerDay / 4);
        };
    }

    /**
     * Sets the simulation core's own logging to match.
     *
     * <p>The core logs very little, and most of it repeats what an event already
     * says, so it is quiet unless asked. Output from the runner's own observers
     * is governed by which observers are attached, not by this.
     */
    public void applyToCore() {
        Level level = switch (this) {
            case QUIET, NORMAL -> Level.WARN;
            case VERBOSE -> Level.INFO;
            case DEBUG -> Level.DEBUG;
        };
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.getLogger("dev.evoworld.core").setLevel(level);
        }
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
