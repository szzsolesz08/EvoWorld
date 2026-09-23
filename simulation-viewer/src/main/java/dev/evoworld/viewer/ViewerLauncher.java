package dev.evoworld.viewer;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.evoworld.core.Simulation;
import dev.evoworld.persistence.SaveFile;
import dev.evoworld.runner.EventLog;
import dev.evoworld.runner.LogLevel;

/**
 * Opens a window and plays a run in it, live.
 *
 * <p>The terminal keeps a quiet narration - first discoveries, lessons, close
 * calls, recoveries, death - so there is a record of the run once the window
 * is closed.
 */
public final class ViewerLauncher {

    private ViewerLauncher() {
    }

    public static void main(String[] args) {
        ViewerOptions options;
        try {
            options = ViewerOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        LogLevel.QUIET.applyToCore();

        Simulation simulation = options.load()
                .map(SaveFile::load)
                .orElseGet(() -> options.scenario().build(options.config()));
        simulation.addObserver(new EventLog(LogLevel.QUIET));

        Lwjgl3ApplicationConfiguration window = new Lwjgl3ApplicationConfiguration();
        window.setTitle("EvoWorld - seed " + simulation.config().seed()
                + (options.load().isPresent() ? " (loaded)" : ", " + options.scenario().name()));
        // Room for a whole 64-tile chunk beside the panel.
        window.setWindowedMode(1600, 1000);
        window.setWindowSizeLimits(1100, 720, -1, -1);
        window.useVsync(true);
        window.setForegroundFPS(60);
        new Lwjgl3Application(new ViewerScreen(
                simulation, options.speedIndex(), options.screenshot(), options.after()), window);
    }
}
