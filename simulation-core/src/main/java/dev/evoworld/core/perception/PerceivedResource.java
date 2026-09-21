package dev.evoworld.core.perception;

import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Direction;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * A resource an agent can currently see, and where it is from here.
 *
 * <p>Wraps an immutable {@link ResourceSnapshot} rather than copying its
 * fields, so what the agent saw cannot drift from what was there when it
 * looked.
 *
 * @param stepsAway how many moves it would take to reach, not a straight line
 */
public record PerceivedResource(ResourceSnapshot resource, int stepsAway, Direction direction) {

    public PerceivedResource {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(direction, "direction");
        if (stepsAway < 0) {
            throw new IllegalArgumentException("distance must not be negative, was " + stepsAway);
        }
    }

    public static PerceivedResource seenFrom(Location viewer, ResourceSnapshot resource) {
        return new PerceivedResource(
                resource,
                viewer.manhattanDistanceTo(resource.location()),
                Direction.from(viewer, resource.location()));
    }

    public ResourceType type() {
        return resource.type();
    }

    public Location location() {
        return resource.location();
    }

    public double quantity() {
        return resource.quantity();
    }

    public boolean isAvailable() {
        return resource.isAvailable();
    }

    /** Whether the agent is standing on it. */
    public boolean isUnderfoot() {
        return stepsAway == 0;
    }

    /** "berries 4 steps north", the way an observer would read it out. */
    public String describe() {
        if (isUnderfoot()) {
            return String.format(
                    Locale.ROOT, "%s right here (%.1f)", type().label(), quantity());
        }
        return String.format(
                Locale.ROOT,
                "%s %d step%s %s (%.1f)",
                type().label(),
                stepsAway,
                stepsAway == 1 ? "" : "s",
                direction.label(),
                quantity());
    }

    @Override
    public String toString() {
        return describe();
    }
}
