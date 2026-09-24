package dev.evoworld.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.item.Item;
import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.resource.ResourceType;
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
        World world = WorldGenerator.forConfig(config);
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
    @DisplayName("a save taken in the middle of a night's sleep sleeps on to the same morning")
    void aSleepSurvivesASave() {
        Simulation probe = fresh(7L);
        long midSleep = -1;
        for (long tick = 1; tick <= 3 * TOTAL && midSleep < 0; tick++) {
            probe.tick();
            SavedAction underway = probe.save().agents().get(0).underway();
            if (underway != null && underway.kind().equals(SleepAction.NAME)
                    && probe.save().agents().get(0).underwayTicksRemaining() > 5) {
                midSleep = tick;
            }
        }
        assertTrue(midSleep > 0, "the run should sleep at some point");

        Simulation unbroken = fresh(7L);
        Transcript whole = new Transcript();
        unbroken.addObserver(whole);
        unbroken.run(midSleep + 120);

        Simulation interrupted = fresh(7L);
        interrupted.run(midSleep);
        Simulation resumed = Simulation.load(interrupted.save());
        Transcript rest = new Transcript();
        resumed.addObserver(rest);
        resumed.run(120);

        assertEquals(whole.from(midSleep), rest.lines);
        assertEquals(unbroken.save(), resumed.save());
    }

    @Test
    @DisplayName("a save taken part-way to somewhere carries on to the same place after loading")
    void aJourneySurvivesASave() {
        // Find a tick where the agent has set out somewhere and not yet arrived.
        Simulation probe = fresh(7L);
        long midJourney = -1;
        for (long tick = 1; tick <= TOTAL && midJourney < 0; tick++) {
            probe.tick();
            Agent agent = atlas(probe);
            long now = tick;
            if (agent.intent().filter(intent -> intent.startingDistance() >= 3
                    && !intent.isReachedFrom(agent.location())
                    && intent.formedAtTick() < now).isPresent()) {
                midJourney = tick;
            }
        }
        assertTrue(midJourney > 0, "the run should travel somewhere at some point");

        Simulation unbroken = fresh(7L);
        Transcript whole = new Transcript();
        unbroken.addObserver(whole);
        unbroken.run(midJourney + 50);

        Simulation interrupted = fresh(7L);
        interrupted.run(midJourney);
        SavedSimulation saved = interrupted.save();
        assertTrue(saved.agents().get(0).intent() != null, "the intent should be in the save");
        Simulation resumed = Simulation.load(saved);
        assertEquals(atlas(interrupted).intent(), atlas(resumed).intent());
        Transcript rest = new Transcript();
        resumed.addObserver(rest);
        resumed.run(50);

        assertEquals(whole.from(midJourney), rest.lines, "the same steps, to the same place");
        assertEquals(unbroken.save(), resumed.save());
    }

    @Test
    @DisplayName("a save taken part-way through mending carries on mending exactly as before")
    void aRecoverySurvivesASave() {
        Simulation unbroken = hurt(5L);
        Transcript whole = new Transcript();
        unbroken.addObserver(whole);
        unbroken.run(300);

        Simulation interrupted = hurt(5L);
        interrupted.run(60);
        double healthAtSave = atlas(interrupted).needs().health();
        assertTrue(healthAtSave > 30.0 && healthAtSave < 100.0, "mid-recovery: " + healthAtSave);
        Simulation resumed = Simulation.load(interrupted.save());
        Transcript rest = new Transcript();
        resumed.addObserver(rest);
        resumed.run(240);

        assertEquals(whole.from(60), rest.lines);
        assertEquals(unbroken.save(), resumed.save());
    }

    private static Simulation hurt(long seed) {
        SimulationConfig config = SimulationConfig.defaults(seed);
        World world = WorldGenerator.forConfig(config);
        world.addAgent(new Agent(
                AgentId.of("agent-1"), "Atlas", config.bounds().center(),
                new Needs(0.0, 0.0, 100.0, 30.0)));
        return new Simulation(world, DeliberativeDecisionMaker.from(config), config);
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
        // An endless world is saved as the chunks the run has actually met,
        // plus whatever in them is no longer what the terrain would build.
        // Counting nodes would be counting the seed back to itself.
        assertFalse(saved.chunks().isEmpty(), "the ground it has walked");
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
                current.chunks(),
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

    @Test
    @DisplayName("a save stays small however far it has walked: chunks, not their contents")
    void savesDoNotGrowWithTheWorld() {
        Simulation walked = fresh(7L);
        walked.run(1500);

        SavedSimulation saved = walked.save();
        long nodesInTheWorld = walked.world().resources().size();

        assertFalse(saved.chunks().isEmpty(), "it has met some country");
        // A chunk can be built again from the seed, so only what is no longer
        // what the seed would build has to be written down. A save that listed
        // every bush would grow without limit as the agent walked.
        assertTrue(
                saved.resources().size() < nodesInTheWorld / 4,
                saved.resources().size() + " nodes saved for " + nodesInTheWorld + " in the world");
    }

    @Test
    @DisplayName("a run resumed mid-walk goes on into country neither half had seen")
    void resumingCarriesOnIntoNewCountry() {
        // Long enough that it certainly moves on: an agent with a flask to
        // fill and wood to find stays put more than it used to.
        Simulation unbroken = fresh(3L);
        unbroken.run(2400);

        Simulation before = fresh(3L);
        before.run(1200);
        Simulation resumed = Simulation.load(before.save());
        int chunksAtTheBreak = resumed.world().chunkCount();
        resumed.run(1200);

        assertEquals(unbroken.snapshot(), resumed.snapshot());
        assertTrue(
                resumed.world().chunkCount() > chunksAtTheBreak,
                "it should have walked into new country after loading");
        assertEquals(
                unbroken.world().chunkCount(),
                resumed.world().chunkCount(),
                "and met exactly the country it would have met");
    }

    @Test
    @DisplayName("what it has made, and what is in it, comes back from a save")
    void theKitSurvivesASave() {
        Simulation before = fresh(1L);
        before.run(600);
        Agent had = atlas(before);
        had.acquire(Item.made(ItemKind.FLASK));
        had.store(ResourceType.WATER, 5.0);
        had.use(ItemKind.FLASK);

        Agent back = atlas(Simulation.load(before.save()));

        assertEquals(had.belongings(), back.belongings(), "the same kit, worn the same amount");
        assertEquals(
                had.inventory().quantityOf(ResourceType.WATER),
                back.inventory().quantityOf(ResourceType.WATER),
                1e-9,
                "with the same water still in it");
        assertEquals(had.inventory().capacity(), back.inventory().capacity(), 1e-9,
                "and the same room left in the pack");
    }

    @Test
    @DisplayName("a pack does not shrink a little every time the run is saved and loaded")
    void theePackKeepsItsSize() {
        Simulation before = fresh(1L);
        atlas(before).acquire(Item.made(ItemKind.FLASK));
        double afterOne = atlas(before).inventory().capacity();

        Simulation reloaded = Simulation.load(Simulation.load(before.save()).save());

        assertEquals(afterOne, atlas(reloaded).inventory().capacity(), 1e-9);
    }

    @Test
    @DisplayName("a run with a flask in it resumes exactly, kit and all")
    void resumingWithAKit() {
        Simulation unbroken = fresh(1L);
        unbroken.run(1200);

        Simulation before = fresh(1L);
        before.run(600);
        Simulation resumed = Simulation.load(before.save());
        resumed.run(600);

        assertEquals(unbroken.snapshot(), resumed.snapshot());
        assertEquals(atlas(unbroken).belongings(), atlas(resumed).belongings());
    }
}
