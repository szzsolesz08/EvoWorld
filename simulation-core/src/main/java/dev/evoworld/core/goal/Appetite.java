package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Needs;

/**
 * How much an agent wants to eat or drink, at a given hunger or thirst.
 *
 * <p>Two things at once, as in a person. An <em>appetite</em> that comes early
 * and levels off: a few hours after a meal you are peckish and would eat if
 * food were at hand, but you would not cross a valley for it. And an
 * <em>urgency</em> that is nothing at first and everything at the end: the
 * squared curve the needs always had, so a starving agent thinks of nothing
 * else.
 *
 * <p>The appetite is why an agent eats two or three times a day rather than
 * waiting until the need is dangerous. When it acts still depends on what is
 * near and what else it could be doing - food in the pack is eaten sooner than
 * food a long walk away, which is what will make carrying food worth it.
 */
final class Appetite {

    /** How much of the want is appetite; the rest is urgency. */
    static final double APPETITE_SHARE = 0.4;

    private Appetite() {
    }

    /**
     * @param pressure the need, 0 to 100
     * @param fullAt the pressure at which appetite stops growing and only urgency is left
     */
    static double want(double pressure, double fullAt) {
        double appetite = Math.min(1.0, Math.max(0.0, pressure) / fullAt);
        double urgency = Math.max(0.0, pressure) / Needs.MAX;
        return APPETITE_SHARE * appetite + (1.0 - APPETITE_SHARE) * urgency * urgency;
    }
}
