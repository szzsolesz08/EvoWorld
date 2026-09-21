package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want that grows as energy runs out.
 *
 * <p>Energy is a reserve rather than a pressure, so the want is driven by what
 * is missing. The same squared curve as hunger and thirst: being a little tired
 * is worth almost nothing next to being unable to stand.
 */
public final class RecoverEnergyGoal extends Goal {

    public static final String NAME = "RECOVER_ENERGY";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        double tiredness = (Needs.MAX - perception.needs().energy()) / Needs.MAX;
        return tiredness * tiredness;
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(Locale.ROOT, "energy at %.1f", perception.needs().energy());
    }
}
