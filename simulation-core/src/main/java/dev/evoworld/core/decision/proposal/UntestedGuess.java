package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.resource.ResourceType;

/**
 * How much weight an agent gives a hunch it has never tested.
 *
 * <p>Deliberately the same as {@link ExploreProposer#SPECULATIVE}: walking over
 * to try something the agent has never tried <em>is</em> exploring, and a hunch
 * about a thing should be worth exactly what a hunch about a place is worth.
 * Anything less and an agent would rather wander at random than investigate the
 * spring it is looking at, which is how one dies of thirst within sight of
 * water.
 *
 * <p>It still loses to a proven belief, which reaches 0.75 after nine
 * observations, so experience always beats a guess. It doubles as a floor under
 * learned confidence, so a single observation never leaves an agent less sure
 * than it was while guessing.
 */
final class UntestedGuess {

    /** What a complete guess is worth next to a certainty. */
    static final double CONFIDENCE = ExploreProposer.SPECULATIVE;

    /**
     * How sure an agent is that a thing is worth having for {@code kind},
     * never less than a guess.
     *
     * <p>Every proposer that offers a thing for what it does charges this
     * doubt, whether the thing is in sight or remembered. When only the ones
     * in sight did, a bush out of sight scored higher than an identical bush in
     * view, and an agent between two walked back and forth, each step putting
     * the other one out of sight.
     */
    static double worthIt(Knowledge knowledge, BeliefKind kind, ResourceType type) {
        return Math.max(knowledge.confidence(kind, type), CONFIDENCE);
    }

    private UntestedGuess() {
    }
}
