package dev.evoworld.core.decision;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A candidate action together with the score it was given and why.
 *
 * <p>Keeping the rationale on the decision is what makes the simulation
 * observable: the log can show not just what the agent did but what it
 * considered and how close the alternatives were.
 *
 * @param goalName the goal the action serves, when the decision came from one
 * @param target where the action heads, for decisions that travel; choosing
 *     one is what sets an agent out on an intent
 */
public record Decision(
        Action action,
        double utility,
        String rationale,
        Optional<String> goalName,
        Optional<Location> target) {

    public Decision {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(goalName, "goalName");
        Objects.requireNonNull(target, "target");
    }

    /** A decision that serves no named goal and heads nowhere in particular. */
    public Decision(Action action, double utility, String rationale) {
        this(action, utility, rationale, Optional.empty(), Optional.empty());
    }

    /** The scored form of {@code candidate}, keeping what it is for and where it goes. */
    public static Decision of(ActionCandidate candidate, double utility, String rationale) {
        return new Decision(
                candidate.action(),
                utility,
                rationale,
                Optional.of(candidate.goalName()),
                candidate.target());
    }

    /** The same decision, scored differently and explained further. */
    public Decision rescored(double newUtility, String newRationale) {
        return new Decision(action, newUtility, newRationale, goalName, target);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s(%.2f) %s", action.name(), utility, rationale);
    }
}
