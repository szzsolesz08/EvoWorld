package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.RememberedResource;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offers to walk to somewhere the agent remembers but cannot see.
 *
 * <p>The whole point of having a memory. Without it an agent that wandered out
 * of sight of the only spring had no candidate to head back, and its best
 * option was to wander at random until it stumbled on water or died of thirst.
 * With it, "water eleven steps west, seen sixty ticks ago" is a real option,
 * competing on the same scale as everything else.
 *
 * <p>It competes honestly. The candidate carries the memory's faded confidence
 * straight into the utility formula, so a half-forgotten spring is worth half a
 * remembered one, and both are worth less than water the agent can actually
 * see. Nothing here decides that a belief is good enough to act on - the
 * arithmetic does.
 */
public final class ApproachRememberedProposer implements ActionProposer {

    private final double moveEnergyCost;

    private final int stepsPerTick;
    private final int gatherTicks;

    public ApproachRememberedProposer(double moveEnergyCost) {
        this(moveEnergyCost, 1, 2);
    }

    /** @param gatherTicks how long picking food takes once there, before any of it is eaten */
    public ApproachRememberedProposer(double moveEnergyCost, int stepsPerTick, int gatherTicks) {
        this.moveEnergyCost = moveEnergyCost;
        this.stepsPerTick = stepsPerTick;
        this.gatherTicks = gatherTicks;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Needs needs = perception.needs();
        List<ActionCandidate> candidates = new ArrayList<>();

        for (RememberedResource place : perception.remembered()) {
            if (place.stepsAway() == 0) {
                continue;
            }
            Knowledge knowledge = perception.knowledge();
            if (needs.thirst() >= Needs.NOTICEABLE
                    && ExpectedRelief.thinksItIsADrink(knowledge, place.type())) {
                candidates.add(head(
                        place,
                        SatisfyThirstGoal.NAME,
                        ExpectedRelief.fromDrinking(knowledge, needs, place.type(),
                                UntestedGuess.unitsExpected(
                                        knowledge, BeliefKind.HYDRATION, place.type(), ExpectedRelief.FILL_UP)),
                        UntestedGuess.worthIt(knowledge, BeliefKind.HYDRATION, place.type()),
                        knowledge,
                        0));
            }
            if (needs.hunger() >= Needs.NOTICEABLE
                    && ExpectedRelief.thinksItIsFood(knowledge, place.type())) {
                candidates.add(head(
                        place,
                        SatisfyHungerGoal.NAME,
                        ExpectedRelief.fromEating(knowledge, needs, place.type(),
                                UntestedGuess.unitsExpected(
                                        knowledge, BeliefKind.NOURISHMENT, place.type(), ExpectedRelief.FILL_UP)),
                        UntestedGuess.worthIt(knowledge, BeliefKind.NOURISHMENT, place.type()),
                        knowledge,
                        gatherTicks + 1));
            }
        }
        return List.copyOf(candidates);
    }

    private ActionCandidate head(
            RememberedResource place,
            String goal,
            double benefit,
            double worthIt,
            Knowledge knowledge,
            int work) {
        int steps = place.stepsAway();
        // Three doubts compound: how well the agent remembers the place, how
        // often places of this kind turn out to still have anything in them,
        // and - as for anything in sight - whether it is worth having at all.
        double reliability =
                knowledge.estimate(BeliefKind.LOCATION_RELIABILITY, place.type());
        return ActionCandidate.heading(
                place.location(),
                new MoveAction(place.location(), moveEnergyCost, stepsPerTick),
                goal,
                benefit,
                clampToFraction(place.confidence() * reliability * worthIt),
                steps,
                steps * moveEnergyCost,
                String.format(
                        Locale.ROOT,
                        "recalls %s %d steps %s, %d ticks ago",
                        place.type().label(),
                        steps,
                        place.direction().label(),
                        place.ticksSinceSeen()),
                work);
    }

    private static double clampToFraction(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }
}
