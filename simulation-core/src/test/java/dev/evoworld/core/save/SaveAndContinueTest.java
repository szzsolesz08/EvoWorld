package dev.evoworld.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Save, load, continue - and end up exactly where an unbroken run would have.
 *
 * <p>"Equivalent" here means identical, not similar: the same decisions on the
 * same ticks, the same events, the same random draws, and the same body, mind
 * and world at the end. A resumed run that merely looked plausible would pass
 * a weaker test and still be a different simulation.
 */
class SaveAndContinueTest {

    private static final long TOTAL = 900L;

    /** Everything a run says, tick by tick, as comparable text. */
    private static final class Transcript implements SimulationObserver {
        final List<String> lines = new ArrayList<>();

        @Override
        public void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
            lines.add("t" + tick + " chose " + chosen);
        }

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            lines.add("t" + tick + " " + outcome);
        }

        @Override
        public void onEvent(SimulationEvent event) {
            lines.add("t" + event.tick() + " event " + event.describe());
        }

        List<String> from(long tick) {
            return lines.stream()
                    .filter(line -> Long.parseLong(line.substring(1, line.indexOf(' '))) > tick)
                    .toList();
        }
    }

    private static Simulation fresh(long seed) {
        SimulationConfig config = SimulationConfig.defaults(seed);
        World world = WorldGenerator.generate(config);
        world.addAgent(Agent.spawn("agent-1", "Atlas", config.bounds().center()));
        return new Simulation(world, DeliberativeDecisionMaker.from(config), config);
    }

    private static Agent atlas(Simulation simulation) {
        return simulation.world().agents().get(0);
    }

    /** Everything about an agent's mind, as values that compare exactly. */
    private static List<Object> mindOf(Agent agent) {
        return List.of(
                agent.knowledge(),
                agent.skills(),
                agent.memory().contents(),
                agent.exploration().records(),
                agent.exploration().knownBounds());
    }

    @ParameterizedTest(name = "saved at tick {0}")
    @ValueSource(longs = {1L, 2L, 37L, 150L, 151L, 333L, 500L, 777L, 899L})
    @DisplayName("save, load and continue is indistinguishable from never having stopped")
    void resumingIsExact(long saveAt) {
        Simulation unbroken = fresh(7L);
        Transcript whole = new Transcript();
        unbroken.addObserver(whole);
        unbroken.run(TOTAL);

        Simulation before = fresh(7L);
        before.run(saveAt);
        Simulation resumed = Simulation.load(before.save());
        Transcript rest = new Transcript();
        resumed.addObserver(rest);
        resumed.run(TOTAL - saveAt);

        assertEquals(whole.from(saveAt), rest.lines, "the continuation should say exactly the same");
        assertEquals(unbroken.snapshot(), resumed.snapshot(), "world, body and time should match");
        assertEquals(mindOf(atlas(unbroken)), mindOf(atlas(resumed)), "and so should the mind");
        assertEquals(unbroken.randomDrawCount(), resumed.randomDrawCount());
        assertEquals(unbroken.save(), resumed.save(), "down to the last saved field");
    }

    @Test
    @DisplayName("a save taken in the middle of a two-tick job finishes the job after loading")
    void unfinishedWorkSurvivesASave() {
        // Find a tick where the agent is part-way through a gather.
        Simulation probe = fresh(7L);
        long midJob = -1;
        for (long tick = 1; tick <= TOTAL && midJob < 0; tick++) {
            probe.tick();
            if (probe.save().agents().get(0).underway() != null) {
                midJob = tick;
            }
        }
        assertTrue(midJob > 0, "the run should gather something at some point");

        Simulation unbroken = fresh(7L);
        unbroken.run(midJob + 50);
        Simulation interrupted = fresh(7L);
        interrupted.run(midJob);
        Simulation resumed = Simulation.load(interrupted.save());
        resumed.run(50);

        assertEquals(unbroken.snapshot(), resumed.snapshot());
    }

    @Test
    @DisplayName("saving is looking, not touching: a run that was saved is the run it would have been")
    void savingChangesNothing() {
        Simulation plain = fresh(3L);
        plain.run(400);

        Simulation saved = fresh(3L);
        for (int i = 0; i < 400; i++) {
            saved.run(1);
            saved.save();
        }

        assertEquals(plain.snapshot(), saved.snapshot());
        assertEquals(plain.save(), saved.save());
    }

    @Test
    @DisplayName("loading a save and saving it again gives back the same save")
    void aSaveRoundTrips() {
        Simulation simulation = fresh(11L);
        simulation.run(600);
        SavedSimulation saved = simulation.save();

        assertEquals(saved, Simulation.load(saved).save());
    }

    @Test
    @DisplayName("everything the brief names is in the save")
    void theSaveHoldsWhatMatters() {
        Simulation simulation = fresh(7L);
        simulation.run(600);

        SavedSimulation saved = simulation.save();
        SavedAgent agent = saved.agents().get(0);

        assertEquals(600L, saved.tick(), "simulation time");
        assertEquals(7L, saved.config().seed(), "seed");
        assertTrue(saved.randomState() >= 0L, "generator state");
        assertEquals(16, saved.resources().size(), "world state");
        assertTrue(agent.health() > 0.0, "needs");
        assertTrue(!agent.memories().isEmpty(), "memories");
        assertTrue(!agent.beliefs().isEmpty(), "knowledge");
        assertTrue(!agent.explored().isEmpty(), "discovered information");
        assertTrue(agent.explored().stream().anyMatch(region -> !region.found().isEmpty()),
                "including what was found where");
        assertTrue(!agent.skills().isEmpty(), "skills");
    }

    @Test
    @DisplayName("a save from a different format version is refused rather than misread")
    void refusesAnUnknownFormat() {
        SavedSimulation current = fresh(1L).save();
        SavedSimulation future = new SavedSimulation(
                SavedSimulation.FORMAT_VERSION + 1,
                current.config(),
                current.tick(),
                current.randomState(),
                current.randomDraws(),
                current.announced(),
                current.resources(),
                current.agents());

        assertThrows(IllegalArgumentException.class, () -> Simulation.load(future));
    }

    @Test
    @DisplayName("an arrival is announced once, not again every time the run is resumed")
    void resumingDoesNotAnnounceArrivalsAgain() {
        Simulation first = fresh(7L);
        first.run(10);
        Simulation resumed = Simulation.load(first.save());
        Transcript transcript = new Transcript();
        resumed.addObserver(transcript);

        resumed.run(10);

        assertTrue(transcript.lines.stream().noneMatch(line -> line.contains("came into the world")));
    }
}
