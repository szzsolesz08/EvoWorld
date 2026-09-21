package dev.evoworld.core.knowledge;

import java.util.Locale;
import java.util.Objects;

/**
 * One thing an agent has worked out, and how sure it is.
 *
 * <p>The estimate is a running mean of everything observed, and the confidence
 * is a function of how many times it has looked. Both are arithmetic anyone can
 * check by hand - there is no model here, just counting.
 *
 * <pre>
 *   estimate  &lt;- estimate + (observed - estimate) / (observations + 1)
 *   confidence = observations / (observations + 3)
 * </pre>
 *
 * <p>One observation buys a quarter of the agent's confidence, three buys half,
 * nine buys three quarters. It never reaches certainty, which is the right
 * shape: an agent that has eaten nine berries should be fairly sure and not
 * completely sure.
 *
 * @param observations how many times the agent has seen this for itself
 */
public record Belief(BeliefKind kind, String subject, double estimate, int observations) {

    /** How many observations buy half the agent's confidence. */
    public static final int CONFIDENCE_HALF_LIFE = 3;

    public Belief {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        if (observations < 0) {
            throw new IllegalArgumentException("cannot have observed something a negative number of times");
        }
        if (Double.isNaN(estimate) || Double.isInfinite(estimate)) {
            throw new IllegalArgumentException("an estimate must be a real number, was " + estimate);
        }
    }

    /** What an agent assumes before it has any evidence at all. */
    public static Belief assumed(BeliefKind kind, String subject) {
        return new Belief(kind, subject, kind.prior(), 0);
    }

    /** How much the agent trusts its own estimate, from nothing towards one. */
    public double confidence() {
        return (double) observations / (observations + CONFIDENCE_HALF_LIFE);
    }

    /** Whether this is a guess or something the agent has actually seen. */
    public boolean isAssumed() {
        return observations == 0;
    }

    /**
     * What the belief is worth acting on: a large estimate held loosely is not
     * worth much more than a small one held firmly.
     */
    public double strength() {
        return estimate * confidence();
    }

    /**
     * The belief after seeing one more case.
     *
     * <p>The first observation replaces the prior outright rather than
     * averaging with it - a guess should not survive contact with evidence.
     */
    public Belief updatedWith(double observed) {
        if (isAssumed()) {
            return new Belief(kind, subject, observed, 1);
        }
        double corrected = estimate + (observed - estimate) / (observations + 1);
        return new Belief(kind, subject, corrected, observations + 1);
    }

    /** "berries nourish 8.0 hunger per unit (0.75 sure, 9 times)" */
    public String describe() {
        if (isAssumed()) {
            return String.format(
                    Locale.ROOT,
                    "%s %s: assumes %.1f %s (untested)",
                    subject.toLowerCase(Locale.ROOT),
                    kind.name().toLowerCase(Locale.ROOT),
                    estimate,
                    kind.unit());
        }
        return String.format(
                Locale.ROOT,
                "%s %s: %.1f %s (%.2f sure after %d)",
                subject.toLowerCase(Locale.ROOT),
                kind.name().toLowerCase(Locale.ROOT),
                estimate,
                kind.unit(),
                confidence(),
                observations);
    }

    @Override
    public String toString() {
        return describe();
    }
}
