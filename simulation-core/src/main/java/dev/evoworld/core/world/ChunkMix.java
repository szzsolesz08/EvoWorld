package dev.evoworld.core.world;

import dev.evoworld.core.resource.ResourceType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What a chunk of the world tends to hold, and how much that varies.
 *
 * <p>The counts are what an average chunk gets; each chunk then rolls its own
 * <em>richness</em> between {@link #leanest} and {@link #richest} and scales
 * them by it. That is what makes one stretch of country a berry field and the
 * next a bare slope, without any chunk being written by hand.
 *
 * <p>However lean the roll, a chunk always holds at least one spring. An agent
 * that wandered into a day's walk of waterless country would simply die, and a
 * world that can kill you for walking the wrong way is a worse world, not a
 * harder one.
 *
 * @param average how many nodes of each type an average chunk holds
 * @param leanest the poorest a chunk can be, as a share of the average
 * @param richest the richest a chunk can be
 */
public record ChunkMix(Map<ResourceType, Integer> average, double leanest, double richest) {

    /**
     * Whatever else a chunk rolls, it has this many springs.
     *
     * <p>The number is not a taste: an agent sees about three hundred tiles at
     * once and a chunk is four thousand, so a dozen springs is what it takes
     * for water to be in view somewhere along a walk across the chunk. Fewer
     * than that and water becomes a thing you can only find by luck, which in
     * a world you can walk out of is a slow death rather than a hard country.
     */
    public static final int SPRINGS_AT_LEAST = 12;

    public ChunkMix {
        // An EnumMap iterates in declaration order. Map.copyOf promises no
        // order at all, and the JDK's immutable maps randomise theirs once per
        // process - so a terrain that laid its water down in that order would
        // build a different world for the same seed in a different JVM. That
        // is the kind of bug that only shows up as a run that dies in one
        // process and lives in the next.
        average = Collections.unmodifiableMap(new EnumMap<>(average));
        if (leanest < 0.0 || richest < leanest) {
            throw new IllegalArgumentException(
                    "richness must run from lean to rich, was " + leanest + ".." + richest);
        }
    }

    /**
     * The standard country: about one tile in sixty holds something, and a
     * chunk is anywhere from two fifths as rich to half as rich again.
     *
     * <p>Thinner on the ground than the old 16x16 world, because an agent now
     * sees twice as far: what matters is how much is in view at once, and this
     * puts about as much there as the small world did. It also keeps a chunk
     * to a few dozen nodes rather than a few hundred, which is what makes a
     * world of hundreds of chunks cost nothing to walk through.
     *
     * <p>Water is the exception, and is as common as berries. Thirst comes
     * about twice as often as hunger, so water has to be about twice as easy
     * to reach; in a world you can walk out of, making it the rare thing is
     * simply a slow death sentence.
     */
    public static ChunkMix standard() {
        return new ChunkMix(counts(20, 24, 20, 12), 0.4, 1.6);
    }

    /** Hard country: half the food and materials, and more variable with it. */
    public static ChunkMix lean() {
        return new ChunkMix(counts(16, 12, 10, 6), 0.3, 1.2);
    }

    private static Map<ResourceType, Integer> counts(int water, int berries, int wood, int stone) {
        Map<ResourceType, Integer> counts = new LinkedHashMap<>();
        counts.put(ResourceType.WATER, water);
        counts.put(ResourceType.BERRIES, berries);
        counts.put(ResourceType.WOOD, wood);
        counts.put(ResourceType.STONE, stone);
        return counts;
    }

    /** How many nodes of {@code type} a chunk of this richness holds. */
    public int countOf(ResourceType type, double richness) {
        Objects.requireNonNull(type, "type");
        int count = (int) Math.round(average.getOrDefault(type, 0) * richness);
        return type == ResourceType.WATER ? Math.max(SPRINGS_AT_LEAST, count) : count;
    }

    /** How many nodes an average chunk holds in all, for sanity checks. */
    public int averageTotal() {
        return average.values().stream().mapToInt(Integer::intValue).sum();
    }
}
