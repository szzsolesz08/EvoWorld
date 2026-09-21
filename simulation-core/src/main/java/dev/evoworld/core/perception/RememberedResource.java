package dev.evoworld.core.perception;

import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Direction;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * Somewhere the agent believes a resource to be, without being able to see it.
 *
 * <p>The counterpart to {@link PerceivedResource}, and the reason the two are
 * different types: one is a fact, the other is a belief. A belief carries a
 * confidence below one and an age, and every decision built on it is discounted
 * by exactly that confidence. An agent heading for remembered water is taking a
 * calculated chance, and the calculation is visible.
 *
 * @param confidence how much of the original certainty survives
 * @param ticksSinceSeen how long ago the agent last laid eyes on it
 */
public record RememberedResource(
        ResourceType type,
        Location location,
        int stepsAway,
        Direction direction,
        double confidence,
        long ticksSinceSeen) {

    public RememberedResource {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(direction, "direction");
        if (stepsAway < 0) {
            throw new IllegalArgumentException("distance must not be negative, was " + stepsAway);
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0, 1]");
        }
    }

    public static RememberedResource recalledFrom(
            Location viewer,
            ResourceType type,
            Location where,
            double confidence,
            long ticksSinceSeen) {
        return new RememberedResource(
                type,
                where,
                viewer.manhattanDistanceTo(where),
                Direction.from(viewer, where),
                confidence,
                ticksSinceSeen);
    }

    /** "water 11 steps west, seen 60 ticks ago" - how it reads in a log. */
    public String describe() {
        return String.format(
                Locale.ROOT,
                "%s %d steps %s, seen %d ticks ago (%.2f sure)",
                type.label(),
                stepsAway,
                direction.label(),
                ticksSinceSeen,
                confidence);
    }

    @Override
    public String toString() {
        return describe();
    }
}
