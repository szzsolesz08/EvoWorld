package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.agent.Needs;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldStateTest {

    private static final SimulationTime TICK_1 = new SimulationTime(1L, Duration.ofMinutes(10));
    private static final WorldBounds BOUNDS = WorldBounds.of(8, 8);

    private static AgentSnapshot snapshotAt(Location location) {
        return new AgentSnapshot(
                AgentId.of("agent-1"),
                "Atlas",
                location,
                Needs.healthy(),
                Inventory.empty(), Belongings.nothing(),
                0L,
                true);
    }

    @Test
    @DisplayName("two states describing the same world at the same moment are equal")
    void equalityIsByValue() {
        WorldState first = new WorldState(TICK_1, BOUNDS, List.of(snapshotAt(new Location(1, 1))), List.of());
        WorldState second = new WorldState(TICK_1, BOUNDS, List.of(snapshotAt(new Location(1, 1))), List.of());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    @DisplayName("a state taken at a different moment is a different state")
    void timeIsPartOfTheState() {
        WorldState atTick1 = new WorldState(TICK_1, BOUNDS, List.of(), List.of());
        WorldState atTick2 =
                new WorldState(
                        new SimulationTime(2L, Duration.ofMinutes(20)), BOUNDS, List.of(), List.of());

        assertNotEquals(atTick1, atTick2);
    }

    @Test
    @DisplayName("the agent list is copied on the way in and cannot be modified after")
    void agentsAreDefensivelyCopied() {
        List<AgentSnapshot> mutable = new ArrayList<>();
        mutable.add(snapshotAt(new Location(1, 1)));

        WorldState state = new WorldState(TICK_1, BOUNDS, mutable, List.of());
        mutable.add(snapshotAt(new Location(2, 2)));

        assertEquals(1, state.agents().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> state.agents().add(snapshotAt(new Location(3, 3))));
    }

    @Test
    @DisplayName("successive snapshots of a changing world differ")
    void tracksUpdatesToTheWorld() {
        World world = new World(BOUNDS);
        Agent agent = Agent.spawn("agent-1", "Atlas", new Location(1, 1));
        world.addAgent(agent);

        WorldState before = world.snapshot(TICK_1);
        agent.moveTo(new Location(4, 5));
        agent.exert(25.0);
        WorldState after = world.snapshot(new SimulationTime(2L, Duration.ofMinutes(20)));

        assertNotEquals(before, after);
        assertEquals(new Location(1, 1), before.agents().get(0).location());
        assertEquals(new Location(4, 5), after.agents().get(0).location());
        assertEquals(75.0, after.agents().get(0).needs().energy(), 1e-9);
    }
}
