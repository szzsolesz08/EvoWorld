package dev.evoworld.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Driving hand-picked actions through the real simulation. */
class ScriptedRunTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(42L);

    private static final class OutcomeRecorder implements SimulationObserver {
        final List<ActionOutcome> outcomes = new ArrayList<>();

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            outcomes.add(outcome);
        }

        List<String> namesOf(boolean succeeded) {
            return outcomes.stream()
                    .filter(outcome -> outcome.succeeded() == succeeded)
                    .map(ActionOutcome::actionName)
                    .distinct()
                    .toList();
        }
    }

    private List<Action> forageScript() {
        World world = WorldGenerator.generate(CONFIG);
        return ActionScripts.forage(world, CONFIG.bounds().center(), CONFIG);
    }

    @Test
    @DisplayName("the forage script uses every action the milestone added")
    void scriptCoversEveryAction() {
        List<Class<?>> kinds = forageScript().stream()
                .map(Object::getClass)
                .distinct()
                .toList();

        assertTrue(kinds.contains(MoveAction.class), kinds.toString());
        assertTrue(kinds.contains(GatherAction.class), kinds.toString());
        assertTrue(kinds.contains(EatAction.class), kinds.toString());
        assertTrue(kinds.contains(DrinkAction.class), kinds.toString());
        assertTrue(kinds.contains(RestAction.class), kinds.toString());
    }

    @Test
    @DisplayName("a script built from a seed is the same script every time")
    void scriptIsDeterministic() {
        assertEquals(forageScript(), forageScript());
    }

    @Test
    @DisplayName("running the script feeds and waters the agent")
    void scriptedRunFeedsTheAgent() {
        Simulation simulation = EvoWorldRunner.buildSimulation(CONFIG, true);
        OutcomeRecorder recorder = new OutcomeRecorder();
        simulation.addObserver(recorder);

        simulation.run(120);

        assertTrue(recorder.namesOf(true).contains(GatherAction.NAME), "should have gathered");
        assertTrue(recorder.namesOf(true).contains(EatAction.NAME), "should have eaten");
        assertTrue(recorder.namesOf(true).contains(DrinkAction.NAME), "should have drunk");

        var agent = simulation.snapshot().agents().get(0);
        assertTrue(agent.alive(), "the script should keep the agent alive over its course");
        assertTrue(
                agent.needs().thirst() < 100.0,
                "drinking should have held thirst off: " + agent.needs());
    }

    @Test
    @DisplayName("the agent ends up carrying what it gathered and did not eat")
    void gatheredFoodEndsUpInTheInventory() {
        Simulation simulation = EvoWorldRunner.buildSimulation(CONFIG, true);

        simulation.run(40);

        var inventory = simulation.snapshot().agents().get(0).inventory();
        assertTrue(
                inventory.quantityOf(ResourceType.BERRIES) > 0.0,
                "should still be carrying berries: " + inventory);
    }

    @Test
    @DisplayName("a scripted run is reproducible")
    void scriptedRunIsDeterministic() {
        Simulation first = EvoWorldRunner.buildSimulation(CONFIG, true);
        first.run(60);
        Simulation second = EvoWorldRunner.buildSimulation(CONFIG, true);
        second.run(60);

        assertEquals(first.snapshot(), second.snapshot());
    }

    @Test
    @DisplayName("a script that runs out leaves the agent idling, not crashing")
    void scriptOutlivesItself() {
        ScriptedDecisionMaker scripted =
                new ScriptedDecisionMaker(List.of(new RestAction(6.0)), 6.0);
        World world = WorldGenerator.generate(CONFIG);
        world.addAgent(
                dev.evoworld.core.agent.Agent.spawn("agent-1", "Atlas", CONFIG.bounds().center()));
        Simulation simulation = new Simulation(world, scripted, CONFIG);

        simulation.run(20);

        assertEquals(0, scripted.stepsRemaining());
        assertEquals(20L, simulation.currentTick());
    }

    @Test
    @DisplayName("the unscripted run is still the autonomous one")
    void unscriptedRunsUseTheDecisionMaker() {
        Simulation scripted = EvoWorldRunner.buildSimulation(CONFIG, true);
        Simulation autonomous = EvoWorldRunner.buildSimulation(CONFIG, false);

        scripted.run(30);
        autonomous.run(30);

        assertFalse(
                scripted.snapshot().agents().equals(autonomous.snapshot().agents()),
                "a script and a decision maker should not behave identically");
    }
}
