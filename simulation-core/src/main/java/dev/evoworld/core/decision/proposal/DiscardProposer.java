package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.DiscardAction;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offers to put down what the agent has learned is useless, when it is in the way.
 *
 * <p>Serves hunger, because that is what the room is for: the only reason to
 * carry anything is to eat it later. The offer is worth exactly the room it
 * would make that is actually needed - nothing at all while there is space for
 * another haul, so an agent does not tidy its bag for the sake of it, and a lot
 * once junk is what stands between it and a meal.
 *
 * <p>Only things the agent has <em>tested</em> are offered. A thing it has
 * never tried might still be food, and throwing it away before finding out
 * would be deciding on ignorance.
 */
public final class DiscardProposer implements ActionProposer {

    private final double haul;

    /** @param haul how much room a gather needs, which is the room worth making */
    public DiscardProposer(double haul) {
        this.haul = haul;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        double shortfall = Math.max(0.0, haul - perception.inventory().freeSpace());
        if (shortfall <= 0.0) {
            return List.of();
        }

        Knowledge knowledge = perception.knowledge();
        double capacity = perception.inventory().capacity();
        List<ActionCandidate> candidates = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            double carried = perception.inventory().quantityOf(type);
            if (carried <= 0.0 || !knownToBeUseless(knowledge, type)) {
                continue;
            }
            candidates.add(ActionCandidate.here(
                    new DiscardAction(type, carried),
                    SatisfyHungerGoal.NAME,
                    Math.min(carried, shortfall) / capacity,
                    0.0,
                    String.format(
                            Locale.ROOT,
                            "%.1f useless %s taking up room",
                            carried,
                            type.label())));
        }
        return List.copyOf(candidates);
    }

    private static boolean knownToBeUseless(Knowledge knowledge, ResourceType type) {
        return knowledge.hasTested(BeliefKind.NOURISHMENT, type)
                && knowledge.estimate(BeliefKind.NOURISHMENT, type) <= 0.0;
    }
}
