package dev.evoworld.core.decision;

import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.List;

/**
 * Suggests actions worth considering, given what the agent can tell.
 *
 * <p>One proposer per kind of thing an agent might do. Adding a behaviour means
 * adding a proposer to a list - never a branch in a decision tree - which is
 * the whole reason the decision layer is not one long if/else.
 *
 * <p>A proposer only ever sees a {@link Perception}, so it cannot suggest
 * walking to a spring the agent has no way of knowing about. It should propose
 * only what is actually possible right now; the real preconditions are checked
 * again when the action runs, but a candidate that could never succeed is noise
 * in the scoring.
 */
public interface ActionProposer {

    /** Candidates worth weighing, possibly none. */
    List<ActionCandidate> propose(Perception perception, SimulationRandom random);
}
