package dev.evoworld.runner;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The simulation told as a story: one line per thing worth mentioning.
 *
 * <p>The default view of a run. It listens to {@link SimulationEvent}s and
 * nothing else, so it never sees a candidate score or a perception - a
 * hundred-day run reads as a hundred daily summaries with the discoveries,
 * lessons, close calls and new skills in between.
 *
 * <p>Each line is stamped with the time in the world rather than the tick
 * number, because "day 3, 14:20" is how anyone watching would say it.
 */
public final class EventLog implements SimulationObserver {

    private static final Logger log = LoggerFactory.getLogger("evoworld.events");

    private final LogLevel level;

    public EventLog() {
        this(LogLevel.NORMAL);
    }

    public EventLog(LogLevel level) {
        this.level = level;
    }

    /**
     * Whether an event is worth a line at this level.
     *
     * <p>The daily summary is never printed here: at normal and above the
     * status report says the same and more, and quiet is meant to be quiet. At
     * quiet, only the moments that change the story get through.
     */
    boolean shows(SimulationEvent event) {
        return switch (event) {
            case SimulationEvent.DayPassed day -> false;
            case SimulationEvent.SkillImproved skill -> level.atLeast(LogLevel.NORMAL);
            case SimulationEvent.Discovered found ->
                    found.discovery().firstEver() || level.atLeast(LogLevel.NORMAL);
            default -> true;
        };
    }

    @Override
    public void onSimulationStarted(WorldState state) {
        log.info("[{}] the world begins: {} with {} resource node(s)",
                when(state.time()), state.bounds(), state.resources().size());
    }

    @Override
    public void onEvent(SimulationEvent event) {
        if (!shows(event)) {
            return;
        }
        String line = "[" + when(event.time()) + "] " + event.describe();
        switch (event) {
            case SimulationEvent.Died died -> log.warn("{}", line);
            case SimulationEvent.DangerBegan danger -> log.warn("{}", line);
            default -> log.info("{}", line);
        }
    }

    @Override
    public void onSimulationStopped(WorldState state, String reason) {
        log.info("[{}] the run ends: {}", when(state.time()), reason);
    }

    /** "day 3 14:20" - in-world time, counted from the first day. */
    public static String when(SimulationTime time) {
        Duration elapsed = time.elapsed();
        return String.format(
                Locale.ROOT,
                "day %d %02d:%02d",
                elapsed.toDays() + 1,
                elapsed.toHoursPart(),
                elapsed.toMinutesPart());
    }
}
