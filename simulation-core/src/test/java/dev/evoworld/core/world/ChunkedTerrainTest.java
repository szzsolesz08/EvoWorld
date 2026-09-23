package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Country made out of a seed and a pair of coordinates. */
class ChunkedTerrainTest {

    private static ChunkedTerrain terrain(long seed) {
        return new ChunkedTerrain(seed, ChunkMix.standard());
    }

    private static int countOf(List<ResourceNode> nodes, ResourceType type) {
        return (int) nodes.stream().filter(node -> node.type() == type).count();
    }

    /**
     * What was laid down and where, in order. A node is a living thing with a
     * quantity that changes, so it is compared by what it is and where, not by
     * identity.
     */
    private static List<String> layoutOf(List<ResourceNode> nodes) {
        return nodes.stream().map(node -> node.type() + " at " + node.location()).toList();
    }

    @Test
    @DisplayName("a chunk depends on the seed and its own coordinates, and on nothing else")
    void sameSeedSameChunk() {
        Chunk chunk = new Chunk(3, -2);

        List<String> first = layoutOf(terrain(42L).generate(chunk));
        ChunkedTerrain busy = terrain(42L);
        for (int column = 0; column < 40; column++) {
            busy.generate(new Chunk(column, 17));
        }
        List<String> afterForty = layoutOf(busy.generate(chunk));

        assertEquals(first, afterForty, "a chunk is the same whenever it is first asked for");
        assertNotEquals(
                first,
                layoutOf(terrain(43L).generate(chunk)),
                "a different seed is a different world");
    }

    @Test
    @DisplayName("neighbouring chunks are different country, not the same country repeated")
    void chunksDifferFromEachOther() {
        ChunkedTerrain terrain = terrain(42L);

        List<Location> here = terrain.generate(new Chunk(0, 0)).stream()
                .map(node -> new Location(node.location().x() % Chunk.SIZE, node.location().y() % Chunk.SIZE))
                .toList();
        List<Location> next = terrain.generate(new Chunk(1, 0)).stream()
                .map(node -> new Location(node.location().x() % Chunk.SIZE, node.location().y() % Chunk.SIZE))
                .toList();

        assertNotEquals(here, next);
    }

    @Test
    @DisplayName("every chunk is somewhere an agent could cross: there is always water")
    void neverWaterless() {
        ChunkedTerrain lean = new ChunkedTerrain(7L, ChunkMix.lean());

        for (int column = -5; column <= 5; column++) {
            for (int row = -5; row <= 5; row++) {
                Chunk chunk = new Chunk(column, row);
                assertTrue(
                        countOf(lean.generate(chunk), ResourceType.WATER) >= ChunkMix.SPRINGS_AT_LEAST,
                        chunk + " has too little water even for hard country");
            }
        }
    }

    @Test
    @DisplayName("some country is richer than other country, which is what makes it worth remembering")
    void richnessVaries() {
        ChunkedTerrain terrain = terrain(42L);
        List<Integer> berries = new ArrayList<>();
        for (int column = -10; column <= 10; column++) {
            berries.add(countOf(terrain.generate(new Chunk(column, 0)), ResourceType.BERRIES));
        }

        int leanest = berries.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int richest = berries.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(richest >= 2 * leanest,
                "a berry field should be worth walking to: " + berries);
    }

    @Test
    @DisplayName("everything lands on its own tile, inside the chunk")
    void nodesAreInsideAndDistinct() {
        Chunk chunk = new Chunk(-4, 6);

        List<ResourceNode> nodes = terrain(42L).generate(chunk);

        assertEquals(
                nodes.size(),
                nodes.stream().map(ResourceNode::location).distinct().count(),
                "two things on one tile");
        assertTrue(nodes.stream().allMatch(node -> chunk.contains(node.location())));
    }

    @Test
    @DisplayName("the layout does not depend on the order the mix was written down")
    void layoutDoesNotDependOnMapOrder() {
        // The JDK's immutable maps iterate in an order they randomise once per
        // process. A terrain that placed its water in that order would build a
        // different world in the next JVM for the same seed, which is a bug
        // that only shows up as a run that dies in one process and lives in
        // the next. ChunkMix keeps its counts in declaration order instead.
        Map<ResourceType, Integer> backwards = new LinkedHashMap<>();
        backwards.put(ResourceType.STONE, 12);
        backwards.put(ResourceType.WOOD, 20);
        backwards.put(ResourceType.BERRIES, 24);
        backwards.put(ResourceType.WATER, 20);

        ChunkMix written = new ChunkMix(backwards, 0.4, 1.6);

        assertEquals(List.copyOf(ChunkMix.standard().average().keySet()),
                List.copyOf(written.average().keySet()),
                "counts must iterate in declaration order however they were given");
        assertEquals(
                layoutOf(new ChunkedTerrain(42L, ChunkMix.standard()).generate(new Chunk(2, 2))),
                layoutOf(new ChunkedTerrain(42L, written).generate(new Chunk(2, 2))));
    }
}
