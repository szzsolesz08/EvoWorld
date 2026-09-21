package dev.evoworld.core.decision;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.world.Location;
import java.util.Objects;
import java.util.Optional;

/**
 * An action worth considering, and what it is for.
 *
 * <p>This is the join between goals and actions, and it is the reason neither
 * has to know about the other. A goal says "satisfy thirst" without knowing
 * that drinking exists; a {@link DrinkAction} relieves thirst without knowing
 * that wanting is a thing. The proposer that builds this candidate is the only
 * component that knows drinking serves thirst, and it says so here, once.
 *
 * <p>Everything the evaluator needs is on the candidate, so scoring never has
 * to inspect the action to work out what kind it is - which is what keeps the
 * evaluator free of a switch over action types.
 *
 * @param goalName           the goal this would serve, by name
 * @param expectedBenefit    how much of that goal it would satisfy, 0 to 1
 * @param confidence         how sure the agent is, 0 to 1 - full for something
 *     it can see, less for a guess. This is where remembered knowledge will
 *     enter when there is any.
 * @param stepsAway          how far off the payoff is, in moves
 * @param expectedEnergyCost energy the whole undertaking would cost, including
 *     the walk there
 * @param target             where the undertaking heads, for candidates that
 *     travel; what lets an agent recognise that an option carries on what it
 *     already set out to do
 */
public record ActionCandidate(
        Action action,
        String goalName,
        double expectedBenefit,
        double confidence,
        int stepsAway,
        double expectedEnergyCost,
        String rationale,
        Optional<Location> target) {

    public ActionCandidate {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(goalName, "goalName");
        Objects.requireNonNull(rationale, "rationale");
        requireFraction(expectedBenefit, "expectedBenefit");
        requireFraction(confidence, "confidence");
        if (stepsAway < 0) {
            throw new IllegalArgumentException("distance must not be negative, was " + stepsAway);
        }
        if (expectedEnergyCost < 0.0) {
            throw new IllegalArgumentException(
                    "energy cost must not be negative, was " + expectedEnergyCost);
        }
    }

    /** A candidate that goes nowhere in particular. */
    public ActionCandidate(
            Action action,
            String goalName,
            double expectedBenefit,
            double confidence,
            int stepsAway,
            double expectedEnergyCost,
            String rationale) {
        this(action, goalName, expectedBenefit, confidence, stepsAway, expectedEnergyCost,
                rationale, Optional.empty());
    }

    /** A candidate that heads for {@code target}. */
    public static ActionCandidate heading(
            Location target,
            Action action,
            String goalName,
            double expectedBenefit,
            double confidence,
            int stepsAway,
            double expectedEnergyCost,
            String rationale) {
        return new ActionCandidate(action, goalName, expectedBenefit, confidence, stepsAway,
                expectedEnergyCost, rationale, Optional.of(target));
    }

    /** Whether this carries on {@code intent}: the same goal, towards the same place. */
    public boolean continues(Intent intent) {
        return target.filter(place -> intent.isFor(goalName, place)).isPresent();
    }

    /** Something the agent can do right here, right now. */
    public static ActionCandidate here(
            Action action,
            String goalName,
            double expectedBenefit,
            double energyCost,
            String rationale) {
        return new ActionCandidate(action, goalName, expectedBenefit, 1.0, 0, energyCost, rationale);
    }

    /**
     * How many ticks until the benefit lands: the walk there plus the work
     * itself. What makes a close mediocre option beat a distant perfect one.
     */
    public int ticksToBenefit() {
        return stepsAway + action.durationTicks();
    }

    private static void requireFraction(double value, String what) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(what + " must be within [0, 1], was " + value);
        }
    }

    @Override
    public String toString() {
        return action.name() + " for " + goalName;
    }
}
