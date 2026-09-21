package dev.evoworld.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.runner.EvoWorldRunner;
import dev.evoworld.runner.survey.Scenario;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pacing, pausing, stepping and the feed - everything the window does, without a window. */
class ViewerModelTest {

    private static final int FOUR_A_SECOND = 2;

    private static Simulation fresh() {
        return EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
    }

    private static ViewerModel model() {
        return new ViewerModel(fresh(), FOUR_A_SECOND);
    }

    private static long tick(ViewerModel model) {
        return model.state().tick();
    }

    @Test
    @DisplayName("a second at 4 ticks a second runs 4 ticks")
    void runsAtTheChosenSpeed() {
        ViewerModel model = model();

        assertEquals(4, model.advance(1.0));
        assertEquals(4L, tick(model));
    }

    @Test
    @DisplayName("short frames add up: ten tenths of a second still make 4 ticks")
    void fractionsAccumulate() {
        ViewerModel model = model();
        for (int i = 0; i < 10; i++) {
            model.advance(0.1);
        }

        assertEquals(4L, tick(model));
    }

    @Test
    @DisplayName("paused, time passes and nothing happens; a step is exactly one tick")
    void pauseAndStep() {
        ViewerModel model = model();
        model.togglePause();

        assertEquals(0, model.advance(5.0));
        model.stepOnce();
        assertEquals(1L, tick(model));

        model.togglePause();
        assertEquals(4, model.advance(1.0));
    }

    @Test
    @DisplayName("speed moves along the ladder and stops at both ends")
    void speedLadder() {
        ViewerModel model = model();
        for (int i = 0; i < 20; i++) {
            model.faster();
        }
        assertEquals(ViewerModel.SPEEDS[ViewerModel.SPEEDS.length - 1], model.ticksPerSecond());
        for (int i = 0; i < 20; i++) {
            model.slower();
        }
        assertEquals(ViewerModel.SPEEDS[0], model.ticksPerSecond());
    }

    @Test
    @DisplayName("a long stall does not come back as a burst: one frame runs at most its share")
    void framesAreCapped() {
        ViewerModel model = model();
        for (int i = 0; i < 10; i++) {
            model.faster();
        }

        assertEquals(ViewerModel.MAX_TICKS_PER_FRAME, model.advance(30.0));
        assertTrue(model.advance(0.0) <= 1, "the backlog is dropped, not carried");
    }

    @Test
    @DisplayName("watching changes nothing: however the frames fall, the run is the one run() would give")
    void watchingIsTheSameRun() {
        Simulation watched = fresh();
        ViewerModel model = new ViewerModel(watched, ViewerModel.SPEEDS.length - 1);
        double[] frames = {0.016, 0.033, 0.1, 0.007, 0.05};
        for (int i = 0; tick(model) < 300; i++) {
            double frame = frames[i % frames.length];
            // Never let one frame carry the run past tick 300.
            if (tick(model) + Math.ceil(frame * model.ticksPerSecond()) > 300) {
                model.stepOnce();
            } else {
                model.advance(frame);
            }
        }
        Simulation unwatched = fresh();
        unwatched.run(300);

        assertEquals(unwatched.save(), watched.save());
    }

    @Test
    @DisplayName("the feed keeps the last ten events in order, without the daily summary")
    void feedKeepsTheLatest() {
        ViewerModel model = model();
        for (int i = 0; i < 600; i++) {
            model.stepOnce();
        }
        List<SimulationEvent> feed = model.feed();

        assertEquals(ViewerModel.FEED_SIZE, feed.size());
        assertTrue(feed.stream().noneMatch(SimulationEvent.DayPassed.class::isInstance));
        for (int i = 1; i < feed.size(); i++) {
            assertTrue(feed.get(i - 1).tick() <= feed.get(i).tick(), "oldest first");
        }
    }

    @Test
    @DisplayName("the arrival is in the feed from the start, because the model starts the run")
    void startsTheRun() {
        List<SimulationEvent> feed = model().feed();

        assertTrue(feed.stream().anyMatch(SimulationEvent.Born.class::isInstance), feed.toString());
    }

    @Test
    @DisplayName("once the agent is dead the clock stops, and the death is kept for the notice")
    void stopsAtDeath() {
        Simulation dying = new Scenario("dying", "about to die", 10L, SimulationConfig::defaults,
                new Needs(Needs.MAX, Needs.MAX, 50.0, 5.0), Scenario.BASELINE.resources()).build(3L);
        ViewerModel model = new ViewerModel(dying, ViewerModel.SPEEDS.length - 1);
        for (int i = 0; i < 100 && !model.isOver(); i++) {
            model.advance(1.0);
        }

        assertTrue(model.isOver());
        assertTrue(model.death().isPresent());
        assertFalse(model.status().body().alive());
        long diedAt = tick(model);
        assertEquals(0, model.advance(1.0));
        model.stepOnce();
        assertEquals(diedAt, tick(model));
    }
}
