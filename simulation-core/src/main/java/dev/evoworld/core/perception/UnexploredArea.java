package dev.evoworld.core.perception;

import dev.evoworld.core.exploration.Region;
import dev.evoworld.core.world.Direction;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * Somewhere the agent has not been lately, and how to get there.
 *
 * <p>The third thing a perception can offer alongside what is seen and what is
 * remembered: somewhere worth going to find out. Familiarity of zero means the
 * agent has never set foot there; anything between is a place it has been but
 * is starting to lose track of.
 */
public record UnexploredArea(
        Region region,
        Location centre,
        int stepsAway,
        Direction direction,
        double familiarity) {

    public UnexploredArea {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(centre, "centre");
        Objects.requireNonNull(direction, "direction");
    }

    public static UnexploredArea seenFrom(Location viewer, Region region, double familiarity) {
        Location centre = region.centre();
        return new UnexploredArea(
                region,
                centre,
                viewer.manhattanDistanceTo(centre),
                Direction.from(viewer, centre),
                familiarity);
    }

    /** How much there is to find out here, from nothing to everything. */
    public double novelty() {
        return 1.0 - familiarity;
    }

    public boolean isUntrodden() {
        return familiarity <= 0.0;
    }

    public String describe() {
        return String.format(
                Locale.ROOT,
                "%s %d steps %s (%s)",
                region,
                stepsAway,
                direction.label(),
                isUntrodden() ? "never been" : String.format(Locale.ROOT, "%.2f known", familiarity));
    }

    @Override
    public String toString() {
        return describe();
    }
}
