package dev.evoworld.core.decision;

import dev.evoworld.core.action.Action;
import java.util.Locale;

/**
 * A candidate action together with the score it was given and why.
 *
 * <p>Keeping the rationale on the decision is what makes the simulation
 * observable: the log can show not just what the agent did but what it
 * considered and how close the alternatives were.
 */
public record Decision(Action action, double utility, String rationale) {

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s(%.2f) %s", action.name(), utility, rationale);
    }
}
