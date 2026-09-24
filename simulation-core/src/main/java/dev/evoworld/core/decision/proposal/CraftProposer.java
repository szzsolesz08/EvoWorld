package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.CraftAction;
import dev.evoworld.core.craft.Recipe;
import dev.evoworld.core.craft.Recipes;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.StayProvisionedGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offers to make something out of what the agent is already carrying.
 *
 * <p>Nothing about making things is special. A craft is an offer like any
 * other: it serves a want ({@link StayProvisionedGoal} - the want to have what
 * you will need before you need it), it is worth what the thing would do for
 * that want, the agent is as sure of that as experience allows, and it costs
 * time and energy which the evaluator charges for. An agent that has never
 * made anything is going on a hunch; one whose last flask saw it through a dry
 * afternoon is not.
 */
public final class CraftProposer implements ActionProposer {

    private final Duration tick;

    public CraftProposer(Duration tick) {
        this.tick = tick;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        List<ActionCandidate> candidates = new ArrayList<>();
        for (Recipe recipe : Recipes.known()) {
            double benefit = RecipeWorth.benefit(recipe, perception);
            if (benefit <= 0.0 || !hasMaterialsFor(recipe, perception)) {
                continue;
            }
            candidates.add(new ActionCandidate(
                    new CraftAction(recipe, recipe.ticks(tick)),
                    StayProvisionedGoal.NAME,
                    benefit,
                    RecipeWorth.confidence(recipe, perception),
                    0,
                    recipe.energyCost(),
                    "making a " + recipe.makes().label() + " from what it carries"));
        }
        return List.copyOf(candidates);
    }

    private static boolean hasMaterialsFor(Recipe recipe, Perception perception) {
        for (Map.Entry<ResourceType, Double> needed : recipe.needs().entrySet()) {
            if (perception.inventory().quantityOf(needed.getKey()) < needed.getValue()) {
                return false;
            }
        }
        return true;
    }
}
