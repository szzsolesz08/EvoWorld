package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offers to walk towards something worth reaching.
 *
 * <p>A step towards a spring relieves no thirst, so on its own it would score
 * nothing and an agent would never travel anywhere. The trick is that an
 * approach is valued at whatever waits at the end of it, and the evaluator
 * discounts that by how long the walk takes. Five steps from water is worth
 * most of a drink; fifteen steps is worth almost nothing.
 *
 * <p>When the agent arrives, the walk stops being possible - you cannot move to
 * where you already are - and the drink itself is proposed instead,
 * undiscounted. That handover needs no plan and no state: it falls out of the
 * distance reaching zero.
 */
public final class ApproachResourceProposer implements ActionProposer {

    private final double moveEnergyCost;
    private final double eatPortion;
    private final double drinkGulp;

    public ApproachResourceProposer(double moveEnergyCost, double eatPortion, double drinkGulp) {
        this.moveEnergyCost = moveEnergyCost;
        this.eatPortion = eatPortion;
        this.drinkGulp = drinkGulp;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Needs needs = perception.needs();
        List<ActionCandidate> candidates = new ArrayList<>();

        for (PerceivedResource seen : perception.resources()) {
            if (!seen.isAvailable() || seen.isUnderfoot()) {
                continue;
            }
            Knowledge knowledge = perception.knowledge();
            if (needs.thirst() > Needs.MIN
                    && ExpectedRelief.thinksItIsADrink(knowledge, seen.type())) {
                candidates.add(approach(
                        seen,
                        SatisfyThirstGoal.NAME,
                        ExpectedRelief.fromDrinking(
                                knowledge, needs, seen.type(),
                                Math.min(drinkGulp, seen.quantity())),
                        knowledge.confidence(BeliefKind.HYDRATION, seen.type())));
            }
            if (needs.hunger() > Needs.MIN
                    && ExpectedRelief.thinksItIsFood(knowledge, seen.type())) {
                candidates.add(approach(
                        seen,
                        SatisfyHungerGoal.NAME,
                        ExpectedRelief.fromEating(
                                knowledge, needs, seen.type(),
                                Math.min(eatPortion, seen.quantity())),
                        knowledge.confidence(BeliefKind.NOURISHMENT, seen.type())));
            }
        }
        return List.copyOf(candidates);
    }

    private ActionCandidate approach(
            PerceivedResource target, String goal, double benefit, double believed) {
        int steps = target.stepsAway();
        return new ActionCandidate(
                new MoveAction(target.location(), moveEnergyCost),
                goal,
                benefit,
                // Sure it is there - it can see it. Only as sure as experience
                // allows that going there is worth the walk.
                Math.max(believed, UntestedGuess.CONFIDENCE),
                steps,
                steps * moveEnergyCost,
                String.format(
                        Locale.ROOT,
                        "%s %d steps %s",
                        target.type().label(),
                        steps,
                        target.direction().label()));
    }
}
