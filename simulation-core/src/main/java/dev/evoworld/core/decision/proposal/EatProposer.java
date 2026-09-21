package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offers a meal from whatever the agent is carrying and believes edible.
 *
 * <p>Eats what it has rather than a fixed portion, so a single unit of
 * something untried is still enough to find out what it does. That matters:
 * an agent that could only ever try things four at a time would rarely get the
 * chance to learn anything.
 */
public final class EatProposer implements ActionProposer {

    private final double portion;

    public EatProposer(double portion) {
        this.portion = portion;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        if (perception.needs().hunger() <= Needs.MIN) {
            return List.of();
        }

        List<ActionCandidate> candidates = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            double carried = perception.inventory().quantityOf(type);
            double meal = Math.min(portion, carried);
            if (meal <= 0.0 || !ExpectedRelief.thinksItIsFood(perception.knowledge(), type)) {
                continue;
            }

            candidates.add(new ActionCandidate(
                    new EatAction(type, meal),
                    SatisfyHungerGoal.NAME,
                    ExpectedRelief.fromEating(
                            perception.knowledge(), perception.needs(), type, meal),
                    Math.max(
                            perception.knowledge().confidence(BeliefKind.NOURISHMENT, type),
                            UntestedGuess.CONFIDENCE),
                    0,
                    0.0,
                    String.format(Locale.ROOT, "carrying %.1f %s", carried, type.label())));
        }
        return List.copyOf(candidates);
    }
}
