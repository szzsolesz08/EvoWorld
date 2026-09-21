package dev.evoworld.core.decision.proposal;

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

    private UntestedGuess() {
    }
}
