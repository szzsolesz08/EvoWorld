package dev.evoworld.core.decision;

import java.util.List;

/**
 * The full result of one round of thinking: every option the agent weighed and
 * the one it committed to.
 *
 * <p>Returning both together is what lets an observer show why the agent acted,
 * and guarantees the logged alternatives are exactly the ones that were scored.
 */
public record Deliberation(List<Decision> considered, Decision chosen) {

    public Deliberation {
        considered = List.copyOf(considered);
        if (considered.isEmpty()) {
            throw new IllegalArgumentException("an agent must always consider at least one option");
        }
        if (!considered.contains(chosen)) {
            throw new IllegalArgumentException("the chosen option must be one of the considered ones");
        }
    }
}
