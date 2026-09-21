package dev.evoworld.core.decision;

import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;

/**
 * Puts a number on a candidate.
 *
 * <p>The seam the milestone exists to leave open. {@link UtilityEvaluator} is
 * the MVP's answer, but a learned scorer, a planner's cost estimate or a neural
 * policy would implement this same interface and drop in without the proposers
 * or the selector noticing.
 */
public interface ActionEvaluator {

    Decision evaluate(ActionCandidate candidate, Motivation motivation, Perception perception);
}
