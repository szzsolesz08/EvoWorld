package dev.evoworld.core.exploration;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What an agent has seen of the world, and how long ago.
 *
 * <p>A third kind of standing state, alongside memory and knowledge, and
 * distinct from both. Memory holds episodes and forgets the unimportant ones;
 * knowledge holds general claims and never forgets. This holds coverage -
 * which parts of the map the agent has laid eyes on - and it is bounded by the
 * map itself rather than by a capacity, because there are only ever so many
 * regions.
 *
 * <p>Familiarity fades. A corner walked through an hour ago is worth less than
 * one walked through a minute ago, and eventually worth revisiting. That decay
 * is what stops a fully explored world from making an agent incurious forever:
 * coverage lapses, curiosity returns, and the agent goes round again. Nothing
 * schedules that; it falls out of the arithmetic.
 */
public final class ExplorationMap {

    /** How long until a region is half as familiar as when it was last seen. */
    public static final long FAMILIARITY_HALF_LIFE = 500L;

    /** The bounds are learned from the first sighting rather than given up front. */
    private WorldBounds bounds;

    /** Insertion-ordered, so nothing about a run can depend on hash codes. */
    private final Map<Region, Sighting> sightings = new LinkedHashMap<>();

    /** What was seen in a region and when it was last laid eyes on. */
    private record Sighting(long lastSeenTick, Set<ResourceType> found) {
    }

    /**
     * One region's coverage, as a value - what a save file holds.
     *
     * <p>A list in declaration order rather than a set, because the JDK's
     * immutable sets iterate in an order it randomises per run: the same state
     * should always be written out the same way.
     */
    public record RegionRecord(Region region, long lastSeenTick, List<ResourceType> found) {

        public RegionRecord {
            found = found.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(found));
        }
    }

    /**
     * Coverage exactly as it was, for a saved agent.
     *
     * @param worldBounds null if the agent had never looked at anything
     */
    public static ExplorationMap restored(WorldBounds worldBounds, List<RegionRecord> records) {
        ExplorationMap map = new ExplorationMap();
        map.bounds = worldBounds;
        for (RegionRecord record : records) {
            Set<ResourceType> found = EnumSet.noneOf(ResourceType.class);
            found.addAll(record.found());
            map.sightings.put(record.region(), new Sighting(record.lastSeenTick(), found));
        }
        return map;
    }

    /** Every region visited, in the order first visited. */
    public List<RegionRecord> records() {
        List<RegionRecord> records = new ArrayList<>(sightings.size());
        sightings.forEach((region, sighting) -> records.add(new RegionRecord(
                region, sighting.lastSeenTick(), List.copyOf(sighting.found()))));
        return List.copyOf(records);
    }

    /** The world's size, once the agent has looked at anything; otherwise empty. */
    public Optional<WorldBounds> knownBounds() {
        return Optional.ofNullable(bounds);
    }

    /**
     * Notes that the agent is here and can see this far.
     *
     * <p>Marks the region it stands in as freshly seen. Only the region it is
     * actually in counts: seeing the edge of the next one is not the same as
     * having been there.
     */
    public void sight(WorldBounds worldBounds, Location where, SimulationTime when) {
        this.bounds = worldBounds;
        Region region = Region.containing(where);
        Sighting existing = sightings.get(region);
        sightings.put(
                region,
                new Sighting(
                        when.tick(),
                        existing == null ? EnumSet.noneOf(ResourceType.class) : existing.found()));
    }

    /**
     * Records finding a resource, and says whether that was news.
     *
     * @return the discovery if this was the first of its kind here, otherwise empty
     */
    public Optional<Discovery> notice(
            Location where, ResourceType what, SimulationTime when) {
        Region region = Region.containing(where);
        Sighting existing = sightings.get(region);
        Set<ResourceType> found = existing == null
                ? EnumSet.noneOf(ResourceType.class)
                : EnumSet.copyOf(existing.found());
        if (found.contains(what)) {
            return Optional.empty();
        }

        boolean firstEver = sightings.values().stream()
                .noneMatch(sighting -> sighting.found().contains(what));
        found.add(what);
        sightings.put(
                region,
                new Sighting(existing == null ? when.tick() : existing.lastSeenTick(), found));
        return Optional.of(new Discovery(what, where, region, when, firstEver));
    }

    /**
     * How well the agent knows a region right now, from nothing to fresh.
     *
     * <p>Halves every {@link #FAMILIARITY_HALF_LIFE} ticks, the same shape
     * memory uses, so a place fades rather than expiring.
     */
    public double familiarity(Region region, long tick) {
        Sighting sighting = sightings.get(region);
        if (sighting == null) {
            return 0.0;
        }
        long age = Math.max(0L, tick - sighting.lastSeenTick());
        return Math.pow(0.5, (double) age / FAMILIARITY_HALF_LIFE);
    }

    /** What the agent has found in a region, if it has been there. */
    public Set<ResourceType> foundIn(Region region) {
        Sighting sighting = sightings.get(region);
        return sighting == null ? Set.of() : Set.copyOf(sighting.found());
    }

    /** How much of the world the agent currently has a fresh sense of, from 0 to 1. */
    public double worldFamiliarity(long tick) {
        List<Region> regions = regions();
        if (regions.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Region region : regions) {
            total += familiarity(region, tick);
        }
        return total / regions.size();
    }

    /** How many regions the agent has set foot in at all. */
    public int regionsVisited() {
        return sightings.size();
    }

    public int regionCount() {
        return regions().size();
    }

    /**
     * The least familiar regions first, ties broken by position.
     *
     * <p>This is the frontier: where an agent goes when it wants to see
     * something new. Nothing marks a region as "unexplored" - it is simply the
     * one with the lowest number.
     */
    public List<Region> leastFamiliar(long tick, int limit) {
        List<Region> ordered = new ArrayList<>(regions());
        ordered.sort(java.util.Comparator
                .comparingDouble((Region region) -> familiarity(region, tick))
                .thenComparingInt(Region::column)
                .thenComparingInt(Region::row));
        return ordered.size() <= limit ? List.copyOf(ordered) : List.copyOf(ordered.subList(0, limit));
    }

    /** Every region of the world, once the agent has seen enough to know its size. */
    private List<Region> regions() {
        return bounds == null ? List.of() : Region.covering(bounds);
    }

    @Override
    public String toString() {
        return "has been to " + regionsVisited() + " of " + regionCount() + " regions";
    }
}
