package dev.evoworld.core.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.proposal.HeadForPlentyProposer;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the country was like, remembered and acted on.
 *
 * <p>An endless world is only worth having if its parts differ and the agent
 * can tell which was which. That is what these are about: a berry field
 * remembered is a place worth walking back to, and a bare slope is not.
 */
class RememberedPlentyTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    /** The middle of region 0,0, so that places either side of it are an equal walk. */
    private static final Location HERE = new Region(0, 0).centre();

    private static SimulationTime at(long tick) {
        return new SimulationTime(tick, Duration.ofMinutes(10).multipliedBy(tick));
    }

    /** A hungry agent that knows perfectly well what berries are. */
    private static Agent hungryAgent() {
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", HERE, new Needs(85.0, 0.0, 100.0, Needs.MAX));
        Knowledge learned = agent.knowledge();
        for (int i = 0; i < 20; i++) {
            learned = learned
                    .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0)
                    .learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.BERRIES, 1.0);
        }
        agent.updateKnowledge(learned);
        return agent;
    }

    private static Perception seeing(Agent agent, long tick) {
        World world = new World(CONFIG.bounds());
        world.addAgent(agent);
        return new PerceptionSystem(CONFIG.perceptionRadius()).perceive(agent, world, at(tick));
    }

    private static Optional<ActionCandidate> tripFor(Perception perception) {
        return new HeadForPlentyProposer(1.0).propose(perception, SimulationRandom.seededWith(1L))
                .stream()
                .findFirst();
    }

    @Test
    @DisplayName("what a place held is remembered as the most seen there at once")
    void keepsTheBestSighting() {
        ExplorationMap map = new ExplorationMap();
        Region field = new Region(10, 0);

        map.sawPlenty(field, ResourceType.BERRIES, 9, at(1));
        map.sawPlenty(field, ResourceType.BERRIES, 3, at(2));

        assertEquals(9, map.plentyOf(field, ResourceType.BERRIES).orElseThrow().count(),
                "a field picked over today is still a field");
        assertEquals(1L, map.plentyOf(field, ResourceType.BERRIES).orElseThrow().tick());
    }

    @Test
    @DisplayName("an old impression of plenty is weaker than a fresh one")
    void abundanceFades() {
        ExplorationMap map = new ExplorationMap();
        Region field = new Region(10, 0);
        map.sawPlenty(field, ResourceType.BERRIES, 8, at(100));

        double fresh = map.abundance(field, ResourceType.BERRIES, 100L);
        double stale = map.abundance(field, ResourceType.BERRIES, 100L + 20 * ExplorationMap.FAMILIARITY_HALF_LIFE);

        assertTrue(fresh > stale, fresh + " should beat " + stale);
        assertTrue(stale < 0.05, "a week-old impression is barely an impression: " + stale);
    }

    @Test
    @DisplayName("somewhere it has never been offers nothing, however rich that country is")
    void memoryIsNotSecondSight() {
        Agent agent = hungryAgent();

        assertEquals(List.of(), seeing(agent, 10).plenty());
        assertTrue(tripFor(seeing(agent, 10)).isEmpty(), "nothing remembered, nowhere to go");
    }

    @Test
    @DisplayName("hungry, it heads for the field it remembers rather than the slope it remembers")
    void richBeatsPoorAtTheSameDistance() {
        Agent agent = hungryAgent();
        Region field = new Region(-3, 0);
        Region slope = new Region(3, 0);
        agent.exploration().sawPlenty(field, ResourceType.BERRIES, 9, at(10));
        agent.exploration().sawPlenty(slope, ResourceType.BERRIES, 1, at(10));

        Perception perception = seeing(agent, 12);
        ActionCandidate trip = tripFor(perception).orElseThrow();

        assertEquals(
                field.centre().manhattanDistanceTo(HERE),
                slope.centre().manhattanDistanceTo(HERE),
                "the two places must be the same walk away for this to mean anything");
        assertEquals(field.centre(), trip.target().orElseThrow(), trip.rationale());
    }

    @Test
    @DisplayName("a rich place half forgotten loses to a poorer one seen just now")
    void freshnessCounts() {
        Agent agent = hungryAgent();
        Region oldField = new Region(-3, 0);
        Region seenToday = new Region(3, 0);
        long now = 40 * ExplorationMap.FAMILIARITY_HALF_LIFE;
        agent.exploration().sawPlenty(oldField, ResourceType.BERRIES, 9, at(1));
        agent.exploration().sawPlenty(seenToday, ResourceType.BERRIES, 5, at(now - 1));

        ActionCandidate trip = tripFor(seeing(agent, now)).orElseThrow();

        assertEquals(seenToday.centre(), trip.target().orElseThrow(), trip.rationale());
    }

    @Test
    @DisplayName("a full agent stays where it is: plenty is a reason, not a compulsion")
    void onlyWhenThereIsAReason() {
        Agent full = new Agent(AgentId.of("agent-1"), "Atlas", HERE, Needs.healthy());
        full.exploration().sawPlenty(new Region(-3, 0), ResourceType.BERRIES, 9, at(10));

        assertFalse(seeing(full, 12).plenty().isEmpty(), "it still remembers the field");
        assertTrue(tripFor(seeing(full, 12)).isEmpty(), "but has no reason to go");
    }
}
