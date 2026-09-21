package dev.evoworld.core.goal;

import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want that grows as hunger does.
 *
 * <p>The same {@link Appetite} as thirst, which is what makes the two
 * comparable: peckish a few hours after a meal, and single-minded when
 * starving. Appetite is full at {@link #COMFORTABLE_UP_TO}, about three days
 * after a meal. Thirst still wins over a long run, because the body dries out
 * faster than it starves, not because this layer says so.
 */
public final class SatisfyHungerGoal extends Goal {

    /** The hunger at which appetite has fully arrived and only urgency still grows. */
    public static final double COMFORTABLE_UP_TO = 50.0;

    public static final String NAME = "SATISFY_HUNGER";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        return Appetite.want(perception.needs().hunger(), COMFORTABLE_UP_TO);
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(Locale.ROOT, "hunger at %.1f", perception.needs().hunger());
    }
}
