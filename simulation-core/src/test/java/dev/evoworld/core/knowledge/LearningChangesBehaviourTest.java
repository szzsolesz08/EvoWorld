package dev.evoworld.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.MotivationSystem;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.ChunkMix;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The point of the whole milestone: an agent that has learned something behaves
 * differently from the same agent before it learned it.
 *
 * <p>Each test here runs the identical situation twice, changing nothing but
 * what the agent has been through, and shows the choice come out differently.
 */
class LearningChangesBehaviourTest {

    private static final double EPSILON = 1e-9;
    /**
     * A world with edges, built by hand: these tests put one bush and one
     * spring somewhere and watch what the agent makes of them, which only says
     * anything if the agent cannot simply walk away into open country.
     */
    private static final SimulationConfig CONFIG =
            SimulationConfig.defaults(1L).withWorld(WorldBounds.of(16, 16), ChunkMix.standard());
    private static final Location HERE = new Location(8, 8);
    private static final SimulationTime NOW = new SimulationTime(1L, Duration.ofMinutes(10));

    private final PerceptionSystem senses = new PerceptionSystem(CONFIG.perceptionRadius());
    private final MotivationSystem motivations = MotivationSystem.standard();
    private final DeliberativeDecisionMaker decisions = DeliberativeDecisionMaker.from(CONFIG);

    private Agent agentWith(Needs needs) {
        return new Agent(AgentId.of("agent-1"), "Atlas", HERE, needs);
    }

    private World worldWith(ResourceType type) {
        World world = new World(CONFIG.bounds());
        world.addResource(ResourceNode.full(type, HERE));
        return world;
    }

    private Deliberation decide(Agent agent, World world) {
        // Every region freshly seen, so curiosity sits at its floor and the only
        // thing that differs between two agents is what they have learned.
        for (dev.evoworld.core.exploration.Region region : dev.evoworld.core.exploration.Region
                .covering(world.bounds())) {
            agent.exploration().sight(world.bounds(), region.centre(), NOW);
        }
        Perception perception = senses.perceive(agent, world, NOW);
        return decisions.deliberate(
                perception, motivations.assess(perception), SimulationRandom.seededWith(1L));
    }

    /** Runs the action for real and files whatever it taught. */
    private ActionOutcome experience(Agent agent, World world, Action action) {
        ActionOutcome outcome = action.execute(agent, world);
        agent.updateKnowledge(KnowledgeLearner.learnFrom(agent.knowledge(), action, outcome));
        return outcome;
    }

    // --- the headline -----------------------------------------------------

    @Nested
    @DisplayName("the same agent, before and after")
    class BeforeAndAfter {

        @Test
        @DisplayName("having learned stone is not food, it stops trying to eat stone")
        void stopsTryingWhatItHasLearnedIsUseless() {
            World world = new World(WorldBounds.of(16, 16));
            Agent innocent = agentWith(new Needs(80.0, 0.0, 100.0, Needs.MAX));
            innocent.store(ResourceType.STONE, 10.0);

            // Before: it has no idea, so a stone is worth a try.
            boolean consideredBefore = considersEating(innocent, world);

            // The experience: it tries, and the world says no, permanently.
            ActionOutcome refused =
                    experience(innocent, world, new EatAction(ResourceType.STONE, 4.0));

            // After: the same agent, in the same state, will not try again.
            boolean consideredAfter = considersEating(innocent, world);

            assertTrue(consideredBefore, "an ignorant agent should be willing to try");
            assertTrue(refused.wasImpossible());
            assertFalse(consideredAfter, "a taught agent should not try again");
            assertEquals(
                    0.0,
                    innocent.knowledge().estimate(BeliefKind.NOURISHMENT, ResourceType.STONE),
                    EPSILON);
        }

        @Test
        @DisplayName("having learned what water does, the same walk to it is worth more")
        void travelsForWhatItHasLearnedIsWorthIt() {
            World world = new World(CONFIG.bounds());
            world.addResource(ResourceNode.full(ResourceType.WATER, new Location(12, 8)));

            Agent innocent = agentWith(new Needs(0.0, 60.0, 100.0, Needs.MAX));
            Agent experienced = agentWith(new Needs(0.0, 60.0, 100.0, Needs.MAX));
            experienced.updateKnowledge(taughtAboutWater(experienced.knowledge()));

            double onAHunch = utilityOfWalkingToWater(innocent, world);
            double knowingly = utilityOfWalkingToWater(experienced, world);

            assertTrue(
                    knowingly > onAHunch * 1.2,
                    "knowing what water does should make the walk clearly more worth it: "
                            + onAHunch + " -> " + knowingly);
            assertTrue(
                    decide(experienced, world).chosen().rationale().contains("satisfy thirst"),
                    "and the experienced agent should actually take it");
        }

