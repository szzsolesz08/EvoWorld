package dev.evoworld.core.agent;

import java.util.Objects;

/**
 * What a tick asked of the body: how hard it worked, and how comfortably it
 * recovered.
 *
 * <p>{@code comfort} scales recovery only - energy coming back and wounds
 * mending - and never what a tick costs. It is 1 for anything done on bare
 * ground. It is the hook for equipment: a bed is a sleep with more comfort,
 * so the same hours of sleep bring more back, and nothing in the metabolism
 * has to know that beds exist.
 *
 * @param level how hard the agent worked
 * @param comfort how much of the usual recovery it gets, 1 being the usual
 */
public record Exertion(ActivityLevel level, double comfort) {

    public Exertion {
        Objects.requireNonNull(level, "level");
        if (!(comfort > 0.0) || Double.isInfinite(comfort)) {
            throw new IllegalArgumentException("comfort must be positive, was " + comfort);
        }
    }

    /** At this level, with no help. */
    public static Exertion of(ActivityLevel level) {
        return new Exertion(level, 1.0);
    }
}
