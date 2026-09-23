package dev.evoworld.core.perception;

import dev.evoworld.core.exploration.Region;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * Somewhere the agent remembers there being a lot of something.
 *
 * <p>Coarser than a remembered bush and more useful for it: an agent that
 * wants to fill a pack cares less about one particular bush than about the
 * field it stood in. What the agent kept is the most it saw at once, and how
 * strongly it still believes it, faded by how long ago that was.
 *
 * @param region where, as a region of the map
 * @param where the middle of that region, which is what to walk to
 * @param type what there was a lot of
 * @param seen how many were in view at once, at best
 * @param belief how much of that impression is left, 0 to 1
 * @param stepsAway how far it is from here
 * @param ticksSinceSeen how long ago that impression was formed
 */
public record KnownPlenty(
        Region region,
        Location where,
        ResourceType type,
        int seen,
        double belief,
        int stepsAway,
        long ticksSinceSeen) {

    public KnownPlenty {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(type, "type");
    }

    public static KnownPlenty seenFrom(
            Location from, Region region, ResourceType type, int seen, double belief, long ticksSinceSeen) {
        Location centre = region.centre();
        return new KnownPlenty(
                region, centre, type, seen, belief, from.manhattanDistanceTo(centre), ticksSinceSeen);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "%d %s around %s, %d steps away, seen %d ticks ago",
                seen, type.label(), region, stepsAway, ticksSinceSeen);
    }
}