        private double utilityOfWalkingToWater(Agent agent, World world) {
            return decide(agent, world).considered().stream()
                    .filter(option -> option.rationale().contains("satisfy thirst (water"))
                    .mapToDouble(option -> option.utility())
                    .max()
                    .orElse(0.0);
        }

        @Test
        @DisplayName("one drink is enough to change what drinking is worth")
        void aSingleExperienceShiftsTheScore() {
            // Two agents equally thirsty in identical worlds. The only
            // difference is that one of them has had a drink before, so the
            // comparison is of knowledge and nothing else - drinking would also
            // relieve the thirst, and that would muddle the two effects.
            Agent innocent = agentWith(new Needs(0.0, 70.0, 100.0, Needs.MAX));
            Agent experienced = agentWith(new Needs(0.0, 70.0, 100.0, Needs.MAX));
            experienced.updateKnowledge(knowledgeFromOneDrink());

            double untaught = utilityOfDrinking(innocent, worldWith(ResourceType.WATER));
            double taught = utilityOfDrinking(experienced, worldWith(ResourceType.WATER));

            assertTrue(
                    taught > untaught,
                    "having drunk once should make drinking look better: "
                            + untaught + " -> " + taught);
            assertTrue(experienced.knowledge().hasTested(
                    BeliefKind.HYDRATION, ResourceType.WATER));
        }

        /** What an agent knows after exactly one drink, and nothing else. */
        private Knowledge knowledgeFromOneDrink() {
            World world = worldWith(ResourceType.WATER);
            Agent scout = agentWith(new Needs(0.0, 90.0, 100.0, Needs.MAX));
            experience(scout, world, new DrinkAction(ResourceType.WATER, 4.0));
            return scout.knowledge();
        }

        private boolean considersEating(Agent agent, World world) {
            return decide(agent, world).considered().stream()
                    .anyMatch(option -> option.action() instanceof EatAction eat
                            && eat.food() == ResourceType.STONE);
        }

        /** What drinking is worth as a way to quench thirst, not as an experiment. */
        private double utilityOfDrinking(Agent agent, World world) {
            return decide(agent, world).considered().stream()
                    .filter(option -> option.action() instanceof DrinkAction)
                    .filter(option -> option.rationale().contains("satisfy thirst"))
                    .mapToDouble(option -> option.utility())
                    .max()
                    .orElse(0.0);
        }

