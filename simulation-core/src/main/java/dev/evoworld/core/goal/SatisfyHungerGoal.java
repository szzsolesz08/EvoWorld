package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want that grows as hunger does.
 *
 * <p>The same curve as thirst, which is what makes the two comparable. Thirst
 * still wins over a long run, but because the body dehydrates faster than it
 * starves rather than because this layer says so.
 */
public final class SatisfyHungerGoal extends Goal {

    public static final String NAME = "SATISFY_HUNGER";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        double pressure = perception.needs().hunger() / Needs.MAX;
        return pressure * pressure;
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(Locale.ROOT, "hunger at %.1f", perception.needs().hunger());
    }
}
