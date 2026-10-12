package dev.evoworld.core.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.proposal.ApproachRememberedProposer;
import dev.evoworld.core.decision.proposal.ApproachResourceProposer;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.memory.MemoryRecorder;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An agent between two things it wants walks to one of them.
 *
 * <p>The MVP's one known way to die: Atlas stood between two berry bushes,
 * seven steps apart either way, and walked back and forth between them until
 * he starved. Two faults made each step turn him round, and each has a test
 * here that the MVP fails:
 *
 * <ul>
 *   <li>a bush in sight was doubted more than the same bush remembered, so
 *       whichever was behind him looked better;
 *   <li>the place he was walking to could be crowded out of recall by things
 *       he was standing next to, so the goal he had set out for vanished.
 * </ul>
 *
 * <p>The last test is the scene itself, run from start to finish.
 */
class DitheringTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final int RADIUS = CONFIG.perceptionRadius();

    // The geometry of seed 10 at t300: from (7,9) one bush is in sight and the
    // other is a step beyond it; one step south, and the other way round.
    private static final Location SOUTH_WEST_BUSH = new Location(4, 13);
    private static final Location NORTH_EAST_BUSH = new Location(10, 6);
    private static final Location BETWEEN = new Location(7, 9);

    /** Hungry enough that curiosity about a world it has never seen stays quiet. */
    private static final double HUNGRY = 85.0;

    /** Ticks through the middle of the day, where an agent can see properly. */
    private static SimulationTime at(long tick) {
        return new SimulationTime(
                tick, Duration.ofHours(12).plus(Duration.ofMinutes(10).multipliedBy(tick)));
    }

    /** Hungry, sure bushes are reliable, but has eaten berries only twice. */
    private static Agent hungryAgentAt(Location where) {
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", where, new Needs(HUNGRY, 0.0, 100.0, Needs.MAX));
        Knowledge learned = agent.knowledge()
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0)
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0);
        for (int i = 0; i < 20; i++) {
            learned = learned.learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES, 1.0);
        }
        agent.updateKnowledge(learned);
        return agent;
    }

    private static void lookAround(Agent agent, World world, PerceptionSystem senses, long tick) {
        MemoryRecorder.rememberSightings(agent.memory(), senses.perceive(agent, world, at(tick)));
    }

    @Test
    @DisplayName("a bush in sight is never worth less than the same bush remembered")
    void seeingIsNoWorseThanRemembering() {
        World world = new World(CONFIG.bounds());
        Location bush = new Location(8, 8 - RADIUS);
        world.addResource(ResourceNode.full(ResourceType.BERRIES, bush));
        Agent agent = hungryAgentAt(new Location(8, 8));
        PerceptionSystem sharp = new PerceptionSystem(RADIUS);
        // The same bush, the same distance away, just past the edge of sight.
        PerceptionSystem shortSighted = new PerceptionSystem(RADIUS - 1);
        lookAround(agent, world, sharp, 1);

        Perception seeing = sharp.perceive(agent, world, at(2));
        Perception remembering = shortSighted.perceive(agent, world, at(2));
        assertEquals(1, seeing.resources().size());
        assertEquals(List.of(), remembering.resources());
        assertEquals(1, remembering.remembered().size());

        ActionCandidate seen = onlyCandidate(
                new ApproachResourceProposer(1.0).propose(seeing, random()));
        ActionCandidate recalled = onlyCandidate(
                new ApproachRememberedProposer(1.0).propose(remembering, random()));
        assertTrue(seen.confidence() >= recalled.confidence(),
                "seen " + seen.confidence() + " but remembered " + recalled.confidence());
    }

    @Test
    @DisplayName("the place an agent set out for stays in mind, however much is in sight around it")
    void clutterDoesNotCrowdOutTheDestination() {
        World world = new World(CONFIG.bounds());
        Location spring = new Location(1, 1);
        world.addResource(ResourceNode.full(ResourceType.WATER, spring));
        Location here = new Location(10, 10);
        // More in sight than recall used to bring back at once.
        for (int i = 0; i < 10; i++) {
            ResourceType type = i % 2 == 0 ? ResourceType.WOOD : ResourceType.STONE;
            world.addResource(ResourceNode.full(type, new Location(8 + i % 5, 9 + i / 5 * 2)));
        }
        PerceptionSystem senses = new PerceptionSystem(RADIUS);
        Agent agent = hungryAgentAt(spring);
        lookAround(agent, world, senses, 1);
        agent.moveTo(here);
        lookAround(agent, world, senses, 20);

        Perception now = senses.perceive(agent, world, at(21));

        assertTrue(now.resources().size() >= 10, "surrounded by clutter");
        assertTrue(now.remembered().stream().anyMatch(place -> place.location().equals(spring)),
                "but still remembers the spring: " + now.remembered());
    }

    @Test
    @DisplayName("between two bushes, it walks to one of them and eats, without turning back")
    void walksToOneOfTwoBushes() {
        World world = new World(CONFIG.bounds());
        world.addResource(ResourceNode.full(ResourceType.BERRIES, SOUTH_WEST_BUSH));
        world.addResource(ResourceNode.full(ResourceType.BERRIES, NORTH_EAST_BUSH));
        Agent agent = hungryAgentAt(new Location(7, 10));
        PerceptionSystem senses = new PerceptionSystem(RADIUS);
        // Has seen both, from one step either side of where it now stands.
        lookAround(agent, world, senses, 0);
        agent.moveTo(BETWEEN);
        world.addAgent(agent);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);

        List<Location> path = new ArrayList<>(List.of(agent.location()));
        for (int tick = 0; tick < 20 && agent.needs().hunger() >= HUNGRY; tick++) {
            simulation.tick();
            if (!agent.location().equals(path.get(path.size() - 1))) {
                path.add(agent.location());
            }
        }

        Location reached = path.get(path.size() - 1);
        assertTrue(reached.equals(SOUTH_WEST_BUSH) || reached.equals(NORTH_EAST_BUSH),
                "should have reached a bush: " + path);
        assertTrue(agent.needs().hunger() < HUNGRY, "and eaten there: " + agent.needs());
        for (int i = 1; i < path.size(); i++) {
            assertTrue(
                    path.get(i).manhattanDistanceTo(reached)
                            < path.get(i - 1).manhattanDistanceTo(reached),
                    "every step should bring it closer: " + path);
        }
    }

    private static ActionCandidate onlyCandidate(List<ActionCandidate> candidates) {
        assertEquals(1, candidates.size(), candidates.toString());
        return candidates.get(0);
    }

    private static SimulationRandom random() {
        return SimulationRandom.seededWith(1L);
    }
}
