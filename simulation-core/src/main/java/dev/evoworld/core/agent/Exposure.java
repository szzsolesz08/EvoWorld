package dev.evoworld.core.agent;

import java.util.Locale;

/**
 * What the conditions are doing to a body: the cost of keeping warm, and how
 * well it rests in them.
 *
 * <p>Two numbers, because cold does two different things and the metabolism
 * already treats them differently. Shivering is <em>work</em> - a body burns
 * its own fuel to hold its temperature - so it adds to what a tick costs.
 * Sleeping cold is poor <em>recovery</em> - you wake having got less out of
 * the night - so it scales what a tick gives back, through the same comfort
 * the body already applies to a bed.
 *
 * <p>Darkness belongs here too, and only helps: people sleep better in the
 * dark. It is the one thing about night that is an improvement.
 *
 * <p>Nothing here knows what a fire is. It is handed a temperature, and
 * whatever raised that temperature is somebody else's business.
 */
public record Exposure(double chill, double comfort) {

    /**
     * Warm enough to be doing nothing about it.
     *
     * <p>Not room temperature: someone outdoors and moving is comfortable a
     * good deal cooler than someone sitting still indoors, and the spring days
     * here top out at sixteen.
     */
    public static final double MILD = 15.0;

    /**
     * How much more of its own fuel a body burns for each degree below mild.
     *
     * <p>Gentle, because this is a spring. Ten degrees under - the coldest
     * hour of the night here - costs a tenth more energy and a sixth of a
     * night's rest: enough that a fire is worth the wood, not enough to be
     * dangerous. Set higher, the one scenario where wood is too scarce to
     * burn began losing agents, which is not what a spring is for. A winter
     * will want its own numbers, and its own way of being survived.
     */
    private static final double SHIVERING_PER_DEGREE = 0.010;

    /** How much of a night's rest is lost for each degree below mild. */
    private static final double REST_LOST_PER_DEGREE = 0.016;

    /** Caps, so that a winter worth writing later cannot take everything. */
    private static final double MOST_SHIVERING = 0.5;

    private static final double LEAST_REST = 0.3;

    /** How much better a body sleeps in the dark than in daylight. */
    private static final double DARKNESS_HELPS = 0.15;

    public Exposure {
        if (chill < 0.0) {
            throw new IllegalArgumentException("chill cannot be negative, was " + chill);
        }
        if (!(comfort > 0.0) || Double.isInfinite(comfort)) {
            throw new IllegalArgumentException("comfort must be positive, was " + comfort);
        }
    }

    /** Comfortable, unremarkable conditions: no shivering, ordinary rest. */
    public static Exposure none() {
        return new Exposure(0.0, 1.0);
    }

    /**
     * What a body makes of a temperature and a sky.
     *
     * @param temperature what it feels where the agent is, fires and all
     * @param daylight how light it is, 0 to 1
     */
    public static Exposure of(double temperature, double daylight) {
        double belowMild = Math.max(0.0, MILD - temperature);
        double chill = Math.min(MOST_SHIVERING, SHIVERING_PER_DEGREE * belowMild);
        double rest = Math.max(LEAST_REST, 1.0 - REST_LOST_PER_DEGREE * belowMild);
        double darkness = 1.0 + DARKNESS_HELPS * (1.0 - clampToFraction(daylight));
        return new Exposure(chill, rest * darkness);
    }

    /** Whether the agent is cold enough for it to be costing anything. */
    public boolean isCold() {
        return chill > 0.0;
    }

    private static double clampToFraction(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "chill %.2f, comfort %.2f", chill, comfort);
    }
}
