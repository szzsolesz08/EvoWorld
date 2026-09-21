package dev.evoworld.core.decision;

import java.util.List;

/**
 * Takes the best-scoring option, every time.
 *
 * <p>Ties go to the earlier candidate, which makes the choice reproducible
 * rather than dependent on how the list happened to be built. No randomness and
 * no exploration bonus: an agent that occasionally tries the second-best thing
 * is a learning strategy, and belongs with learning.
 */
public final class HighestUtilitySelector implements ActionSelector {

    @Override
    public Decision select(List<Decision> scored) {
        if (scored.isEmpty()) {
            throw new IllegalArgumentException("an agent must have something to choose from");
        }
        Decision best = scored.get(0);
        for (Decision option : scored) {
            // Strictly greater, so an earlier candidate keeps a tie.
            if (option.utility() > best.utility()) {
                best = option;
            }
        }
        return best;
    }
}
