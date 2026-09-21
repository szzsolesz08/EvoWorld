package dev.evoworld.core.goal;

import java.util.Locale;
import java.util.Objects;

/**
 * One goal, weighed against how the agent currently is.
 *
 * <p>The reason travels with the number for the same reason it does on a
 * {@link dev.evoworld.core.decision.Decision}: a log that says
 * "thirst 0.81" is a measurement, and one that says "thirst 0.81 (thirst at
 * 90.0)" is an explanation.
 */
public record GoalPriority(Goal goal, double priority, String reason) {

    public GoalPriority {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(reason, "reason");
        if (priority < Goal.MIN_PRIORITY || priority > Goal.MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "priority must be within [0, 1], was " + priority);
        }
    }

    public String name() {
        return goal.name();
    }

    /** Whether the agent wants this at all. */
    public boolean isWanted() {
        return priority > Goal.MIN_PRIORITY;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s %.2f (%s)", goal.name(), priority, reason);
    }
}
