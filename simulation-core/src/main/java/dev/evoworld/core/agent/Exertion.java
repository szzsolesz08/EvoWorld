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
 * <p>{@code chill} is the other half of the same idea and works the other way
 * round: it is the share of extra energy a cold body spends holding its own
 * temperature, so it scales the cost and never the recovery. A tick spent
 * shivering is a tick that asked more of the body, whatever the body was doing.
 *
 * @param level how hard the agent worked
 * @param comfort how much of the usual recovery it gets, 1 being the usual
 * @param chill how much more than usual it spent keeping warm, 0 being nothing
 */
public record Exertion(ActivityLevel level, double comfort, double chill) {

    public Exertion {
        Objects.requireNonNull(level, "level");
        if (!(comfort > 0.0) || Double.isInfinite(comfort)) {
            throw new IllegalArgumentException("comfort must be positive, was " + comfort);
        }
        if (chill < 0.0 || Double.isInfinite(chill)) {
            throw new IllegalArgumentException("chill must not be negative, was " + chill);
        }
    }

    /** At this level, in conditions that ask nothing extra. */
    public Exertion(ActivityLevel level, double comfort) {
        this(level, comfort, 0.0);
    }

    /** At this level, with no help. */
    public static Exertion of(ActivityLevel level) {
        return new Exertion(level, 1.0, 0.0);
    }

    /**
     * The same work, done in these conditions.
     *
     * <p>Comforts multiply: a warm night in a good bed is better than either.
     */
    public Exertion in(Exposure conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return new Exertion(level, comfort * conditions.comfort(), conditions.chill());
    }
}
