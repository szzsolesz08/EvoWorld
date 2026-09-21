package dev.evoworld.core.knowledge;

/**
 * A kind of thing an agent can work out about the world.
 *
 * <p>Each is a general claim rather than an episode. Memory holds "I found
 * berries at (4,3) on tick 12"; knowledge holds "berries are worth about eight
 * hunger a unit", which is true wherever and whenever you meet a berry.
 *
 * <p>The prior is what an agent assumes before it has tried anything: that a
 * consumable thing probably helps, by something like the amount such things
 * usually help, held with little confidence. The guess has to be roughly the
 * right size to be any use - a prior so cautious that experimenting never wins
 * would leave an agent dying of thirst beside a spring it never tried - while
 * the low confidence keeps a hunch worth a fraction of a certainty. The result
 * is an agent that tries the unknown when it is desperate, which is exactly
 * when trying the unknown is sensible.
 *
 * @param prior what to assume before any evidence
 * @param unit what the estimate is measured in, for logs
 */
public enum BeliefKind {

    /** How much hunger one unit of something relieves. */
    NOURISHMENT(10.0, "hunger per unit"),

    /** How much thirst one unit of something relieves. */
    HYDRATION(10.0, "thirst per unit"),

    /**
     * How often going somewhere for a resource actually pays off.
     *
     * <p>Not about any one place - that is memory's job - but about whether
     * this kind of thing tends to still be there when you arrive.
     */
    LOCATION_RELIABILITY(0.6, "chance it is still there");

    private final double prior;
    private final String unit;

    BeliefKind(double prior, String unit) {
        this.prior = prior;
        this.unit = unit;
    }

    public double prior() {
        return prior;
    }

    public String unit() {
        return unit;
    }
}
