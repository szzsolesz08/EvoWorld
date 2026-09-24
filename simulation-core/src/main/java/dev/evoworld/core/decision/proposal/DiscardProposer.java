package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.DiscardAction;
import dev.evoworld.core.craft.Recipe;
import dev.evoworld.core.craft.Recipes;
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
            // Materials are worth keeping, but only as much of them as the
            // thing being made actually needs. The rest is ballast, whatever
            // it might have been good for.
            double spare = carried - keptForMaking(type, perception);
            if (spare <= 0.0) {
                continue;
            }
            candidates.add(ActionCandidate.here(
                    new DiscardAction(type, spare),
                    SatisfyHungerGoal.NAME,
                    Math.min(spare, shortfall) / capacity,
                    0.0,
                    String.format(
                            Locale.ROOT,
                            "%.1f useless %s taking up room",
                            spare,
                            type.label())));
        }
        return List.copyOf(candidates);
    }

    /**
     * Useless means good for nothing: not food, not drink, and not enough of a
     * material to matter. Checking only food was enough while water was
     * something you could only drink where you found it - now that it can be
     * carried, an agent that threw out everything it could not eat threw out
     * its own water.
     */
    private static boolean knownToBeUseless(Knowledge knowledge, ResourceType type) {
        return knowsItIsNotFood(knowledge, type) && !knowsItIsADrink(knowledge, type);
    }

    private static boolean knowsItIsNotFood(Knowledge knowledge, ResourceType type) {
        return knowledge.hasTested(BeliefKind.NOURISHMENT, type)
                && knowledge.estimate(BeliefKind.NOURISHMENT, type) <= 0.0;
    }

    private static boolean knowsItIsADrink(Knowledge knowledge, ResourceType type) {
        return knowledge.hasTested(BeliefKind.HYDRATION, type)
                && knowledge.estimate(BeliefKind.HYDRATION, type) > 0.0;
    }

    /**
     * How much of something is worth keeping for what the agent means to make.
     *
     * <p>Wood is not food and never will be, but it is what a flask is made of,
     * and an agent that threw away every mouthful it could not eat would spend
     * its life putting down the very thing it walked out to fetch. Only as much
     * as a wanted recipe calls for, though: a pack full of stone for an axe it
     * does not want is a pack with no room for supper.
     */
    private static double keptForMaking(ResourceType type, Perception perception) {
        double kept = 0.0;
        for (Recipe recipe : Recipes.known()) {
            if (RecipeWorth.benefit(recipe, perception) <= 0.0) {
                continue;
            }
            kept = Math.max(kept, recipe.needs().getOrDefault(type, 0.0));
        }
        return kept;
    }
}
