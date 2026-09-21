package dev.evoworld.core.resource;

import dev.evoworld.core.world.Location;
import java.util.Locale;

/**
 * An immutable copy of one resource node's state at a point in time.
 *
 * <p>What an observer, and later a save file or a viewer, sees instead of the
 * live node.
 */
public record ResourceSnapshot(
        ResourceType type, Location location, double quantity, double capacity) {

    public boolean isAvailable() {
        return quantity > 0.0;
    }

    public boolean isDepleted() {
        return !isAvailable();
    }

    public double availability() {
        return quantity / capacity;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT, "%s at %s %.1f/%.1f", type.label(), location, quantity, capacity);
    }
}
