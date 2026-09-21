package dev.evoworld.core.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DiscardAction;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.ExploreGoal;
import dev.evoworld.core.goal.MotivationSystem;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.memory.MemoryKind;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Discovery and experiment, end to end, through the real simulation.
 *
 * <p>The chain the milestone is about - perception, uncertainty, curiosity,
 * experiment, result, memory, knowledge - is exercised by running ticks and
 * reading what came out, not by calling each stage by hand.
 */
class DiscoveryTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final Location HERE = new Location(8, 8);
    private static final SimulationTime NOW = new SimulationTime(1L, Duration.ofMinutes(10));

    /** Everything a run says about decisions, outcomes and finds. */
    private static final class Journal implements SimulationObserver {
        final List<Decision> decisions = new ArrayList<>();
        final List<ActionOutcome> outcomes = new ArrayList<>();
        final List<Discovery> discoveries = new ArrayList<>();

        @Override
        public void onDecision(
                long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
            decisions.add(chosen);
        }

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            outcomes.add(outcome);
        }

        @Override
        public void onDiscovery(long tick, AgentId agentId, Discovery discovery) {
            discoveries.add(discovery);
        }
    }

    private Agent agentAt(Location where, Needs needs) {
        return new Agent(AgentId.of("agent-1"), "Atlas", where, needs);
    }

    private Simulation simulate(World world, Agent agent, Journal journal) {
        world.addAgent(agent);
        Simulation simulation =
                new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);
        simulation.addObserver(journal);
        return simulation;
    }

    // --- the chain --------------------------------------------------------

    @Nested
    @DisplayName("perception to knowledge")
    class TheChain {

        @Test
        @DisplayName("an untried thing underfoot is investigated, and what is learned sticks")
        void uncertaintyLeadsToAnExperimentThatTeaches() {
            World world = new World(CONFIG.bounds());
            world.addResource(ResourceNode.full(ResourceType.STONE, HERE));
            // Comfortable, but not so perfectly sated that drinking could show nothing.
            Agent agent = agentAt(HERE, new Needs(10.0, 10.0, 100.0, Needs.MAX));
            Journal journal = new Journal();
            Simulation simulation = simulate(world, agent, journal);

            // Perception and uncertainty: it sees stone and knows nothing of it.
            assertFalse(agent.knowledge().hasTested(BeliefKind.HYDRATION, ResourceType.STONE));

            simulation.tick();

            // Curiosity and experiment: the choice is made for exploration's sake.
            Decision first = journal.decisions.get(0);
            assertTrue(first.action() instanceof DrinkAction, first.toString());
            assertTrue(first.rationale().startsWith("explore"), first.rationale());
            assertTrue(first.rationale().contains("never tried"), first.rationale());

            // Result: the world says no, for good.
            assertTrue(journal.outcomes.get(0).wasImpossible());

            // Memory: the failed attempt is an episode it can recall.
            assertTrue(agent.memory().contents().stream()
                    .anyMatch(memory -> memory.kind() == MemoryKind.FAILED_ACTION));

            // Knowledge: a general conclusion about stone, not about this rock.
            assertTrue(agent.knowledge().hasTested(BeliefKind.HYDRATION, ResourceType.STONE));
            assertEquals(
                    0.0,
                    agent.knowledge().estimate(BeliefKind.HYDRATION, ResourceType.STONE));

            // And the next decision is different because of it.
            simulation.tick();
            Decision second = journal.decisions.get(1);
            assertFalse(
                    second.action() instanceof DrinkAction,
                    "it should not try drinking stone again: " + second);
        }

        @Test
        @DisplayName("the first thing an agent does in a new world is notice what is there")
        void sightingsBecomeDiscoveriesAndDiscoveriesBecomeMemories() {
            World world = new World(CONFIG.bounds());
            world.addResource(ResourceNode.full(ResourceType.WATER, new Location(9, 8)));
            world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(8, 10)));
            Agent agent = agentAt(HERE, Needs.healthy());
            Journal journal = new Journal();

            simulate(world, agent, journal).tick();

            assertEquals(2, journal.discoveries.size());
            assertTrue(journal.discoveries.stream().allMatch(Discovery::firstEver));
            assertEquals(
                    2,
                    agent.memory().contents().stream()
                            .filter(memory -> memory.kind() == MemoryKind.DISCOVERY)
                            .count());
        }

        @Test
        @DisplayName("nothing is discovered twice, however long the agent stares at it")
        void discoveriesAreNotRepeated() {
            World world = new World(CONFIG.bounds());
            world.addResource(ResourceNode.full(ResourceType.WATER, new Location(9, 8)));
            Agent agent = agentAt(HERE, Needs.healthy());
            Journal journal = new Journal();

            simulate(world, agent, journal).run(20);

            assertEquals(
                    1,
                    journal.discoveries.stream()
                            .filter(found -> found.what() == ResourceType.WATER)
                            .filter(found -> found.region().equals(Region.containing(new Location(9, 8))))
                            .count());
        }
    }

    // --- risk and reward --------------------------------------------------

    @Nested
    @DisplayName("risk and reward")
    class RiskAndReward {

        private final PerceptionSystem senses = new PerceptionSystem(CONFIG.perceptionRadius());
        private final MotivationSystem motivations = MotivationSystem.standard();
        private final DeliberativeDecisionMaker decisions = DeliberativeDecisionMaker.from(CONFIG);

        private Deliberation decide(Agent agent, World world) {
            Perception perception = senses.perceive(agent, world, NOW);
            return decisions.deliberate(
                    perception, motivations.assess(perception), SimulationRandom.seededWith(1L));
        }

        /** A rock it has never tried, and a spring it knows well, three steps off. */
        private World rockBesideASpring() {
            World world = new World(CONFIG.bounds());
            world.addResource(ResourceNode.full(ResourceType.STONE, HERE));
            world.addResource(ResourceNode.full(ResourceType.WATER, new Location(11, 8)));
            return world;
        }

        private Agent knowsWater(Needs needs) {
            Agent agent = agentAt(HERE, needs);
            Knowledge learned = agent.knowledge();
            for (int i = 0; i < 20; i++) {
                learned = learned
                        .learned(BeliefKind.HYDRATION, ResourceType.WATER, 12.0)
                        .learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.WATER, 1.0);
            }
            agent.updateKnowledge(learned);
            return agent;
        }

        @Test
        @DisplayName("a comfortable agent experiments")
        void comfortExperiments() {
            Decision chosen = decide(
                            knowsWater(new Needs(10.0, 10.0, 100.0, Needs.MAX)),
                            rockBesideASpring())
                    .chosen();

            assertTrue(chosen.rationale().contains("never tried"), chosen.toString());
        }

        @Test
        @DisplayName("a desperate agent does not stop to taste a rock on its way to water")
        void desperationDoesNotExperiment() {
            Decision chosen =
                    decide(knowsWater(new Needs(0.0, 97.0, 100.0, Needs.MAX)), rockBesideASpring())
                            .chosen();

            assertFalse(chosen.rationale().contains("never tried"), chosen.toString());
            assertTrue(chosen.rationale().contains("satisfy thirst"), chosen.toString());
        }

        @Test
        @DisplayName("curiosity is what is left once survival has had its say")
        void pressingNeedsSilenceCuriosity() {
            Perception comfortable = senses.perceive(
                    agentAt(HERE, Needs.healthy()), new World(CONFIG.bounds()), NOW);
            Perception parched = senses.perceive(
                    agentAt(HERE, new Needs(0.0, 95.0, 100.0, Needs.MAX)),
                    new World(CONFIG.bounds()),
                    NOW);

            double relaxed = motivations.assess(comfortable).priorityOf(ExploreGoal.NAME);
            double desperate = motivations.assess(parched).priorityOf(ExploreGoal.NAME);

            assertTrue(desperate < relaxed / 5, relaxed + " vs " + desperate);
        }

        @Test
        @DisplayName("an experiment's reward is what is left to learn, so a known thing offers none")
        void onlyTheUnknownIsInvestigated() {
            Agent agent = knowsWater(Needs.healthy());
            World world = new World(CONFIG.bounds());
            world.addResource(ResourceNode.full(ResourceType.WATER, HERE));

            assertTrue(decide(agent, world).considered().stream()
                    .noneMatch(option -> option.rationale().contains("never tried drinking water")));
        }

        @Test
        @DisplayName("junk left over from experiments is put down when it is in the way")
        void uselessSamplesAreDiscarded() {
            Agent agent = agentAt(HERE, new Needs(80.0, 0.0, 100.0, Needs.MAX));
            agent.store(ResourceType.STONE, agent.inventory().capacity());
            agent.updateKnowledge(agent.knowledge()
                    .learned(BeliefKind.NOURISHMENT, ResourceType.STONE, 0.0));

            Decision chosen = decide(agent, new World(CONFIG.bounds())).chosen();

            assertTrue(chosen.action() instanceof DiscardAction, chosen.toString());
        }

        @Test
        @DisplayName("but nothing untested is thrown away before the agent finds out what it is")
        void untestedThingsAreKept() {
            Agent agent = agentAt(HERE, new Needs(80.0, 0.0, 100.0, Needs.MAX));
            agent.store(ResourceType.STONE, agent.inventory().capacity());

            assertTrue(decide(agent, new World(CONFIG.bounds())).considered().stream()
                    .noneMatch(option -> option.action() instanceof DiscardAction));
        }
    }

    // --- exploration ------------------------------------------------------

    @Nested
    @DisplayName("exploration")
    class Exploration {

        @Test
        @DisplayName("left alone in an empty world, an agent covers all of it")
        void exploringCoversTheMap() {
            World world = new World(CONFIG.bounds());
            Agent agent = agentAt(HERE, Needs.healthy());

            simulate(world, agent, new Journal()).run(120);

            assertEquals(
                    agent.exploration().regionCount(),
                    agent.exploration().regionsVisited(),
                    agent.exploration().toString());
        }

        @Test
        @DisplayName("every discovery is something that is actually in the world")
        void discoveriesComeFromTheWorldNotAScript() {
            World world = WorldGenerator.generate(CONFIG);
            Agent agent = agentAt(HERE, Needs.healthy());
            Journal journal = new Journal();

            simulate(world, agent, journal).run(150);

            assertFalse(journal.discoveries.isEmpty());
            for (Discovery found : journal.discoveries) {
                assertEquals(
                        found.what(),
                        world.resourceAt(found.where()).orElseThrow().type(),
                        "a discovery must be of something really there: " + found);
            }
        }

        @Test
        @DisplayName("the same world and seed discover the same things in the same order")
        void discoveryIsDeterministic() {
            assertEquals(discoveriesIn(SimulationConfig.defaults(9L)),
                    discoveriesIn(SimulationConfig.defaults(9L)));
        }

        @Test
        @DisplayName("a different world gives a different history of discovery")
        void differentWorldsDiscoverDifferently() {
            assertFalse(discoveriesIn(SimulationConfig.defaults(9L))
                    .equals(discoveriesIn(SimulationConfig.defaults(10L))));
        }

        private List<String> discoveriesIn(SimulationConfig config) {
            World world = WorldGenerator.generate(config);
            Agent agent = agentAt(HERE, Needs.healthy());
            world.addAgent(agent);
            Simulation simulation =
                    new Simulation(world, DeliberativeDecisionMaker.from(config), config);
            Journal journal = new Journal();
            simulation.addObserver(journal);
            simulation.run(150);
            return journal.discoveries.stream()
                    .map(found -> found.when().tick() + " " + found.describe())
                    .toList();
        }
    }
}
