package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.craft.Recipe;
import dev.evoworld.core.craft.Recipes;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.StayProvisionedGoal;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offers to pick up what a thing worth making is made of.
 *
 * <p>The only place an agent does something for a reason one step removed from
 * a need. Wood is not food, not drink and not rest, and until there was
 * something to make out of it, an agent that picked it up was making a
 * mistake. It is deliberately one step and no further - there is no plan here,
 * only "this is part of something I want" - and a material is worth the share
 * of that thing which is still missing, so the last armful of wood for a flask
 * is worth more than the first.
 *
 * <p>An agent that has everything it needs offers nothing: the want is
 * answered by making the thing, not by hoarding what it is made of.
 */
public final class FetchMaterialsProposer implements ActionProposer {

    /**
     * How far out of its way an agent will go for a material.
     *
     * <p>A few steps, no more. Fetching wood serves a want one step removed
     * from any need, and this decision layer plans no further than the next
     * action: an agent that would cross the country for wood it might one day
     * make something of arrives hungry, far from anything to eat. What it will
     * do is pick up what it passes.
     */
    private static final int WORTH_A_DETOUR = 6;

    private final double gatherAmount;
    private final double gatherEnergyCost;
    private final int gatherTicks;
    private final double moveEnergyCost;
    private final int stepsPerTick;

    public FetchMaterialsProposer(
            double gatherAmount,
            double gatherEnergyCost,
            int gatherTicks,
            double moveEnergyCost,
            int stepsPerTick) {
        this.gatherAmount = gatherAmount;
        this.gatherEnergyCost = gatherEnergyCost;
        this.gatherTicks = gatherTicks;
        this.moveEnergyCost = moveEnergyCost;
        this.stepsPerTick = stepsPerTick;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Map<ResourceType, Double> wanted = whatIsStillNeeded(perception);
        if (wanted.isEmpty()) {
            return List.of();
        }

        double sureness = bestGuess(perception);
        List<ActionCandidate> candidates = new ArrayList<>();
        wanted.forEach((type, worth) -> {
            if (perception.roomFor(type) <= 0.0) {
                return;
            }
            perception.underfoot()
                    .filter(PerceivedResource::isAvailable)
                    .filter(here -> here.type() == type)
                    .ifPresent(here -> candidates.add(pickUp(here, worth, sureness)));
            perception.nearest(type)
                    .filter(PerceivedResource::isAvailable)
                    .filter(seen -> !seen.isUnderfoot())
                    .filter(seen -> seen.stepsAway() <= WORTH_A_DETOUR)
                    .ifPresent(seen -> candidates.add(goTo(seen, worth, sureness)));
        });
        return List.copyOf(candidates);
    }

    /**
     * What is missing for the things worth making, and how much of each thing's
     * worth the missing material carries.
     */
    private static Map<ResourceType, Double> whatIsStillNeeded(Perception perception) {
        Map<ResourceType, Double> wanted = new LinkedHashMap<>();
        for (Recipe recipe : Recipes.known()) {
            double worth = RecipeWorth.benefit(recipe, perception);
            if (worth <= 0.0) {
                continue;
            }
            recipe.needs().forEach((type, needed) -> {
                double missing = needed - perception.inventory().quantityOf(type);
                if (missing <= 0.0) {
                    return;
                }
                wanted.merge(type, worth * (missing / recipe.totalInput()), Math::max);
            });
        }
        return wanted;
    }

    /**
     * How sure the agent is that fetching this is worth anything, which is
     * exactly how sure it is that the thing it is for is worth having. Before
     * this was tied together, wood was fetched with the certainty of a meal.
     */
    private static double bestGuess(Perception perception) {
        return Recipes.known().stream()
                .filter(recipe -> RecipeWorth.benefit(recipe, perception) > 0.0)
                .mapToDouble(recipe -> RecipeWorth.confidence(recipe, perception))
                .max()
                .orElse(0.0);
    }

    private ActionCandidate pickUp(PerceivedResource here, double worth, double sureness) {
        return new ActionCandidate(
                new GatherAction(here.type(), gatherAmount, gatherEnergyCost, gatherTicks),
                StayProvisionedGoal.NAME,
                worth,
                sureness,
                0,
                gatherEnergyCost,
                String.format(
                        Locale.ROOT,
                        "%.1f %s underfoot, for making things",
                        here.quantity(),
                        here.type().label()));
    }

    private ActionCandidate goTo(PerceivedResource seen, double worth, double sureness) {
        int steps = seen.stepsAway();
        return ActionCandidate.heading(
                seen.location(),
                new MoveAction(seen.location(), moveEnergyCost, stepsPerTick),
                StayProvisionedGoal.NAME,
                worth,
                sureness,
                steps,
                steps * moveEnergyCost,
                String.format(
                        Locale.ROOT,
                        "%s %d steps %s, for making things",
                        seen.type().label(),
                        steps,
                        seen.direction().label()),
                gatherTicks);
    }
}
