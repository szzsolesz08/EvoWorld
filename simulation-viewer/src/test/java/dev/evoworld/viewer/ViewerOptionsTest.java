package dev.evoworld.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.runner.RunnerOptions;
import dev.evoworld.runner.survey.Scenario;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ViewerOptionsTest {

    @Test
    @DisplayName("with no options: the console's default run, at 4 ticks a second")
    void defaults() {
        ViewerOptions options = ViewerOptions.parse(new String[0]);

        assertEquals(RunnerOptions.DEFAULT_SEED, options.seed());
        assertEquals(Scenario.BASELINE, options.scenario());
        assertEquals(4.0, ViewerModel.SPEEDS[options.speedIndex()]);
        assertEquals(Optional.empty(), options.load());
        assertEquals(Optional.empty(), options.screenshot());
        assertEquals(Duration.ofMinutes(10), options.config().tickDuration());
    }

    @Test
    @DisplayName("everything can be set, and a scenario keeps its own body unless told otherwise")
    void parsesEverything() {
        ViewerOptions options = ViewerOptions.parse(new String[] {
            "--seed", "10", "--scenario", "hungry-world", "--tick-minutes", "60", "--speed", "30",
            "--load", "saves/a.json", "--screenshot", "shot.png", "--after", "50"});

        assertEquals(10L, options.seed());
        assertEquals(Scenario.HUNGRY_WORLD, options.scenario());
        assertEquals(Duration.ofMinutes(60), options.config().tickDuration());
        assertEquals(MetabolismRates.defaults().hunger() * 1.5,
                options.config().metabolism().hunger(), 1e-12);
        assertEquals(30.0, ViewerModel.SPEEDS[options.speedIndex()]);
        assertEquals(Optional.of(Path.of("saves/a.json")), options.load());
        assertEquals(Optional.of(Path.of("shot.png")), options.screenshot());
        assertEquals(50L, options.after());
    }

    @Test
    @DisplayName("a speed between steps goes to the nearest one")
    void nearestSpeed() {
        assertEquals(15.0, ViewerModel.SPEEDS[ViewerOptions.speedIndexFor(14)]);
        assertEquals(1.0, ViewerModel.SPEEDS[ViewerOptions.speedIndexFor(0)]);
        assertEquals(120.0, ViewerModel.SPEEDS[ViewerOptions.speedIndexFor(1000)]);
    }

    @Test
    @DisplayName("nonsense is refused with a reason")
    void refusesNonsense() {
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--bogus"}));
        assertThrows(IllegalArgumentException.class,
                () -> ViewerOptions.parse(new String[] {"--scenario", "nonsense"}));
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--seed"}));
        assertThrows(IllegalArgumentException.class,
                () -> ViewerOptions.parse(new String[] {"--tick-minutes", "0"}));
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--after", "-1"}));
    }
}