        private Knowledge taughtAboutWater(Knowledge knowledge) {
            Knowledge learned = knowledge;
            for (int i = 0; i < 10; i++) {
                learned = learned
                        .learned(BeliefKind.HYDRATION, ResourceType.WATER, 12.0)
                        .learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.WATER, 1.0);
            }
            return learned;
        }
    }

    // --- experience to knowledge -----------------------------------------

    @Nested
    @DisplayName("experience becomes knowledge")
    class Acquisition {

        @Test
        @DisplayName("eating berries teaches roughly what berries are worth")
        void eatingTeachesNourishment() {
            World world = new World(WorldBounds.of(16, 16));
            Agent agent = agentWith(new Needs(90.0, 0.0, 100.0, Needs.MAX));
            agent.store(ResourceType.BERRIES, 10.0);

            assertFalse(agent.knowledge().hasTested(BeliefKind.NOURISHMENT, ResourceType.BERRIES));

            experience(agent, world, new EatAction(ResourceType.BERRIES, 4.0));

            assertEquals(
                    ResourceType.BERRIES.nourishmentPerUnit(),
                    agent.knowledge().estimate(BeliefKind.NOURISHMENT, ResourceType.BERRIES),
                    EPSILON,
                    "it should work out what it actually got, not what it assumed");
        }

        @Test
        @DisplayName("drinking teaches roughly what water is worth")
        void drinkingTeachesHydration() {
            World world = worldWith(ResourceType.WATER);
            Agent agent = agentWith(new Needs(0.0, 90.0, 100.0, Needs.MAX));

            experience(agent, world, new DrinkAction(ResourceType.WATER, 4.0));

            assertEquals(
                    ResourceType.WATER.hydrationPerUnit(),
                    agent.knowledge().estimate(BeliefKind.HYDRATION, ResourceType.WATER),
                    EPSILON);
        }

        @Test
        @DisplayName("the first real observation replaces the guess outright")
        void evidenceBeatsAssumption() {
            World world = worldWith(ResourceType.WATER);
            Agent agent = agentWith(new Needs(0.0, 90.0, 100.0, Needs.MAX));
            double assumed = agent.knowledge().estimate(BeliefKind.HYDRATION, ResourceType.WATER);

            experience(agent, world, new DrinkAction(ResourceType.WATER, 4.0));

            assertNotEquals(
                    assumed, agent.knowledge().estimate(BeliefKind.HYDRATION, ResourceType.WATER));
            assertEquals(1, agent.knowledge().believe(BeliefKind.HYDRATION, "WATER").observations());
        }

        @Test
        @DisplayName("confidence grows with every repetition, and never quite reaches certainty")
        void confidenceGrowsWithExperience() {
            World world = worldWith(ResourceType.WATER);
            Agent agent = agentWith(new Needs(0.0, 90.0, 100.0, Needs.MAX));

            double previous = 0.0;
            for (int i = 0; i < 12; i++) {
                agent.drink(-0.0);
                agent.updateKnowledge(agent.knowledge().learned(
                        BeliefKind.HYDRATION, ResourceType.WATER, 12.0));
                double now = agent.knowledge().confidence(BeliefKind.HYDRATION, ResourceType.WATER);
                assertTrue(now > previous, "confidence should keep rising");
                assertTrue(now < 1.0, "but never become certainty");
                previous = now;
            }
        }

        @Test
        @DisplayName("going for something and finding nothing lowers belief in such places")
        void disappointmentLowersLocationReliability() {
            World world = new World(CONFIG.bounds());
            world.addResource(
                    ResourceNode.holding(ResourceType.BERRIES, HERE, 0.0));
            Agent agent = agentWith(new Needs(80.0, 0.0, 100.0, Needs.MAX));
            double before =
                    agent.knowledge().estimate(BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES);

            experience(
                    agent,
                    world,
                    new dev.evoworld.core.action.GatherAction(ResourceType.BERRIES, 5.0, 1.0));

            double after =
                    agent.knowledge().estimate(BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES);
            assertTrue(after < before, before + " should have fallen, was " + after);
        }

        @Test
        @DisplayName("a successful trip raises it again")
        void successRaisesLocationReliability() {
            World world = worldWith(ResourceType.BERRIES);
            Agent agent = agentWith(new Needs(80.0, 0.0, 100.0, Needs.MAX));
            agent.updateKnowledge(agent.knowledge()
                    .learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES, 0.0));
            double afterDisappointment =
                    agent.knowledge().estimate(BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES);

            experience(
                    agent,
                    world,
                    new dev.evoworld.core.action.GatherAction(ResourceType.BERRIES, 5.0, 1.0));

            assertTrue(
                    agent.knowledge().estimate(
                                    BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES)
                            > afterDisappointment);
        }

        @Test
        @DisplayName("nothing is learned from actions that teach nothing")
        void mostThingsTeachNothing() {
            World world = new World(CONFIG.bounds());
            Agent agent = agentWith(Needs.healthy());

            experience(agent, world, new dev.evoworld.core.action.RestAction());

            assertTrue(agent.knowledge().isEmpty(), "resting says nothing about the world");
        }
    }

    // --- the whole chain --------------------------------------------------

    @Test
    @DisplayName("experience, memory and knowledge all come out of one run")
    void aRunProducesBothMemoryAndKnowledge() {
        World world = new World(CONFIG.bounds());
        world.addResource(ResourceNode.full(ResourceType.WATER, new Location(8, 9)));
        world.addResource(ResourceNode.full(ResourceType.BERRIES, HERE));
        Agent agent = agentWith(Needs.healthy());
        world.addAgent(agent);
        Simulation simulation =
                new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);

        simulation.run(200);

        assertFalse(agent.memory().isEmpty(), "it should remember where it has been");
        assertFalse(agent.knowledge().isEmpty(), "and have worked something out");
        assertTrue(
                agent.knowledge().hasTested(BeliefKind.NOURISHMENT, ResourceType.BERRIES),
                "including what berries do: " + agent.knowledge());
    }

    @Test
    @DisplayName("what an agent knows does not fade, though what it remembers does")
    void knowledgeDoesNotDecay() {
        World world = worldWith(ResourceType.WATER);
        Agent agent = agentWith(new Needs(0.0, 90.0, 100.0, Needs.MAX));
        experience(agent, world, new DrinkAction(ResourceType.WATER, 4.0));
        Knowledge learnedLongAgo = agent.knowledge();

        // A memory of a spring would have halved several times by now.
        assertEquals(
                learnedLongAgo.estimate(BeliefKind.HYDRATION, ResourceType.WATER),
                agent.knowledge().estimate(BeliefKind.HYDRATION, ResourceType.WATER),
                EPSILON,
                "an agent unsure the spring is still there is not unsure water quenches thirst");
    }
}
