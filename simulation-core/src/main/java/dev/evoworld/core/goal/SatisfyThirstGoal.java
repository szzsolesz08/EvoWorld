package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want that grows as thirst does.
 *
 * <p>Squared rather than linear, so mild thirst stays a background murmur and
 * severe thirst drowns out everything else. A want that rose in a straight line
 * would leave a nearly dead agent only twice as motivated as a comfortable one.
 */
public final class SatisfyThirstGoal extends Goal {

    public static final String NAME = "SATISFY_THIRST";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        double pressure = perception.needs().thirst() / Needs.MAX;
        return pressure * pressure;
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(Locale.ROOT, "thirst at %.1f", perception.needs().thirst());
    }
}
