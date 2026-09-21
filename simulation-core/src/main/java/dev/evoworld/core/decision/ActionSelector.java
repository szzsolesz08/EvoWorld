package dev.evoworld.core.decision;

import java.util.List;

/**
 * Picks one scored option to commit to.
 *
 * <p>Separate from evaluation because choosing is not the same as scoring.
 * Taking the best is one policy; sampling proportionally, or taking the best
 * with a chance of a mistake, are others, and swapping between them should not
 * disturb anything that produces the scores.
 */
public interface ActionSelector {

    /**
     * @param scored every option, already evaluated and never empty
     * @return the one the agent commits to, which must be from the list
     */
    Decision select(List<Decision> scored);
}
