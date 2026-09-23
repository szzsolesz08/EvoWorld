package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.KnownPlenty;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offers to go where there was a lot of something.
 *
 * <p>The difference between this and {@link ApproachRememberedProposer} is the
 * difference between "there was a bush over there" and "that whole slope was
 * berries". One bush is a meal; a field is worth a walk, and worth the walk
 * again tomorrow. Which is what makes an endless world of berry fields and
 * bare hills worth having: the agent can tell them apart and remember which
 * was which.
 *
 * <p>What it offers is the middle of a region it has been to. The memory is
 * the most it saw there at once, faded by how long ago that was, so a field
 * seen this morning beats one half remembered from last week, and somewhere it
 * has never been offers nothing at all - this is memory, not second sight.
 */
public final class HeadForPlentyProposer implements ActionProposer {

    private final double moveEnergyCost;
    private final int stepsPerTick;
    private final int gatherTicks;

    public HeadForPlentyProposer(double moveEnergyCost) {
        this(moveEnergyCost, 1, 2);
    }

    public HeadForPlentyProposer(double moveEnergyCost, int stepsPerTick, int gatherTicks) {
        this.moveEnergyCost = moveEnergyCost;
        this.stepsPerTick = stepsPerTick;
        this.gatherTicks = gatherTicks;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Needs needs = perception.needs();
        Knowledge knowledge = perception.knowledge();
        List<ActionCandidate> candidates = new ArrayList<>();

        for (KnownPlenty known : perception.plenty()) {
            if (known.stepsAway() == 0 || known.belief() <= 0.0) {
                continue;
            }
            if (needs.thirst() >= Needs.NOTICEABLE
                    && ExpectedRelief.thinksItIsADrink(knowledge, known.type())) {
                candidates.add(head(
                        known,
                        SatisfyThirstGoal.NAME,
                        ExpectedRelief.fromDrinking(
                                knowledge, needs, known.type(),
                                UntestedGuess.unitsExpected(
                                        knowledge, BeliefKind.HYDRATION, known.type(), ExpectedRelief.FILL_UP)),
                        UntestedGuess.worthIt(knowledge, BeliefKind.HYDRATION, known.type()),
                        knowledge,
                        0));
            }
            if (needs.hunger() >= Needs.NOTICEABLE
                    && ExpectedRelief.thinksItIsFood(knowledge, known.type())) {
                candidates.add(head(
                        known,
                        SatisfyHungerGoal.NAME,
                        ExpectedRelief.fromEating(
                                knowledge, needs, known.type(),
                                UntestedGuess.unitsExpected(
                                        knowledge, BeliefKind.NOURISHMENT, known.type(), ExpectedRelief.FILL_UP)),
                        UntestedGuess.worthIt(knowledge, BeliefKind.NOURISHMENT, known.type()),
                        knowledge,
                        gatherTicks + 1));
            }
        }
        return List.copyOf(candidates);
    }

    private ActionCandidate head(
            KnownPlenty known,
            String goal,
            double benefit,
            double worthIt,
            Knowledge knowledge,
            int work) {
        int steps = known.stepsAway();
        // Three doubts again: how much was there and how long ago (the belief),
        // how often places of this kind still hold anything, and whether the
        // thing is worth having at all.
        double reliability = knowledge.estimate(BeliefKind.LOCATION_RELIABILITY, known.type());
        return ActionCandidate.heading(
                known.where(),
                new MoveAction(known.where(), moveEnergyCost, stepsPerTick),
                goal,
                benefit,
                Math.min(1.0, Math.max(0.0, known.belief() * reliability * worthIt)),
                steps,
                steps * moveEnergyCost,
                String.format(
                        Locale.ROOT,
                        "%d %s around %s, %d steps away, seen %d ticks ago",
                        known.seen(),
                        known.type().label(),
                        known.region(),
                        steps,
                        known.ticksSinceSeen()),
                work);
    }
}
