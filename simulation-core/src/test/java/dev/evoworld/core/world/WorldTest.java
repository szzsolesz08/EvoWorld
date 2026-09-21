package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldTest {

    private static final SimulationTime TICK_7 = new SimulationTime(7L, Duration.ofMinutes(70));

    private World world() {
        return new World(WorldBounds.of(4, 3));
    }

    @Test
    @DisplayName("an agent cannot be spawned outside the world")
    void rejectsAgentsOutsideTheWorld() {
        World world = world();

        assertThrows(
                IllegalArgumentException.class,
                () -> world.addAgent(Agent.spawn("stray", "Stray", new Location(10, 10))));
    }

    @Test
    @DisplayName("a snapshot carries the time it was taken at")
    void snapshotRecordsTheMoment() {
        WorldState snapshot = world().snapshot(TICK_7);

        assertEquals(TICK_7, snapshot.time());
        assertEquals(7L, snapshot.tick());
        assertEquals(WorldBounds.of(4, 3), snapshot.bounds());
        assertTrue(snapshot.agents().isEmpty());
    }

    @Test
    @DisplayName("a snapshot is a copy, not a live view of the world")
    void snapshotDoesNotFollowLaterChanges() {
        World world = world();
        Agent agent = Agent.spawn("agent-1", "Atlas", new Location(1, 1));
        world.addAgent(agent);

        WorldState snapshot = world.snapshot(TICK_7);
        agent.moveTo(new Location(2, 2));

        assertEquals(new Location(1, 1), snapshot.agents().get(0).location());
        assertEquals(new Location(2, 2), agent.location());
    }

    @Test
    @DisplayName("agents come back in the order they were added, which keeps ticks deterministic")
    void preservesAgentOrder() {
        World world = world();
        world.addAgent(Agent.spawn("first", "Atlas", new Location(0, 0)));
        world.addAgent(Agent.spawn("second", "Bo", new Location(1, 0)));
        world.addAgent(Agent.spawn("third", "Cyd", new Location(2, 0)));

        assertEquals(
                java.util.List.of("first", "second", "third"),
                world.agents().stream().map(agent -> agent.id().value()).toList());
    }

    @Test
    void findsAgentsById() {
        World world = world();
        Agent agent = Agent.spawn("agent-1", "Atlas", new Location(1, 1));
        world.addAgent(agent);

        assertEquals(agent, world.findAgent(agent.id()).orElseThrow());
        assertTrue(world.findAgent(dev.evoworld.core.agent.AgentId.of("nobody")).isEmpty());
    }
}
