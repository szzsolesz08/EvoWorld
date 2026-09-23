package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.WorldGenerator.ResourcePlan;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    /**
     * A world with edges: this is the scatter-it-all-at-once generator, which
     * is what finite worlds are built with. The endless one is a chunk at a
     * time and has its own tests.
     */
    private static SimulationConfig walled(long seed) {
        return SimulationConfig.defaults(seed).withWorld(WorldBounds.of(16, 16), ChunkMix.standard());
    }

    private static List<Location> locationsIn(World world) {
        return world.resources().stream().map(ResourceNode::location).toList();
    }

    @Test
    @DisplayName("the same seed always generates the same world")
    void isDeterministicForAGivenSeed() {
        List<Location> first = locationsIn(WorldGenerator.generate(walled(42L)));
        List<Location> second = locationsIn(WorldGenerator.generate(walled(42L)));

        assertEquals(first, second);
    }

    @Test
    @DisplayName("a different seed generates a different world")
    void differentSeedsGenerateDifferentWorlds() {
        List<Location> first = locationsIn(WorldGenerator.generate(walled(1L)));
        List<Location> second = locationsIn(WorldGenerator.generate(walled(2L)));

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("world layout does not depend on how much randomness the rest of the run uses")
    void layoutIsIndependentOfOtherStreams() {
        SimulationConfig config = walled(42L);
        World world = WorldGenerator.forConfig(config);

        // Draining the run's main stream must not move a single resource.
        SimulationRandom mainStream = SimulationRandom.seededWith(config.seed());
        for (int i = 0; i < 500; i++) {
            mainStream.nextInt(16);
        }

        assertEquals(locationsIn(world), locationsIn(WorldGenerator.forConfig(config)));
    }

    @Test
    @DisplayName("the plan is followed exactly, type by type")
    void placesTheRequestedNumberOfEachType() {
        World world = WorldGenerator.generate(walled(7L));

        for (ResourcePlan entry : WorldGenerator.DEFAULT_PLAN) {
            assertEquals(
                    entry.nodeCount(),
                    world.resourcesOfType(entry.type()).size(),
                    "wrong number of " + entry.type().label());
        }
        assertEquals(16, world.resources().size());
    }

    @Test
    @DisplayName("every resource lands on its own tile, inside the world")
    void placesResourcesOnDistinctTilesInsideTheWorld() {
        World world = WorldGenerator.generate(walled(3L));
        List<Location> locations = locationsIn(world);

        assertEquals(locations.size(), locations.stream().distinct().count(), "tiles must be unique");
        assertTrue(
                locations.stream().allMatch(world.bounds()::contains),
                "every resource must be inside " + world.bounds());
    }

    @Test
    @DisplayName("a generated world starts full")
    void resourcesStartFull() {
        World world = WorldGenerator.generate(walled(5L));

        assertTrue(world.resources().stream().allMatch(ResourceNode::isFull));
        assertTrue(world.resources().stream().allMatch(ResourceNode::isAvailable));
    }

    @Test
    @DisplayName("a generated world contains no agents until someone puts one there")
    void generationPlacesNoAgents() {
        assertTrue(WorldGenerator.generate(walled(5L)).agents().isEmpty());
    }

    @Test
    @DisplayName("a custom plan is honoured")
    void acceptsACustomPlan() {
        World world = WorldGenerator.generate(
                walled(11L),
                List.of(new ResourcePlan(ResourceType.WATER, 3)));

        assertEquals(3, world.resources().size());
        assertEquals(3, world.resourcesOfType(ResourceType.WATER).size());
        assertTrue(world.resourcesOfType(ResourceType.BERRIES).isEmpty());
    }

    @Test
    @DisplayName("an empty plan gives an empty world rather than an error")
    void acceptsAnEmptyPlan() {
        World world = WorldGenerator.generate(walled(11L), List.of());

        assertTrue(world.resources().isEmpty());
    }

    @Test
    @DisplayName("a world can be filled to the last tile")
    void canFillEveryTile() {
        WorldBounds tiny = WorldBounds.of(2, 2);
        World world = WorldGenerator.generate(
                tiny,
                List.of(new ResourcePlan(ResourceType.STONE, 4)),
                SimulationRandom.seededWith(1L));

        assertEquals(4, world.resources().size());
        assertEquals(4, locationsIn(world).stream().distinct().count());
    }

    @Test
    @DisplayName("a plan that cannot fit is rejected rather than silently trimmed")
    void rejectsAPlanThatDoesNotFit() {
        WorldBounds tiny = WorldBounds.of(2, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> WorldGenerator.generate(
                        tiny,
                        List.of(new ResourcePlan(ResourceType.STONE, 5)),
                        SimulationRandom.seededWith(1L)));
    }

    @Test
    void rejectsANegativeNodeCount() {
        assertThrows(
                IllegalArgumentException.class, () -> new ResourcePlan(ResourceType.STONE, -1));
    }
}
