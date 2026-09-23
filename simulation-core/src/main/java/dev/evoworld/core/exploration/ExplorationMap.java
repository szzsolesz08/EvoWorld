package dev.evoworld.core.exploration;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Chunk;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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

    /**
     * How far around itself an agent keeps track of, in regions.
     *
     * <p>A world with no edges has no "fraction explored", so exploring is
     * judged on the neighbourhood: the regions within this many of the one the
     * agent is standing in, nine by nine of them, about a day's walk across.
     * Beyond that it neither knows nor cares - which is also how curiosity
     * keeps working forever instead of being satisfied once and for all.
     */
    public static final int WINDOW = 4;

    /** Seeing this many of something at once is as impressive as it gets. */
    public static final int PLENTY = 4;

    /**
     * Fewer than this is not a place, it is a thing.
     *
     * <p>One bush seen in a region is a bush, and a remembered bush is already
     * something the agent can head for. What this memory is for is the other
     * kind of fact - "that whole slope was berries" - so a region has to have
     * held more than one of something to count as somewhere worth the walk.
     */
    public static final int WORTH_A_TRIP = 2;

    /** The bounds are learned from the first sighting rather than given up front. */
    private WorldBounds bounds;

    /** Insertion-ordered, so nothing about a run can depend on hash codes. */
    private final Map<Region, Sighting> sightings = new LinkedHashMap<>();

    /**
     * What has been found in each chunk, which is what makes a find worth
     * announcing. Per chunk rather than per region: in an endless world,
     * "berries in a new part of the world" every four tiles is not news, it is
     * chatter. Rebuilt from the regions when a save is loaded.
     */
    private final Map<Chunk, Set<ResourceType>> finds = new LinkedHashMap<>();

    /** What was seen in a region and when it was last laid eyes on. */
    private record Sighting(long lastSeenTick, Set<ResourceType> found, Map<ResourceType, Seen> plenty) {

        Sighting(long lastSeenTick, Set<ResourceType> found) {
            this(lastSeenTick, found, Map.of());
        }
    }

    /** The most of something an agent has seen at once in a region, and when. */
    public record Seen(int count, long tick) {
    }

    /**
     * One region's coverage, as a value - what a save file holds.
     *
     * <p>A list in declaration order rather than a set, because the JDK's
     * immutable sets iterate in an order it randomises per run: the same state
     * should always be written out the same way.
     */
    public record RegionRecord(
            Region region, long lastSeenTick, List<ResourceType> found, Map<ResourceType, Seen> plenty) {

        public RegionRecord {
            found = found.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(found));
            plenty = inOrder(plenty);
        }

        public RegionRecord(Region region, long lastSeenTick, List<ResourceType> found) {
            this(region, lastSeenTick, found, Map.of());
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
            map.finds.computeIfAbsent(
                            Chunk.containing(record.region().centre()),
                            chunk -> EnumSet.noneOf(ResourceType.class))
                    .addAll(found);
            map.sightings.put(
                    record.region(),
                    new Sighting(record.lastSeenTick(), found, Map.copyOf(record.plenty())));
        }
        return map;
    }

    /** Every region visited, in the order first visited. */
    public List<RegionRecord> records() {
        List<RegionRecord> records = new ArrayList<>(sightings.size());
        sightings.forEach((region, sighting) -> records.add(new RegionRecord(
                region, sighting.lastSeenTick(), List.copyOf(sighting.found()), sighting.plenty())));
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
                        existing == null ? EnumSet.noneOf(ResourceType.class) : existing.found(),
                        existing == null ? Map.of() : existing.plenty()));
    }

    /**
     * Records finding a resource, and says whether that was news.
     *
     * @return the discovery if this was the first of its kind here, otherwise empty
     */
    public Optional<Discovery> notice(
            Location where, ResourceType what, SimulationTime when) {
        Chunk chunk = Chunk.containing(where);
        Set<ResourceType> inChunk =
                finds.computeIfAbsent(chunk, key -> EnumSet.noneOf(ResourceType.class));
        if (inChunk.contains(what)) {
            return Optional.empty();
        }
        boolean firstEver = finds.values().stream().noneMatch(kinds -> kinds.contains(what));
        inChunk.add(what);

        Region region = Region.containing(where);
        Sighting existing = sightings.get(region);
        Set<ResourceType> found = existing == null
                ? EnumSet.noneOf(ResourceType.class)
                : EnumSet.copyOf(existing.found());
        found.add(what);
        sightings.put(
                region,
                new Sighting(
                        existing == null ? when.tick() : existing.lastSeenTick(),
                        found,
                        existing == null ? Map.of() : existing.plenty()));
        return Optional.of(new Discovery(what, where, chunk, when, firstEver));
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

    /**
     * Notes how much of something was in view in a region.
     *
     * <p>Kept as the most seen at once, not the last count: a bush picked bare
     * today does not mean the field is bare, and "there were nine bushes over
     * there" is what an agent wants when it needs to fill a pack rather than
     * take one meal. Like everything remembered, it fades.
     */
    public void sawPlenty(Region region, ResourceType type, int count, SimulationTime when) {
        if (count <= 0) {
            return;
        }
        Sighting existing = sightings.get(region);
        Map<ResourceType, Seen> plenty = existing == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existing.plenty());
        Seen before = plenty.get(type);
        if (before == null || count >= before.count()) {
            plenty.put(type, new Seen(count, when.tick()));
        }
        sightings.put(
                region,
                new Sighting(
                        existing == null ? when.tick() : existing.lastSeenTick(),
                        existing == null ? EnumSet.noneOf(ResourceType.class) : existing.found(),
                        inOrder(plenty)));
    }

    /** What the agent remembers of how much was in a region, if anything. */
    public Optional<Seen> plentyOf(Region region, ResourceType type) {
        Sighting sighting = sightings.get(region);
        return sighting == null ? Optional.empty() : Optional.ofNullable(sighting.plenty().get(type));
    }

    /**
     * How strongly the agent still believes a region is worth going to for
     * something, from 0 to 1: how much was there, faded by how long ago.
     */
    public double abundance(Region region, ResourceType type, long tick) {
        return plentyOf(region, type)
                .map(seen -> Math.min(1.0, seen.count() / (double) PLENTY) * fade(seen.tick(), tick))
                .orElse(0.0);
    }

    /** Everywhere nearby the agent remembers something being plentiful. */
    public List<RegionRecord> plentyAround(Location around) {
        List<RegionRecord> found = new ArrayList<>();
        for (Region region : regionsAround(around)) {
            Sighting sighting = sightings.get(region);
            if (sighting == null) {
                continue;
            }
            Map<ResourceType, Seen> worthIt = new EnumMap<>(ResourceType.class);
            sighting.plenty().forEach((type, seen) -> {
                if (seen.count() >= WORTH_A_TRIP) {
                    worthIt.put(type, seen);
                }
            });
            if (!worthIt.isEmpty()) {
                found.add(new RegionRecord(
                        region, sighting.lastSeenTick(), List.copyOf(sighting.found()), worthIt));
            }
        }
        return List.copyOf(found);
    }

    private static double fade(long seenTick, long now) {
        long age = Math.max(0L, now - seenTick);
        return Math.pow(0.5, (double) age / FAMILIARITY_HALF_LIFE);
    }

    /** What the agent has found in a region, if it has been there. */
    public Set<ResourceType> foundIn(Region region) {
        Sighting sighting = sightings.get(region);
        return sighting == null
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(sighting.found()));
    }

    /**
     * A copy that always iterates the same way, which the JDK's immutable maps
     * do not: they randomise their order once per process, so what is written
     * into a save - or acted on in a world - would depend on which JVM ran it.
     */
    private static Map<ResourceType, Seen> inOrder(Map<ResourceType, Seen> plenty) {
        return plenty.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new EnumMap<>(plenty));
    }

    /**
     * How fresh a sense the agent has of the country around it, from 0 to 1.
     *
     * <p>Around it, not of the world: an endless world is never explored, and
     * an agent that measured itself against all of it would be forever at
     * nought, restless wherever it stood.
     */
    public double familiarityAround(Location around, long tick) {
        List<Region> regions = regionsAround(around);
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

    /** How many regions the neighbourhood holds - the window, clipped to a finite world. */
    public int regionsInView(Location around) {
        return regionsAround(around).size();
    }

    /**
     * How many of the regions nearby the agent has actually been to.
     *
     * <p>The one to show beside {@link #regionsInView}: in an endless world
     * "271 of 81" is not a fraction of anything, while "34 of 81 nearby" says
     * how well it knows where it is standing.
     */
    public int visitedInView(Location around) {
        return (int) regionsAround(around).stream().filter(sightings::containsKey).count();
    }

    /**
     * The least familiar regions first, ties broken by position.
     *
     * <p>This is the frontier: where an agent goes when it wants to see
     * something new. Nothing marks a region as "unexplored" - it is simply the
     * one with the lowest number.
     */
    public List<Region> leastFamiliar(Location around, long tick, int limit) {
        List<Region> ordered = new ArrayList<>(regionsAround(around));
        // Least familiar first, and among equals the nearest. In a world with
        // edges that hardly mattered; in an endless one everything beyond the
        // frontier is equally unknown, and without the distance the agent
        // picked the far corner of its window every time - a walk so long the
        // evaluator discounted curiosity to nothing, so it never went at all.
        ordered.sort(java.util.Comparator
                .comparingDouble((Region region) -> familiarity(region, tick))
                .thenComparingInt(region -> region.centre().manhattanDistanceTo(around))
                .thenComparingInt(Region::column)
                .thenComparingInt(Region::row));
        return ordered.size() <= limit ? List.copyOf(ordered) : List.copyOf(ordered.subList(0, limit));
    }

    /**
     * The regions around a place: the window, clipped to the world if the world
     * has edges.
     */
    private List<Region> regionsAround(Location around) {
        Region centre = Region.containing(around);
        List<Region> regions = new ArrayList<>();
        for (int row = -WINDOW; row <= WINDOW; row++) {
            for (int column = -WINDOW; column <= WINDOW; column++) {
                Region region = new Region(centre.column() + column, centre.row() + row);
                if (bounds == null || overlaps(region)) {
                    regions.add(region);
                }
            }
        }
        return List.copyOf(regions);
    }

    private boolean overlaps(Region region) {
        Location corner = region.centre();
        int half = Region.SIZE / 2;
        return corner.x() + half > bounds.minX()
                && corner.x() - half <= bounds.maxX()
                && corner.y() + half > bounds.minY()
                && corner.y() - half <= bounds.maxY();
    }

    @Override
    public String toString() {
        return "has been to " + regionsVisited() + " regions";
    }
}
