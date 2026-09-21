package dev.evoworld.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How the simulation handles actions that take more than one tick. */
class MultiTickActionTest {

    private static final double EPSILON = 1e-9;
    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final Location SPOT = CONFIG.bounds().center();

    /** Always proposes the same action, so the test is about timing, not choice. */
    private record FixedDecisionMaker(Action action) implements DecisionMaker {
        @Override
        public Deliberation deliberate(
                Perception perception, Motivation motivation, SimulationRandom random) {
            Decision only = new Decision(action, 1.0, "fixed for the test");
            return new Deliberation(List.of(only), only);
        }
    }

    private static final class Recorder implements SimulationObserver {
        final List<ActionOutcome> outcomes = new ArrayList<>();
        final List<Long> decisions = new ArrayList<>();
        final List<Long> continuations = new ArrayList<>();

        @Override
        public void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
            decisions.add(tick);
        }

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            outcomes.add(outcome);
        }

        @Override
        public void onActionContinued(
                long tick, AgentId agentId, String actionName, long ticksRemaining) {
            continuations.add(tick);
        }
    }

    private Agent agent;
    private World world;

    private Simulation gatheringSimulation(double berriesAvailable) {
        world = new World(CONFIG.bounds());
        // An unusually generous bush, so a test can gather repeatedly from it.
        world.addResource(
                new ResourceNode(ResourceType.BERRIES, SPOT, berriesAvailable, berriesAvailable));
        agent = new Agent(AgentId.of("agent-1"), "Atlas", SPOT, new Needs(50.0, 50.0, 80.0, 100.0));
        world.addAgent(agent);
        return new Simulation(world, new FixedDecisionMaker(new GatherAction(ResourceType.BERRIES, 4.0, 1.0)), CONFIG);
    }

    @Test
    @DisplayName("a two-tick action takes two ticks and lands once")
    void multiTickActionCompletesOnTheLastTick() {
        Simulation simulation = gatheringSimulation(12.0);
        Recorder recorder = new Recorder();
        simulation.addObserver(recorder);

        simulation.tick();
        assertTrue(recorder.outcomes.isEmpty(), "nothing should have happened yet");
        assertEquals(List.of(1L), recorder.continuations);
        assertTrue(agent.inventory().isEmpty());

        simulation.tick();
        assertEquals(1, recorder.outcomes.size());
        assertTrue(recorder.outcomes.get(0).succeeded());
        assertEquals(4.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
    }

    @Test
    @DisplayName("an agent part-way through a job does not stop to reconsider")
    void busyAgentsDoNotDeliberate() {
        Simulation simulation = gatheringSimulation(40.0);
        Recorder recorder = new Recorder();
        simulation.addObserver(recorder);

        simulation.run(6);

        assertEquals(List.of(1L, 3L, 5L), recorder.decisions, "one decision per two-tick job");
        assertEquals(3, recorder.outcomes.size());
    }

    @Test
    @DisplayName("time passes for the body on every tick of a long action")
    void metabolismRunsThroughoutTheAction() {
        Simulation simulation = gatheringSimulation(12.0);

        simulation.run(2);

        assertEquals(2L, agent.ticksAlive());
        assertEquals(50.0 + 2 * CONFIG.metabolism().perTick(CONFIG.tickDuration()).hunger(),
                agent.needs().hunger(), EPSILON);
    }

    @Test
    @DisplayName("a job overtaken by events is blocked at the end rather than applied")
    void staleActionsAreBlockedOnCompletion() {
        Simulation simulation = gatheringSimulation(12.0);
        Recorder recorder = new Recorder();
        simulation.addObserver(recorder);

        simulation.tick();
        // Circumstances move on mid-job: by the time the work is done there is
        // no room left for what it would yield. (Stripping the bush would not
        // do - the environment regrows a little before the agent finishes.)
        agent.store(ResourceType.STONE, agent.inventory().freeSpace());
        simulation.tick();

        assertEquals(1, recorder.outcomes.size());
        assertTrue(recorder.outcomes.get(0).wasBlocked(), "a stale precondition must block");
        assertEquals(
                0.0,
                agent.inventory().quantityOf(ResourceType.BERRIES),
                EPSILON,
                "a blocked job must not pay out");
    }

    @Test
    @DisplayName("a single-tick action still completes on its own tick")
    void singleTickActionsAreUnaffected() {
        World world = new World(CONFIG.bounds());
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", SPOT, new Needs(0.0, 0.0, 50.0, 100.0));
        world.addAgent(agent);
        Simulation simulation = new Simulation(
                world, new FixedDecisionMaker(new dev.evoworld.core.action.RestAction()), CONFIG);
        Recorder recorder = new Recorder();
        simulation.addObserver(recorder);

        simulation.tick();

        assertEquals(1, recorder.outcomes.size());
        assertTrue(recorder.continuations.isEmpty());
    }

    @Test
    @DisplayName("multi-tick actions do not break determinism")
    void staysDeterministic() {
        Simulation first = gatheringSimulation(40.0);
        first.run(9);
        var firstState = first.snapshot();

        Simulation second = gatheringSimulation(40.0);
        second.run(9);

        assertEquals(firstState, second.snapshot());
    }

    @Test
    @DisplayName("an agent that dies mid-job leaves no work behind")
    void deathClearsUnfinishedWork() {
        world = new World(CONFIG.bounds());
        world.addResource(ResourceNode.full(ResourceType.BERRIES, SPOT));
        agent = new Agent(
                AgentId.of("agent-1"), "Atlas", SPOT, new Needs(Needs.MAX, Needs.MAX, 80.0, 1.0));
        world.addAgent(agent);
        Simulation simulation = new Simulation(
                world, new FixedDecisionMaker(new GatherAction(ResourceType.BERRIES, 4.0, 1.0)), CONFIG);

        long processed = simulation.run(10);

        assertFalse(agent.isAlive());
        assertTrue(processed < 10, "the run should stop once nobody is alive");
        assertTrue(agent.inventory().isEmpty(), "an unfinished job must not pay out");
    }
}
