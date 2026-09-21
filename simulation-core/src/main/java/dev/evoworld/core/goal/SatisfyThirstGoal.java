package dev.evoworld.core.goal;

import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want that grows as thirst does.
 *
 * <p>An {@link Appetite}: a thirst that comes on within a few hours and makes
 * a drink worth having if water is near, and an urgency that drowns out
 * everything else once thirst is severe. Here the appetite keeps building
 * across the whole range ({@link #COMFORTABLE_UP_TO} is the top of the bar):
 * with no way to carry water, a trip to a spring is worth making when properly
 * thirsty, for a long drink, rather than for a single gulp every few hours.
 */
public final class SatisfyThirstGoal extends Goal {

    /** The thirst at which appetite has fully arrived and only urgency still grows. */
    public static final double COMFORTABLE_UP_TO = 100.0;

    public static final String NAME = "SATISFY_THIRST";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        return Appetite.want(perception.needs().thirst(), COMFORTABLE_UP_TO);
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(Locale.ROOT, "thirst at %.1f", perception.needs().thirst());
    }
}
