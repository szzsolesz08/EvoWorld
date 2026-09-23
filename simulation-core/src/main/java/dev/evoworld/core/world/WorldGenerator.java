package dev.evoworld.core.world;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a world from a seed.
 *
 * <p>Generation is domain logic, not setup code: where the water is decides
 * what a run is about. It lives in the core so that the runner, a test and a
 * future viewer all get the identical world from the identical seed.
 *
 * <p>This is a hand-placed starter world, not procedural terrain - a fixed
 * recipe of "this many of each resource, scattered on distinct tiles". Biomes,
 * rivers and unbounded terrain are a later problem.
 */
public final class WorldGenerator {

    /** Name of the random stream world generation draws from. */
    public static final String STREAM = "worldgen";

    /**
     * The starter world: water is rare but worth finding, berries are the
     * common food, wood and stone are there for milestones that will need them.
     */
    public static final List<ResourcePlan> DEFAULT_PLAN = List.of(
            new ResourcePlan(ResourceType.WATER, 2),
            new ResourcePlan(ResourceType.BERRIES, 6),
            new ResourcePlan(ResourceType.WOOD, 5),
            new ResourcePlan(ResourceType.STONE, 3));

    private WorldGenerator() {
    }

    /** How many nodes of one type to scatter across the world. */
    public record ResourcePlan(ResourceType type, int nodeCount) {

        public ResourcePlan {
            Objects.requireNonNull(type, "type");
            if (nodeCount < 0) {
                throw new IllegalArgumentException("node count must not be negative, was " + nodeCount);
            }
        }
    }

    /**
     * The world a configuration asks for: an endless one that fills itself in
     * as it is walked into, or a finite one scattered with the standard plan.
     */
    public static World forConfig(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return config.bounds().isEndless()
                ? endless(config)
                : generate(config, DEFAULT_PLAN);
    }

    /** An endless world, generated a chunk at a time from the run's seed. */
    public static World endless(SimulationConfig config) {
        return new World(
                config.bounds(), new ChunkedTerrain(config.seed(), config.chunks()));
    }

    /** The standard finite world for a configuration. */
    public static World generate(SimulationConfig config) {
        return generate(config, DEFAULT_PLAN);
    }

    /**
     * A world with a custom mix of resources, drawn from the configuration's
     * own {@link #STREAM} so that layout is stable no matter how much
     * randomness the rest of the run consumes.
     */
    public static World generate(SimulationConfig config, List<ResourcePlan> plan) {
        Objects.requireNonNull(config, "config");
        return generate(config.bounds(), plan, SimulationRandom.seededWith(config.seed(), STREAM));
    }

    /**
     * Scatters the planned resources over distinct tiles.
     *
     * <p>Locations are drawn from a shrinking pool of free tiles rather than by
     * guessing and retrying on a collision: it cannot loop, and the number of
     * draws depends only on the plan, never on luck.
     */
    public static World generate(WorldBounds bounds, List<ResourcePlan> plan, SimulationRandom random) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(random, "random");

        if (bounds.isEndless()) {
            throw new IllegalArgumentException(
                    "an endless world cannot be scattered all at once; it is generated a chunk "
                            + "at a time - see WorldGenerator.endless");
        }
        int requested = plan.stream().mapToInt(ResourcePlan::nodeCount).sum();
        if (requested > bounds.tileCount()) {
            throw new IllegalArgumentException(
                    "plan needs " + requested + " tiles but the world only has " + bounds.tileCount());
        }

        World world = new World(bounds);
        List<Location> freeTiles = allTilesIn(bounds);
        for (ResourcePlan entry : plan) {
            for (int i = 0; i < entry.nodeCount(); i++) {
                Location location = freeTiles.remove(random.nextInt(freeTiles.size()));
                world.addResource(ResourceNode.full(entry.type(), location));
            }
        }
        return world;
    }

    /** Every tile, in a fixed row-major order so the draw below is reproducible. */
    private static List<Location> allTilesIn(WorldBounds bounds) {
        List<Location> tiles = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, bounds.tileCount()));
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                tiles.add(new Location(x, y));
            }
        }
        return tiles;
    }
}
