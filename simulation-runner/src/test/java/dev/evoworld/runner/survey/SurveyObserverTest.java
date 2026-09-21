package dev.evoworld.runner.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.ExploreGoal;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the survey counts, checked on runs where the answer is known in advance. */
class SurveyObserverTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final Location HOME = new Location(8, 8);
    private static final String HUNGER = SatisfyHungerGoal.NAME;

    private final Script script = new Script();
    private final World world = new World(CONFIG.bounds());

    private SeedResult run(Needs body, long ticks) {
        world.addAgent(new Agent(AgentId.of("agent-1"), "Atlas", HOME, body));
        Simulation simulation = new Simulation(world, script, CONFIG);
        SurveyObserver observer = new SurveyObserver(1L);
        simulation.addObserver(observer);
        simulation.run(ticks);
        return observer.result();
    }

    private SeedResult run(long ticks) {
        return run(Needs.healthy(), ticks);
    }

    @Test
    @DisplayName("stepping back and forth on consecutive ticks is a dithering run")
    void countsDithering() {
        Location east = new Location(12, 8);
        Location west = new Location(4, 8);
        script.then(heading(HUNGER, east)).then(heading(HUNGER, west))
                .then(heading(HUNGER, east)).then(heading(HUNGER, west));

        SeedResult result = run(4);

        assertEquals(3, result.turnBacks());
        assertEquals(1, result.ditheringRuns());
    }

    @Test
    @DisplayName("stepping back with something done in between is neither")
    void aPauseIsNotATurnBack() {
        script.then(heading(HUNGER, new Location(12, 8))).then(rest())
                .then(heading(HUNGER, new Location(4, 8)));

        SeedResult result = run(3);

        assertEquals(0, result.turnBacks());
        assertEquals(0, result.ditheringRuns());
    }

    @Test
    @DisplayName("a trip that arrives where there is nothing left went to nothing")
    void arrivingAtNothing() {
        // Stone never grows back, so an empty one stays empty.
        Location bare = new Location(8, 6);
        world.addResource(ResourceNode.holding(ResourceType.STONE, bare, 0.0));
        script.then(heading(HUNGER, bare)).then(heading(HUNGER, bare));

        SeedResult result = run(2);

        assertEquals(1, result.arrivals());
        assertEquals(1, result.foodTrips());
        assertEquals(0, result.foodTripsAbandoned());
        assertEquals(1, result.foodTripsToNothing());
    }

    @Test
    @DisplayName("a trip that arrives at something to have did not")
    void arrivingAtSomething() {
        Location bush = new Location(8, 6);
        world.addResource(ResourceNode.full(ResourceType.BERRIES, bush));
        script.then(heading(HUNGER, bush)).then(heading(HUNGER, bush));

        SeedResult result = run(2);

        assertEquals(1, result.foodTrips());
        assertEquals(0, result.foodTripsToNothing());
    }

    @Test
    @DisplayName("heading somewhere else before arriving abandons the trip")
    void abandoning() {
        script.then(heading(HUNGER, new Location(8, 2)))
                .then(heading(SatisfyThirstGoal.NAME, new Location(14, 8)));

        SeedResult result = run(2);

        assertEquals(1, result.foodTrips(), "the second trip is still under way");
        assertEquals(1, result.foodTripsAbandoned());
        assertEquals(0, result.arrivals());
    }

    @Test
    @DisplayName("reaching an exploring target is an arrival, not a food trip")
    void exploringIsNotFoodTrips() {
        Location lookout = new Location(8, 7);
        script.then(heading(ExploreGoal.NAME, lookout));

        SeedResult result = run(1);

        assertEquals(1, result.arrivals());
        assertEquals(0, result.foodTrips());
    }

    @Test
    @DisplayName("a death is recorded with its cause and tick, and ends the count of survivors")
    void recordsADeath() {
        SeedResult result = run(new Needs(Needs.MAX, Needs.MAX, 50.0, 10.0), 1000);

        assertFalse(result.alive());
        SeedResult.Death death = result.death().orElseThrow();
        assertEquals(result.ticksRun(), death.tick());
        assertTrue(result.dangers() >= 1);
        assertEquals(0.0, result.lowestHealth(), 1e-9);
    }

    @Test
    @DisplayName("a wounded agent that mends is counted as mended, and when")
    void recordsARecovery() {
        SeedResult result = run(new Needs(0.0, 0.0, 80.0, 95.0), 20);

        assertEquals(1, result.recoveries());
        assertEquals(95.0, result.lowestHealth(), 1e-9);
        assertTrue(result.ticksToFullHealth().isPresent());
        assertTrue(result.rests() > 0, "the script rests once it runs out");
    }

    @Test
    @DisplayName("refused actions are counted by name")
    void countsRefusals() {
        // Nothing in hand to eat: refused every time.
        script.then(new Decision(new EatAction(ResourceType.BERRIES, 4.0), 1.0, "told to"))
                .then(new Decision(new EatAction(ResourceType.BERRIES, 4.0), 1.0, "told to"));

        SeedResult result = run(new Needs(50.0, 0.0, 100.0, Needs.MAX), 2);

        assertEquals(Map.of(EatAction.NAME, 2), result.refusals());
        assertEquals(2, result.totalRefusals());
    }

    // --- a decision maker that does as it is told -------------------------

    private static Decision heading(String goal, Location target) {
        return new Decision(
                new MoveAction(target, 1.0), 1.0, "told to", Optional.of(goal), Optional.of(target));
    }

    private static Decision rest() {
        return new Decision(new RestAction(1.0), 0.1, "told to");
    }

    /** Plays back a list of decisions, and rests once it runs out. */
    private static final class Script implements DecisionMaker {
        private final Deque<Decision> next = new ArrayDeque<>();

        Script then(Decision decision) {
            next.add(decision);
            return this;
        }

        @Override
        public Deliberation deliberate(
                Perception perception, Motivation motivation, SimulationRandom random) {
            Decision decision = next.isEmpty() ? rest() : next.poll();
            return new Deliberation(List.of(decision), decision);
        }
    }
}
