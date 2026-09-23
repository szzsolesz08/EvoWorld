package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A world that builds itself as it is walked into, and remembers what it built. */
class WorldChunkTest {

    private static final Duration TICK = Duration.ofMinutes(10);

    private static World endless() {
        return new World(WorldBounds.endless(), new ChunkedTerrain(42L, ChunkMix.standard()));
    }

    private static List<String> layoutOf(List<ResourceNode> nodes) {
        return nodes.stream()
                .map(node -> node.type() + " at " + node.location() + " " + node.quantity())
                .toList();
    }

    @Test
    @DisplayName("nothing exists until someone comes near enough to see it")
    void generatesOnlyWhatIsNeeded() {
        World world = endless();

        Location middleOfAChunk = new Chunk(0, 0).centre();

        assertEquals(0, world.chunkCount(), "an endless world starts empty");
        world.ensureAround(middleOfAChunk, 12, 0L);

        assertEquals(1, world.chunkCount(), "standing well inside one chunk needs only that one");
        assertFalse(world.resourcesNear(middleOfAChunk, 12).isEmpty());
    }

    @Test
    @DisplayName("stepping over a chunk border brings the next chunk into being, once")
    void generatesNeighboursAtTheBorder() {
        World world = endless();

        world.ensureAround(new Location(Chunk.SIZE - 1, 32), 12, 0L);
        assertEquals(2, world.chunkCount(), "sight reaches over the border");

        world.ensureAround(new Location(Chunk.SIZE, 32), 12, 1L);
        assertEquals(2, world.chunkCount(), "the same ground is not built twice");
    }

    @Test
    @DisplayName("a chunk walked back into is the chunk that was left")
    void chunksAreStable() {
        World world = endless();
        world.ensureAround(new Location(0, 0), 12, 0L);
        List<String> before = layoutOf(world.resourcesNear(new Location(0, 0), 12));

        // A long walk east and back again.
        for (int x = 0; x <= 400; x += 8) {
            world.ensureAround(new Location(x, 0), 12, x);
        }
        world.ensureAround(new Location(0, 0), 12, 500L);

        assertEquals(before, layoutOf(world.resourcesNear(new Location(0, 0), 12)));
    }

    @Test
    @DisplayName("ground left behind regrows exactly as if it had never been left")
    void regrowthCatchesUp() {
        World left = endless();
        World stayed = endless();
        Location home = new Chunk(0, 0).centre();
        for (World world : List.of(left, stayed)) {
            world.ensureAround(home, 12, 0L);
            ResourceNode picked = world.resourcesNear(home, 12).get(0);
            picked.harvest(picked.quantity() / 2);
        }

        // One stays and regrows every tick; the other walks away and comes back.
        for (long tick = 1; tick <= 200; tick++) {
            stayed.regenerateResources(TICK, List.of(home), 12, tick);
        }
        left.regenerateResources(TICK, List.of(new Location(1000, 1000)), 12, 200L);
        left.regenerateResources(TICK, List.of(home), 12, 200L);

        assertEquals(
                layoutOf(stayed.resourcesNear(home, 12)),
                layoutOf(left.resourcesNear(home, 12)),
                "coming back should find what staying would have");
    }

    @Test
    @DisplayName("what is near is what is near: reading the world does not read all of it")
    void nearIsNear() {
        World world = endless();
        Location home = new Chunk(0, 0).centre();
        world.ensureAround(home, 12, 0L);
        world.ensureAround(new Location(300, 0), 12, 1L);

        List<ResourceNode> near = world.resourcesNear(home, 12);

        assertTrue(
                near.stream().allMatch(node -> node.location().manhattanDistanceTo(home) <= 12),
                "nothing beyond the radius: " + near);
        assertTrue(
                world.resources().size() > near.size(),
                "though the world does hold the far chunk as well");
    }

    @Test
    @DisplayName("a fixed world is not generated at all, however far you walk in it")
    void fixedWorldsStayFixed() {
        World world = new World(WorldBounds.of(16, 16));
        world.addResource(ResourceNode.full(ResourceType.WATER, new Location(8, 8)));

        world.ensureAround(new Location(8, 8), 12, 0L);

        assertFalse(world.generatesItself());
        assertEquals(1, world.resources().size());
    }
}
