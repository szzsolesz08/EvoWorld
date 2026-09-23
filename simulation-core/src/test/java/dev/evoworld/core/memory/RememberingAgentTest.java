package dev.evoworld.core.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.MotivationSystem;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Memory as the agent actually uses it: recalled, weighed, acted on. */
class RememberingAgentTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final Location SPRING = new Location(2, 8);
    /** Well beyond sight of the spring, however far the agent can see. */
    private static final Location FAR_FROM_SPRING =
            new Location(SPRING.x() + CONFIG.perceptionRadius() + 4, 8);

    private final PerceptionSystem senses = new PerceptionSystem(CONFIG.perceptionRadius());
    private final MotivationSystem motivations = MotivationSystem.standard();
    private final DeliberativeDecisionMaker decisions = DeliberativeDecisionMaker.from(CONFIG);

    private static SimulationTime at(long tick) {
        return new SimulationTime(tick, Duration.ofMinutes(10).multipliedBy(tick));
    }

    private World worldWithASpring() {
        World world = new World(CONFIG.bounds());
        world.addResource(ResourceNode.full(ResourceType.WATER, SPRING));
        return world;
    }

    /** Thirsty, and experienced enough to know what water is for. */
    private Agent thirstyAgentAt(Location where) {
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", where, new Needs(0.0, 80.0, 100.0, Needs.MAX));
        Knowledge learned = agent.knowledge();
        for (int i = 0; i < 20; i++) {
            learned = learned
                    .learned(BeliefKind.HYDRATION, ResourceType.WATER, 12.0)
                    .learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.WATER, 1.0);
        }
        agent.updateKnowledge(learned);
        return agent;
    }

    private Deliberation decide(Agent agent, World world, long tick) {
        Perception perception = senses.perceive(agent, world, at(tick));
        return decisions.deliberate(
                perception, motivations.assess(perception), SimulationRandom.seededWith(1L));
    }

    @Test
    @DisplayName("what an agent sees, it remembers")
    void seeingIsRemembering() {
        World world = worldWithASpring();
        Agent agent = thirstyAgentAt(new Location(4, 8));

        MemoryRecorder.rememberSightings(
                agent.memory(), senses.perceive(agent, world, at(1)));

        assertFalse(agent.memory().isEmpty());
        assertEquals(
                1,
                agent.memory()
                        .recall(RecallQuery.from(at(1), agent.location())
                                .ofKind(MemoryKind.RESOURCE_LOCATION))
                        .size());
    }

    @Test
    @DisplayName("out of sight is not out of mind")
    void remembersPlacesItCanNoLongerSee() {
        World world = worldWithASpring();
        Agent agent = thirstyAgentAt(new Location(4, 8));

        // Sees the spring, then walks well beyond its perception radius.
        MemoryRecorder.rememberSightings(agent.memory(), senses.perceive(agent, world, at(1)));
        agent.moveTo(FAR_FROM_SPRING);
        Perception later = senses.perceive(agent, world, at(40));

        assertTrue(later.resources().isEmpty(), "the spring is far out of sight");
        assertEquals(1, later.remembered().size(), "but not forgotten");
        assertEquals(SPRING, later.remembered().get(0).location());
    }

    @Test
    @DisplayName("a remembered spring becomes a candidate an agent can act on")
    void rememberedWaterInfluencesTheChoice() {
        World world = worldWithASpring();
        Agent agent = thirstyAgentAt(new Location(4, 8));

        MemoryRecorder.rememberSightings(agent.memory(), senses.perceive(agent, world, at(1)));
        agent.moveTo(FAR_FROM_SPRING);
        Deliberation deliberation = decide(agent, world, 40);

        assertTrue(
                deliberation.chosen().rationale().contains("recalls water"),
                "a thirsty agent should head for water it remembers: "
                        + deliberation.chosen().rationale());
        assertEquals(
                SPRING, ((MoveAction) deliberation.chosen().action()).destination());
    }

    @Test
    @DisplayName("without the memory, the same agent has nowhere to go")
    void withoutMemoryThereIsNoSuchCandidate() {
        World world = worldWithASpring();
        Agent forgetful = thirstyAgentAt(FAR_FROM_SPRING);

        Deliberation deliberation = decide(forgetful, world, 40);

        assertFalse(
                deliberation.chosen().rationale().contains("water"),
                "nothing seen and nothing remembered leaves nothing to aim at: "
                        + deliberation.chosen().rationale());
    }

    @Test
    @DisplayName("water it can see beats water it only remembers")
    void seeingBeatsRemembering() {
        World world = worldWithASpring();
        world.addResource(ResourceNode.full(ResourceType.WATER, new Location(10, 8)));
        Agent agent = thirstyAgentAt(new Location(4, 8));

        MemoryRecorder.rememberSightings(agent.memory(), senses.perceive(agent, world, at(1)));
        agent.moveTo(new Location(12, 8));
        Deliberation deliberation = decide(agent, world, 40);

        assertEquals(
                new Location(10, 8),
                ((MoveAction) deliberation.chosen().action()).destination(),
                "the visible spring two steps away should win");
    }

    @Test
    @DisplayName("a place in sight is never also offered as a place remembered")
    void visiblePlacesAreNotDoubleCounted() {
        World world = worldWithASpring();
        Agent agent = thirstyAgentAt(new Location(4, 8));

        MemoryRecorder.rememberSightings(agent.memory(), senses.perceive(agent, world, at(1)));
        Perception again = senses.perceive(agent, world, at(2));

        assertEquals(1, again.resources().size());
        assertTrue(again.remembered().isEmpty(), "it can see it, so it need not recall it");
    }

    @Test
    @DisplayName("a faded memory is worth less than a fresh one, and says so")
    void staleMemoriesAreDiscounted() {
        World world = worldWithASpring();
        Agent agent = thirstyAgentAt(new Location(4, 8));
        MemoryRecorder.rememberSightings(agent.memory(), senses.perceive(agent, world, at(1)));
        agent.moveTo(FAR_FROM_SPRING);

        double fresh = senses.perceive(agent, world, at(10)).remembered().get(0).confidence();
        double stale = senses.perceive(agent, world, at(3000)).remembered().get(0).confidence();

        assertTrue(stale < fresh, stale + " should be less than " + fresh);
        assertTrue(fresh > 0.99, "a memory minutes old should be as good as seeing it");
    }

    @Test
    @DisplayName("a run records what happened to the agent, not just what it saw")
    void aRunFillsMemoryWithItsOwnHistory() {
        World world = worldWithASpring();
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(9, 8)));
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", new Location(8, 8), Needs.healthy());
        world.addAgent(agent);
        Simulation simulation =
                new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);

        simulation.run(120);

        assertFalse(agent.memory().isEmpty());
        assertTrue(
                agent.memory().contents().stream()
                        .anyMatch(memory -> memory.kind() == MemoryKind.SUCCESSFUL_ACTION),
                "it should remember something working");
        assertTrue(
                agent.memory().contents().stream()
                        .anyMatch(memory -> memory.kind() == MemoryKind.RESOURCE_LOCATION),
                "and where things are");
        assertTrue(
                agent.memory().size() <= agent.memory().capacity(),
                "and it should never outgrow itself");
    }

    @Test
    @DisplayName("memory does not make a run any less reproducible")
    void memoryKeepsRunsDeterministic() {
        assertEquals(runFor(60), runFor(60));
    }

    private Object runFor(long ticks) {
        World world = worldWithASpring();
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(9, 8)));
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", new Location(8, 8), Needs.healthy());
        world.addAgent(agent);
        Simulation simulation =
                new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);
        simulation.run(ticks);
        return simulation.snapshot();
    }
}
