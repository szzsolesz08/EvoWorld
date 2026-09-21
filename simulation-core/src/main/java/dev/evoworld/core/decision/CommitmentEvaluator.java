package dev.evoworld.core.decision;

import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.perception.Perception;
import java.util.Objects;
import java.util.Optional;

/**
 * Makes carrying on with what the agent set out to do worth a little more
 * than starting something else.
 *
 * <p>Wraps another evaluator and changes one thing: a candidate that continues
 * the agent's current intent - the same goal, towards the same place - scores
 * {@code 1 + commitment} times what it would otherwise. Everything else is
 * scored exactly as the inner evaluator scores it.
 *
 * <p>This is what stops an agent dithering. Two bushes in opposite directions
 * score almost the same, and each step towards one refreshes the memory of
 * the other behind it, so without a thumb on the scale the agent turns round
 * every tick. With one, the bush it chose stays ahead until the other is
 * clearly better.
 *
 * <p>The bonus multiplies, so it never rescues an option that has become
 * worthless: once the agent is no longer thirsty, the walk to water scores
 * near nothing however committed it was. Loyal, not stubborn.
 */
public final class CommitmentEvaluator implements ActionEvaluator {

    /** How much more carrying on is worth than an equal fresh start. */
    public static final double COMMITMENT = 0.3;

    private final ActionEvaluator inner;
    private final double commitment;

    public CommitmentEvaluator(ActionEvaluator inner) {
        this(inner, COMMITMENT);
    }

    public CommitmentEvaluator(ActionEvaluator inner, double commitment) {
        this.inner = Objects.requireNonNull(inner, "inner");
        if (commitment < 0.0) {
            throw new IllegalArgumentException(
                    "commitment must not be negative, was " + commitment);
        }
        this.commitment = commitment;
    }

    @Override
    public Decision evaluate(ActionCandidate candidate, Motivation motivation, Perception perception) {
        Decision scored = inner.evaluate(candidate, motivation, perception);
        Optional<Intent> intent = perception.self().intent();
        if (intent.isEmpty() || !candidate.continues(intent.get())) {
            return scored;
        }
        return scored.rescored(
                scored.utility() * (1.0 + commitment),
                scored.rationale() + " (keeping on)");
    }
}
