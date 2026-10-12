package dev.evoworld.core.world;

import dev.evoworld.core.Rates;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * A fire burning in one place.
 *
 * <p>The first thing an agent puts <em>into</em> the world rather than into its
 * pack, and the first that keeps working while nobody is looking at it. It
 * warms the ground around it, it eats the wood it was built from, and when the
 * wood is gone it goes out. Everything else about it follows from those three.
 *
 * <p>Mutable, like a {@link dev.evoworld.core.resource.ResourceNode}: a fire is
 * a thing in the world with a state that changes, not a value.
 */
public final class Fire {

    /** How far the warmth reaches, in steps. */
    public static final int RADIUS = 3;

    /** How much warmer it is right beside the fire, in degrees. */
    public static final double WARMTH = 12.0;

    /** Wood burnt per simulated hour. */
    public static final double WOOD_PER_HOUR = 0.5;

    /** The least wood it takes to get one going at all. */
    public static final double KINDLING = 3.0;

    /** More than a night's worth is wasted on a fire nobody will sit at. */
    public static final double MOST_IT_HOLDS = 8.0;

    private final Location location;
    private double fuel;

    public Fire(Location location, double fuel) {
        this.location = Objects.requireNonNull(location, "location");
        if (fuel < 0.0) {
            throw new IllegalArgumentException("fuel cannot be negative, was " + fuel);
        }
        this.fuel = fuel;
    }

    public static Fire lit(Location location, double fuel) {
        return new Fire(location, Math.min(fuel, MOST_IT_HOLDS));
    }

    public Location location() {
        return location;
    }

    public double fuel() {
        return fuel;
    }

    public boolean isOut() {
        return fuel <= 0.0;
    }

    /** How many more hours it will burn unattended. */
    public double hoursLeft() {
        return fuel / WOOD_PER_HOUR;
    }

    /** How much wood it would take to fill it up. */
    public double roomForFuel() {
        return Math.max(0.0, MOST_IT_HOLDS - fuel);
    }

    /** Adds wood, up to what it can hold; returns how much was actually used. */
    public double feed(double wood) {
        double taken = Math.min(Math.max(0.0, wood), roomForFuel());
        fuel += taken;
        return taken;
    }

    /** Burns for one tick of {@code tick}, and for {@code ticks} of them. */
    public void burn(Duration tick, long ticks) {
        if (ticks <= 0 || isOut()) {
            return;
        }
        fuel = Math.max(0.0, fuel - Rates.perTick(WOOD_PER_HOUR, tick) * ticks);
    }

    /**
     * How much warmer this fire makes a place, in degrees.
     *
     * <p>Full beside it and fading to nothing a step beyond its reach, which is
     * near enough to how a fire feels and keeps "am I warm here" a question
     * about where the agent is standing.
     */
    public double warmthAt(Location place) {
        if (isOut()) {
            return 0.0;
        }
        int steps = location.manhattanDistanceTo(Objects.requireNonNull(place, "place"));
        if (steps > RADIUS) {
            return 0.0;
        }
        return WARMTH * (1.0 - steps / (double) (RADIUS + 1));
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT, "fire at %s, %.1f wood left (%.1fh)", location, fuel, hoursLeft());
    }
}
