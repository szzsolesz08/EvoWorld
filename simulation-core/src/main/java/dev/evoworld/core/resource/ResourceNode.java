package dev.evoworld.core.resource;

import java.time.Duration;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * A patch of one resource sitting on one tile: a berry bush, a spring, a stand
 * of trees, a stone outcrop.
 *
 * <p>Like {@link dev.evoworld.core.agent.Agent}, a node is an identity whose
 * state changes over time, so it is mutable - but only through
 * {@link #harvest(double)} and {@link #regenerate()}. Everything handed outside
 * the world is a {@link ResourceSnapshot}.
 *
 * <p>Quantities are fractional rather than whole units. Regrowth of a quarter
 * of a berry per tick has to accumulate somewhere, and rounding it away would
 * mean a bush with a slow enough regen rate never recovered at all.
 */
public final class ResourceNode {

    private final ResourceType type;
    private final Location location;
    private final double capacity;
    private double quantity;

    public ResourceNode(ResourceType type, Location location, double capacity, double quantity) {
        this.type = Objects.requireNonNull(type, "type");
        this.location = Objects.requireNonNull(location, "location");
        if (capacity <= 0.0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        if (quantity < 0.0 || quantity > capacity) {
            throw new IllegalArgumentException(
                    "quantity " + quantity + " must be within [0, " + capacity + "]");
        }
        this.capacity = capacity;
        this.quantity = quantity;
    }

    /** A node holding as much as its type allows. */
    public static ResourceNode full(ResourceType type, Location location) {
        return new ResourceNode(type, location, type.capacity(), type.capacity());
    }

    /** A node of standard size holding a given amount. */
    public static ResourceNode holding(ResourceType type, Location location, double quantity) {
        return new ResourceNode(type, location, type.capacity(), quantity);
    }

    public ResourceType type() {
        return type;
    }

    public Location location() {
        return location;
    }

    public double capacity() {
        return capacity;
    }

    public double quantity() {
        return quantity;
    }

    /** Whether there is anything left to take. */
    public boolean isAvailable() {
        return quantity > 0.0;
    }

    public boolean isDepleted() {
        return !isAvailable();
    }

    public boolean isFull() {
        return quantity >= capacity;
    }

    /** How full the node is, from 0 (empty) to 1 (full). */
    public double availability() {
        return quantity / capacity;
    }

    /**
     * Takes up to {@code requested} from the node.
     *
     * <p>Returns what was actually obtained, which is less than requested when
     * the node runs out. Asking a depleted node for berries is not an error -
     * it is the answer "there are none", and an agent finding that out the hard
     * way is exactly the kind of experience later milestones will remember.
     *
     * @return the amount actually harvested, never more than was asked for
     */
    public double harvest(double requested) {
        if (requested < 0.0) {
            throw new IllegalArgumentException("cannot harvest a negative amount: " + requested);
        }
        double taken = Math.min(requested, quantity);
        quantity -= taken;
        return taken;
    }

    /** Empties the node in one go, returning what was there. */
    public double harvestAll() {
        return harvest(quantity);
    }

    /**
     * Applies one tick of regrowth, for a tick of {@code tick}, up to the node's capacity.
     *
     * <p>Non-renewable types are unaffected, and an empty renewable node does
     * recover - a picked bush grows its berries back.
     */
    public void regenerate(Duration tick) {
        regenerate(tick, 1L);
    }

    /**
     * Several ticks of regrowth at once, for ground nobody has been near.
     *
     * <p>Regrowth is a flat rate up to capacity, so a hundred ticks applied
     * together come to exactly what a hundred ticks applied one at a time
     * would - which is what lets a world leave distant chunks alone until
     * somebody walks back into them.
     */
    public void regenerate(Duration tick, long ticks) {
        if (!type.isRenewable() || ticks <= 0L) {
            return;
        }
        quantity = Math.min(capacity, quantity + type.regenPerTick(tick) * ticks);
    }

    public ResourceSnapshot snapshot() {
        return new ResourceSnapshot(type, location, quantity, capacity);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT, "%s at %s %.1f/%.1f", type.label(), location, quantity, capacity);
    }
}
