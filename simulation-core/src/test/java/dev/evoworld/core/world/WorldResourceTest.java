package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How the world holds, finds and grows the resources placed on it. */
class WorldResourceTest {

    private static final double EPSILON = 1e-9;
    private static final SimulationTime TICK_1 = new SimulationTime(1L, Duration.ofMinutes(10));

    private World world() {
        return new World(WorldBounds.of(8, 8));
    }

    @Test
    @DisplayName("a placed resource can be found on its tile")
    void findsResourcesByLocation() {
        World world = world();
        ResourceNode bush = ResourceNode.full(ResourceType.BERRIES, new Location(2, 3));
        world.addResource(bush);

        assertEquals(bush, world.resourceAt(new Location(2, 3)).orElseThrow());
        assertTrue(world.resourceAt(new Location(2, 4)).isEmpty());
    }

    @Test
    @DisplayName("a depleted node is still there, but not available")
    void depletedResourcesAreFoundButNotAvailable() {
        World world = world();
        Location here = new Location(1, 1);
        world.addResource(ResourceNode.holding(ResourceType.STONE, here, 0.0));

        assertTrue(world.resourceAt(here).isPresent(), "the outcrop is still on the map");
        assertTrue(world.availableResourceAt(here).isEmpty(), "but there is nothing left in it");
    }

    @Test
    @DisplayName("a resource cannot be placed outside the world")
    void rejectsResourcesOutsideTheWorld() {
        World world = world();

        assertThrows(
                IllegalArgumentException.class,
                () -> world.addResource(ResourceNode.full(ResourceType.WATER, new Location(9, 9))));
    }

    @Test
    @DisplayName("a tile holds at most one resource")
    void rejectsTwoResourcesOnOneTile() {
        World world = world();
        Location here = new Location(4, 4);
        world.addResource(ResourceNode.full(ResourceType.BERRIES, here));

        assertThrows(
                IllegalArgumentException.class,
                () -> world.addResource(ResourceNode.full(ResourceType.WOOD, here)));
        assertEquals(ResourceType.BERRIES, world.resourceAt(here).orElseThrow().type());
    }

    @Test
    @DisplayName("resources come back in the order they were placed")
    void preservesPlacementOrder() {
        World world = world();
        world.addResource(ResourceNode.full(ResourceType.WATER, new Location(0, 0)));
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(1, 0)));
        world.addResource(ResourceNode.full(ResourceType.STONE, new Location(2, 0)));

        assertEquals(
                List.of(ResourceType.WATER, ResourceType.BERRIES, ResourceType.STONE),
                world.resources().stream().map(ResourceNode::type).toList());
    }

    @Test
    void filtersResourcesByType() {
        World world = world();
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(0, 0)));
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(1, 0)));
        world.addResource(ResourceNode.full(ResourceType.WATER, new Location(2, 0)));

        assertEquals(2, world.resourcesOfType(ResourceType.BERRIES).size());
        assertEquals(1, world.resourcesOfType(ResourceType.WATER).size());
        assertTrue(world.resourcesOfType(ResourceType.WOOD).isEmpty());
    }

    @Test
    @DisplayName("regenerating the world grows every renewable node and leaves stone alone")
    void regeneratesEveryNode() {
        World world = world();
        ResourceNode bush = ResourceNode.holding(ResourceType.BERRIES, new Location(0, 0), 0.0);
        ResourceNode spring = ResourceNode.holding(ResourceType.WATER, new Location(1, 0), 0.0);
        ResourceNode outcrop = ResourceNode.holding(ResourceType.STONE, new Location(2, 0), 0.0);
        world.addResource(bush);
        world.addResource(spring);
        world.addResource(outcrop);

        world.regenerateResources();

        assertEquals(ResourceType.BERRIES.regenPerTick(), bush.quantity(), EPSILON);
        assertEquals(ResourceType.WATER.regenPerTick(), spring.quantity(), EPSILON);
        assertEquals(0.0, outcrop.quantity(), EPSILON);
    }

    @Test
    @DisplayName("harvesting through the world drains the node the world holds")
    void harvestingThroughTheWorldAffectsTheNode() {
        World world = world();
        Location here = new Location(5, 5);
        world.addResource(ResourceNode.full(ResourceType.BERRIES, here));

        double taken = world.availableResourceAt(here).orElseThrow().harvest(4.0);

        assertEquals(4.0, taken, EPSILON);
        assertEquals(
                ResourceType.BERRIES.capacity() - 4.0,
                world.resourceAt(here).orElseThrow().quantity(),
                EPSILON);
    }

    @Test
    @DisplayName("a snapshot carries the resources, frozen")
    void snapshotIncludesResources() {
        World world = world();
        Location here = new Location(3, 3);
        ResourceNode bush = ResourceNode.full(ResourceType.BERRIES, here);
        world.addResource(bush);

        WorldState state = world.snapshot(TICK_1);
        bush.harvestAll();

        ResourceSnapshot snapshot = state.resourceAt(here).orElseThrow();
        assertEquals(ResourceType.BERRIES, snapshot.type());
        assertEquals(ResourceType.BERRIES.capacity(), snapshot.quantity(), EPSILON);
        assertTrue(bush.isDepleted(), "the live node moved on without the snapshot");
    }

    @Test
    @DisplayName("a snapshot can be searched by type and tile")
    void snapshotSupportsLookups() {
        World world = world();
        world.addResource(ResourceNode.full(ResourceType.WATER, new Location(0, 1)));
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(1, 1)));

        WorldState state = world.snapshot(TICK_1);

        assertEquals(1, state.resourcesOfType(ResourceType.WATER).size());
        assertEquals(
                ResourceType.BERRIES, state.resourceAt(new Location(1, 1)).orElseThrow().type());
        assertTrue(state.resourceAt(new Location(7, 7)).isEmpty());
    }
}
