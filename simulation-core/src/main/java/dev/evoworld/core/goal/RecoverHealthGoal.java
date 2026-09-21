package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want to mend.
 *
 * <p>Health is a reserve, like energy, and the want follows the same squared
 * curve of what is missing: a scratch is worth nothing, being half dead is
 * worth a great deal. What serves it is looking after the body and resting,
 * because that is when a body heals - and the goal knows nothing of either.
 */
public final class RecoverHealthGoal extends Goal {

    public static final String NAME = "RECOVER_HEALTH";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        double injury = (Needs.MAX - perception.needs().health()) / Needs.MAX;
        return injury * injury;
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(Locale.ROOT, "health at %.1f", perception.needs().health());
    }
}
