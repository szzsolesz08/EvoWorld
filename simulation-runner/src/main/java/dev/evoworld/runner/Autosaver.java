package dev.evoworld.runner;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.world.WorldState;
import dev.evoworld.persistence.SaveFile;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves the run every so often, so a long unattended run survives being stopped.
 *
 * <p>Saves at the end of a tick, when nothing is half-done, to the same file
 * each time - each save replaces the last atomically, so there is always one
 * complete save to go back to. Saving only reads the simulation, so a run with
 * autosave on is the same run as one without.
 */
public final class Autosaver implements SimulationObserver {

    private static final Logger log = LoggerFactory.getLogger("evoworld.save");

    private final Simulation simulation;
    private final Path file;
    private final long every;

    public Autosaver(Simulation simulation, Path file, long every) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.file = Objects.requireNonNull(file, "file");
        if (every < 1) {
            throw new IllegalArgumentException("autosave needs a positive interval, was " + every);
        }
        this.every = every;
    }

    @Override
    public void onTickCompleted(WorldState state) {
        if (state.tick() % every == 0) {
            SaveFile.write(simulation, file);
            log.info("[{}] autosaved to {}", EventLog.when(state.time()), file);
        }
    }
}
