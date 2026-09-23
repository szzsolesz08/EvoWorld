package dev.evoworld.core.world;

import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a chunk from the run's seed and the chunk's own coordinates.
 *
 * <p>Each chunk draws from its own named stream - {@code "chunk 3,-2"} mixed
 * with the seed (ADR 6) - so what is in it depends on nothing else: not on
 * which chunks were generated before it, not on how long the agent took to get
 * there, not on anything the agent did. Walk away and come back and it is the
 * same chunk; start the same seed again and it is the same world; change the
 * seed and it is a different one.
 */
public final class ChunkedTerrain implements Terrain {

    /** How finely a chunk's richness is drawn, between the leanest and the richest. */
    private static final int RICHNESS_STEPS = 101;

    private final long seed;
    private final ChunkMix mix;

    public ChunkedTerrain(long seed, ChunkMix mix) {
        this.seed = seed;
        this.mix = Objects.requireNonNull(mix, "mix");
    }

    public ChunkMix mix() {
        return mix;
    }

    @Override
    public List<ResourceNode> generate(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        SimulationRandom random = SimulationRandom.seededWith(seed, streamFor(chunk));
        // In hundredths, because the one generator deals in whole numbers.
        double richness = mix.leanest()
                + (mix.richest() - mix.leanest()) * random.nextInt(RICHNESS_STEPS) / (RICHNESS_STEPS - 1.0);

        List<Location> freeTiles = tilesOf(chunk);
        List<ResourceNode> nodes = new ArrayList<>();
        for (Map.Entry<ResourceType, Integer> planned : mix.average().entrySet()) {
            int count = Math.min(mix.countOf(planned.getKey(), richness), freeTiles.size());
            for (int i = 0; i < count; i++) {
                // Drawn from a shrinking pool, so two nodes never land on one
                // tile and the number of draws never depends on luck.
                Location where = freeTiles.remove(random.nextInt(freeTiles.size()));
                nodes.add(ResourceNode.full(planned.getKey(), where));
            }
        }
        return List.copyOf(nodes);
    }

    /** The stream a chunk draws from: the seed, its column and its row, and nothing else. */
    static String streamFor(Chunk chunk) {
        return "chunk " + chunk.column() + "," + chunk.row();
    }

    private static List<Location> tilesOf(Chunk chunk) {
        Location origin = chunk.origin();
        List<Location> tiles = new ArrayList<>(Chunk.SIZE * Chunk.SIZE);
        for (int y = 0; y < Chunk.SIZE; y++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                tiles.add(new Location(origin.x() + x, origin.y() + y));
            }
        }
        return tiles;
    }
}
