package dev.evoworld.runner.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.world.WorldGenerator;
import dev.evoworld.runner.EvoWorldRunner;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each scenario builds what it says it does, and only that. */
class ScenarioTest {

    private static Simulation built(Scenario scenario) {
        return scenario.build(7L);
    }

    @Test
    @DisplayName("the baseline is exactly the run the console builds")
    void baselineIsTheConsoleRun() {
        Simulation survey = built(Scenario.BASELINE);
        Simulation console = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
        survey.run(300);
        console.run(300);

        assertEquals(console.save(), survey.save());
    }

    @Test
    @DisplayName("hourly changes the tick length and nothing else")
    void hourlyTicks() {
        Simulation hourly = built(Scenario.HOURLY);

        assertEquals(Duration.ofMinutes(60), hourly.config().tickDuration());
        assertEquals(SimulationConfig.defaults(7L).metabolism(), hourly.config().metabolism());
    }

    @Test
    @DisplayName("wounded starts the agent at health 30, and otherwise whole")
    void woundedStart() {
        Needs body = built(Scenario.WOUNDED).world().agents().get(0).needs();

        assertEquals(new Needs(0.0, 0.0, 100.0, 30.0), body);
    }

    @Test
    @DisplayName("hungry-world makes hunger and thirst rise half as fast again, and nothing else")
    void hungrierBody() {
        MetabolismRates normal = MetabolismRates.defaults();
        MetabolismRates hungry = built(Scenario.HUNGRY_WORLD).config().metabolism();

        assertEquals(normal.hunger() * 1.5, hungry.hunger(), 1e-12);
        assertEquals(normal.thirst() * 1.5, hungry.thirst(), 1e-12);
        assertEquals(normal.starvation(), hungry.starvation());
        assertEquals(normal.restingHealing(), hungry.restingHealing());
    }

    @Test
    @DisplayName("sparse has half of every kind of resource, and at least one of each")
    void sparseWorld() {
        int full = WorldGenerator.DEFAULT_PLAN.stream().mapToInt(WorldGenerator.ResourcePlan::nodeCount).sum();
        int sparse = built(Scenario.SPARSE).world().resources().size();

        assertEquals(16, full);
        assertEquals(7, sparse, "1 water, 3 berries, 2 wood, 1 stone");
        assertTrue(Scenario.SPARSE.resources().stream().allMatch(plan -> plan.nodeCount() >= 1));
    }

    @Test
    @DisplayName("scenarios are found by name, and every name is distinct")
    void lookup() {
        List<String> names = Scenario.ALL.stream().map(Scenario::name).toList();

        assertEquals(names.size(), names.stream().distinct().count());
        names.forEach(name -> assertEquals(name, Scenario.named(name).orElseThrow().name()));
        assertTrue(Scenario.named("nonsense").isEmpty());
    }
}
