package dev.evoworld.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** An intent, and what happens to it as an agent sets out, pauses, arrives or gives up. */
class IntentTest {

    private static final String THIRST = SatisfyThirstGoal.NAME;
    private static final String HUNGER = SatisfyHungerGoal.NAME;
    private static final Location HOME = new Location(8, 8);

    @Nested
    @DisplayName("the value")
    class Value {

        @Test
        @DisplayName("remembers how far it had to go when it set off")
        void measuresTheTripFromWhereItStarted() {
            Intent intent = Intent.formed(THIRST, new Location(2, 8), HOME, 5L);

            assertEquals(new Intent(THIRST, new Location(2, 8), 5L, 6), intent);
        }

        @Test
        @DisplayName("is the same undertaking only for the same goal and the same place")
        void matchesOnGoalAndPlace() {
            Intent intent = Intent.formed(THIRST, new Location(2, 8), HOME, 0L);

            assertTrue(intent.isFor(THIRST, new Location(2, 8)));
            assertFalse(intent.isFor(HUNGER, new Location(2, 8)));
            assertFalse(intent.isFor(THIRST, new Location(3, 8)));
        }

        @Test
        @DisplayName("counts as stuck only after three times the trip, plus slack")
        void goesStaleGenerously() {
            Intent intent = Intent.formed(THIRST, new Location(2, 8), HOME, 10L);

            assertFalse(intent.isStale(10L + 6 * 3 + 10));
            assertTrue(intent.isStale(10L + 6 * 3 + 11));
        }

        @Test
        @DisplayName("reads as where it is heading and why")
        void describesItself() {
            Intent intent = Intent.formed(THIRST, new Location(2, 8), HOME, 0L);

            assertEquals("heading for " + new Location(2, 8) + " to satisfy thirst", intent.describe());
        }

        @Test
        @DisplayName("refuses a negative distance or a time before time")
        void refusesNonsense() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Intent(THIRST, HOME, 0L, -1));
            assertThrows(IllegalArgumentException.class,
                    () -> new Intent(THIRST, HOME, -1L, 0));
        }
    }

    @Nested
    @DisplayName("the lifecycle")
    class Lifecycle {

        private final Script script = new Script();

        private Simulation simulationWith(Agent agent) {
            SimulationConfig config = SimulationConfig.defaults(1L);
            World world = new World(config.bounds());
            world.addAgent(agent);
            return new Simulation(world, script, config);
        }

        private Simulation healthyAgent() {
            return simulationWith(Agent.spawn("agent-1", "Atlas", HOME));
        }

        private static Optional<Intent> intentOf(Simulation simulation) {
            return simulation.world().agents().get(0).intent();
        }

        @Test
        @DisplayName("choosing to head somewhere sets the agent out on an intent")
        void formedWhenSettingOut() {
            Simulation simulation = healthyAgent();
            script.then(heading(THIRST, new Location(8, 2)));

            simulation.tick();

            assertEquals(Optional.of(new Intent(THIRST, new Location(8, 2), 1L, 6)),
                    intentOf(simulation));
        }

        @Test
        @DisplayName("carrying on keeps the intent it has, rather than starting a new one")
        void keptWhileCarryingOn() {
            Simulation simulation = healthyAgent();
            Location spring = new Location(8, 2);
            script.then(heading(THIRST, spring))
                    .then(heading(THIRST, spring))
                    .then(heading(THIRST, spring));

            simulation.run(3);

            assertEquals(1L, intentOf(simulation).orElseThrow().formedAtTick());
        }

        @Test
        @DisplayName("heading somewhere else replaces it")
        void replacedByADifferentPlace() {
            Simulation simulation = healthyAgent();
            script.then(heading(THIRST, new Location(8, 2)))
                    .then(heading(THIRST, new Location(14, 7)));

            simulation.run(2);

            // Set out on tick 2 from (8,7), where the first step had taken it.
            assertEquals(Optional.of(new Intent(THIRST, new Location(14, 7), 2L, 6)),
                    intentOf(simulation));
        }

        @Test
        @DisplayName("heading to the same place for a different reason is a different undertaking")
        void replacedByADifferentGoal() {
            Simulation simulation = healthyAgent();
            Location bush = new Location(8, 2);
            script.then(heading(THIRST, bush)).then(heading(HUNGER, bush));

            simulation.run(2);

            assertEquals(HUNGER, intentOf(simulation).orElseThrow().goalName());
            assertEquals(2L, intentOf(simulation).orElseThrow().formedAtTick());
        }

        @Test
        @DisplayName("resting on the way is a pause, not a change of mind")
        void keptThroughAPause() {
            Simulation simulation = healthyAgent();
            Location spring = new Location(8, 2);
            script.then(heading(THIRST, spring)).then(rest()).then(rest());

            simulation.run(3);

            assertEquals(Optional.of(new Intent(THIRST, spring, 1L, 6)), intentOf(simulation));
        }

        @Test
        @DisplayName("arriving fulfils it")
        void completedOnArrival() {
            Simulation simulation = healthyAgent();
            Location spring = new Location(8, 6);
            script.then(heading(THIRST, spring)).then(heading(THIRST, spring));

            simulation.tick();
            assertTrue(intentOf(simulation).isPresent(), "one step to go");
            simulation.tick();

            assertEquals(Optional.empty(), intentOf(simulation), "arrived with the second step");
        }

        @Test
        @DisplayName("a trip that drags on far too long is given up")
        void givenUpWhenStale() {
            Simulation simulation = healthyAgent();
            // Two steps away: stale once more than 2 x 3 + 10 = 16 ticks have passed.
            script.then(heading(THIRST, new Location(8, 6)));

            simulation.run(17);
            assertTrue(intentOf(simulation).isPresent(), "16 ticks in is still within reason");
            simulation.tick();

            assertEquals(Optional.empty(), intentOf(simulation));
        }

        @Test
        @DisplayName("the dead intend nothing")
        void clearedOnDeath() {
            Agent dying = new Agent(
                    AgentId.of("agent-1"), "Atlas", HOME,
                    new Needs(Needs.MAX, Needs.MAX, 50.0, 10.0));
            Simulation simulation = simulationWith(dying);
            script.then(heading(THIRST, new Location(8, 2)));

            simulation.tick();
            assertTrue(dying.intent().isPresent());
            simulation.run(1000);

            assertFalse(dying.isAlive());
            assertEquals(Optional.empty(), dying.intent());
        }
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
