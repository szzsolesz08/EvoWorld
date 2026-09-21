package dev.evoworld.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import dev.evoworld.core.world.WorldGenerator;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(7L);

    private Simulation simulation(SimulationConfig config, Needs startingNeeds) {
        World world = new World(config.bounds());
        world.addAgent(new Agent(AgentId.of("agent-1"), "Atlas", config.bounds().center(), startingNeeds));
        return new Simulation(world, DeliberativeDecisionMaker.from(config), config);
    }

    private Simulation simulation(SimulationConfig config) {
        return simulation(config, Needs.healthy());
    }

    // --- tick progression -------------------------------------------------

    @Test
    @DisplayName("the clock advances one tick per step")
    void advancesTheClock() {
        Simulation simulation = simulation(CONFIG);

        assertEquals(0L, simulation.currentTick());
        simulation.tick();
        assertEquals(1L, simulation.currentTick());
        simulation.run(9);
        assertEquals(10L, simulation.currentTick());
    }

    @Test
    @DisplayName("run reports how many ticks it actually processed")
    void reportsProcessedTicks() {
        Simulation simulation = simulation(CONFIG);

        assertEquals(0L, simulation.run(0));
        assertEquals(12L, simulation.run(12));
        assertEquals(12L, simulation.currentTick());
    }

    @Test
    @DisplayName("simulated time advances by the configured tick duration")
    void advancesSimulatedTime() {
        SimulationConfig hourlyTicks = CONFIG.withTickDuration(Duration.ofHours(1));
        Simulation simulation = simulation(hourlyTicks);

        simulation.run(24);

        assertEquals(24L, simulation.currentTick());
        assertEquals(Duration.ofDays(1), simulation.time().elapsed());
        assertEquals(simulation.time(), simulation.snapshot().time());
    }

    @Test
    @DisplayName("tick duration changes the meaning of a tick, not the outcome of the run")
    void tickDurationDoesNotAlterBehaviour() {
        WorldState fastTicks = runAndSnapshot(CONFIG.withTickDuration(Duration.ofSeconds(1)));
        WorldState slowTicks = runAndSnapshot(CONFIG.withTickDuration(Duration.ofHours(1)));

        assertEquals(fastTicks.agents(), slowTicks.agents());
        assertNotEquals(fastTicks.time(), slowTicks.time());
    }

    @Test
    void rejectsANegativeTickCount() {
        assertThrows(IllegalArgumentException.class, () -> simulation(CONFIG).run(-1));
    }

    // --- world state updates ----------------------------------------------

    @Test
    @DisplayName("time alone makes the agent hungry and thirsty")
    void needsChangeOverTime() {
        Simulation simulation = simulation(CONFIG);

        simulation.run(5);

        AgentSnapshot agent = simulation.snapshot().agents().get(0);
        assertEquals(5 * CONFIG.metabolism().hungerPerTick(), agent.needs().hunger(), 1e-9);
        assertEquals(5 * CONFIG.metabolism().thirstPerTick(), agent.needs().thirst(), 1e-9);
    }

    @Test
    @DisplayName("every tick produces a new snapshot reflecting that tick")
    void snapshotsTrackEveryTick() {
        Simulation simulation = simulation(CONFIG);
        List<WorldState> states = new ArrayList<>();
        simulation.addObserver(new SimulationObserver() {
            @Override
            public void onTickCompleted(WorldState state) {
                states.add(state);
            }
        });

        simulation.run(4);

        assertEquals(List.of(1L, 2L, 3L, 4L), states.stream().map(WorldState::tick).toList());
        assertEquals(4, states.stream().distinct().count(), "each tick must change the world");
        assertEquals(simulation.snapshot(), states.get(states.size() - 1));
    }

    @Test
    @DisplayName("the agent never leaves the world")
    void agentStaysInsideTheWorld() {
        Simulation simulation = simulation(CONFIG);

        simulation.run(200);

        Location location = simulation.snapshot().agents().get(0).location();
        assertTrue(simulation.world().bounds().contains(location), "agent escaped to " + location);
    }

    @Test
    @DisplayName("resources regrow as the simulation ticks")
    void resourcesRegenerateOverTicks() {
        World world = new World(CONFIG.bounds());
        ResourceNode bush = ResourceNode.holding(ResourceType.BERRIES, new Location(0, 0), 0.0);
        ResourceNode outcrop = ResourceNode.holding(ResourceType.STONE, new Location(1, 0), 0.0);
        world.addResource(bush);
        world.addResource(outcrop);
        world.addAgent(new Agent(AgentId.of("agent-1"), "Atlas", CONFIG.bounds().center(), Needs.healthy()));
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);

        simulation.run(8);

        assertEquals(8 * ResourceType.BERRIES.regenPerTick(), bush.quantity(), 1e-9);
        assertEquals(0.0, outcrop.quantity(), 1e-9, "stone does not regrow");
    }

    @Test
    @DisplayName("a generated world is part of the run, so the same seed reproduces it too")
    void generatedWorldsAreDeterministic() {
        assertEquals(
                runGeneratedWorld(SimulationConfig.defaults(77L)),
                runGeneratedWorld(SimulationConfig.defaults(77L)));
    }

    @Test
    @DisplayName("a world that disagrees with its configuration is rejected")
    void rejectsAMismatchedWorld() {
        World world = new World(WorldBounds.of(4, 4));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG));
    }

    // --- determinism ------------------------------------------------------

    @Test
    @DisplayName("the same seed replays the same run, tick for tick")
    void isDeterministicForAGivenSeed() {
        assertEquals(
                runAndSnapshot(SimulationConfig.defaults(123L)),
                runAndSnapshot(SimulationConfig.defaults(123L)));
    }

    @Test
    @DisplayName("a different seed produces a different run")
    void differentSeedsDiverge() {
        // In an empty world an explorer's route is fully determined by its
        // coverage, not by chance, so seeds only show through the world they
        // generate.
        assertNotEquals(
                runGeneratedWorld(SimulationConfig.defaults(1L)),
                runGeneratedWorld(SimulationConfig.defaults(2L)));
    }

    @Test
    @DisplayName("two runs of the same config consume the random stream identically")
    void consumesRandomnessIdentically() {
        Simulation first = simulation(SimulationConfig.defaults(55L));
        Simulation second = simulation(SimulationConfig.defaults(55L));

        first.run(30);
        second.run(30);

        assertEquals(first.randomDrawCount(), second.randomDrawCount());
    }

    @Test
    @DisplayName("replaying a run reproduces every tick along the way, not just the ending")
    void replayMatchesTickByTick() {
        assertEquals(
                tickByTickStates(SimulationConfig.defaults(321L)),
                tickByTickStates(SimulationConfig.defaults(321L)));
    }

    // --- lifecycle and observation ----------------------------------------

    @Test
    @DisplayName("an agent dies of unmet needs and the run stops early")
    void stopsWhenEveryAgentIsDead() {
        Needs doomed = new Needs(Needs.MAX, Needs.MAX, 50.0, 3.0);
        Simulation simulation = simulation(CONFIG, doomed);
        List<AgentId> deaths = new ArrayList<>();
        simulation.addObserver(new SimulationObserver() {
            @Override
            public void onAgentDied(long tick, AgentId agentId) {
                deaths.add(agentId);
            }
        });

        long processed = simulation.run(100);

        assertEquals(List.of(AgentId.of("agent-1")), deaths);
        assertTrue(processed < 100, "simulation should have stopped early, ran " + processed);
        assertFalse(simulation.snapshot().agents().get(0).alive());
    }

    @Test
    @DisplayName("observers see the whole loop: decision, action and resulting state")
    void observersSeeEveryStageOfTheTick() {
        Simulation simulation = simulation(CONFIG);
        RecordingObserver observer = new RecordingObserver();
        simulation.addObserver(observer);

        simulation.run(3);

        assertEquals(1, observer.started);
        assertEquals(3, observer.ticksStarted);
        assertEquals(3, observer.decisions.size());
        assertEquals(3, observer.outcomes.size());
        assertEquals(3, observer.ticksCompleted.size());
        assertEquals(1, observer.stopped);
        assertEquals(
                List.of(1L, 2L, 3L), observer.ticksCompleted.stream().map(WorldState::tick).toList());
    }

    @Test
    @DisplayName("observing a run does not change it")
    void observationIsSideEffectFree() {
        Simulation observed = simulation(SimulationConfig.defaults(11L));
        observed.addObserver(new RecordingObserver());
        observed.run(25);

        Simulation unobserved = simulation(SimulationConfig.defaults(11L));
        unobserved.run(25);

        assertEquals(unobserved.snapshot(), observed.snapshot());
        assertEquals(unobserved.randomDrawCount(), observed.randomDrawCount());
    }

    private WorldState runGeneratedWorld(SimulationConfig config) {
        World world = WorldGenerator.generate(config);
        world.addAgent(Agent.spawn("agent-1", "Atlas", config.bounds().center()));
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(config), config);
        simulation.run(30);
        return simulation.snapshot();
    }

    private WorldState runAndSnapshot(SimulationConfig config) {
        Simulation simulation = simulation(config);
        simulation.run(40);
        return simulation.snapshot();
    }

    private List<WorldState> tickByTickStates(SimulationConfig config) {
        Simulation simulation = simulation(config);
        List<WorldState> states = new ArrayList<>();
        simulation.addObserver(new SimulationObserver() {
            @Override
            public void onTickCompleted(WorldState state) {
                states.add(state);
            }
        });
        simulation.run(40);
        return states;
    }

    private static final class RecordingObserver implements SimulationObserver {
        int started;
        int ticksStarted;
        int stopped;
        final List<Decision> decisions = new ArrayList<>();
        final List<ActionOutcome> outcomes = new ArrayList<>();
        final List<WorldState> ticksCompleted = new ArrayList<>();

        @Override
        public void onSimulationStarted(WorldState state) {
            started++;
        }

        @Override
        public void onTickStarted(long tick) {
            ticksStarted++;
        }

        @Override
        public void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
            decisions.add(chosen);
        }

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            outcomes.add(outcome);
        }

        @Override
        public void onTickCompleted(WorldState state) {
            ticksCompleted.add(state);
        }

        @Override
        public void onSimulationStopped(WorldState state, String reason) {
            stopped++;
        }
    }
}
