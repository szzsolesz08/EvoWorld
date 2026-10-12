package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Exposure;
import dev.evoworld.core.craft.Recipe;
import dev.evoworld.core.craft.Recipes;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.KeepWarmGoal;
import dev.evoworld.core.goal.StayProvisionedGoal;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Fire;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    /**
     * The least a log or an outcrop has to have left in it to be worth going to.
     *
     * <p>A stripped node still counts as something the agent can see, and
     * without this it is worth the whole of what the agent needs right up to
     * the moment it arrives and finds a twig. That asymmetry - full price from
     * one tile, nothing from the next - had an agent stepping back and forth
     * between a bare log and its supper for hours.
     */
    private static final double WORTH_FETCHING = 1.0;

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
        Map<ResourceType, Want> wanted = whatIsStillNeeded(perception);
        if (wanted.isEmpty()) {
            return List.of();
        }

        double sureness = bestGuess(perception);
        // Firewood and flask-wood are both things the agent means to have
        // before it needs them, so they serve the same want.
        boolean forWarmth = false;
        List<ActionCandidate> candidates = new ArrayList<>();
        wanted.forEach((type, want) -> {
            double room = perception.roomFor(type);
            if (room <= 0.0) {
                return;
            }
            String goal = goalFor(type, forWarmth);
            perception.underfoot()
                    .filter(here -> here.type() == type)
                    .filter(here -> worthTaking(here, room))
                    .ifPresent(here -> candidates.add(
                            pickUp(here, want.share(here, room), sureness, goal)));
            nearestWorthTaking(perception, type, room)
                    .ifPresent(seen -> candidates.add(
                            goTo(seen, want.share(seen, room), sureness, goal)));
        });
        return List.copyOf(candidates);
    }

    /**
     * The nearest one with enough left in it to be worth fetching.
     *
     * <p>Not simply the nearest: a stripped log a step away would then be the
     * only one the agent ever considered, and the offer would appear and
     * vanish as it moved, which is a recipe for walking in circles. It looks
     * past the bare ones to the next real thing.
     */
    private static Optional<PerceivedResource> nearestWorthTaking(
            Perception perception, ResourceType type, double room) {
        return perception.resourcesOfType(type).stream()
                .filter(seen -> !seen.isUnderfoot())
                .filter(seen -> seen.stepsAway() <= WORTH_A_DETOUR)
                .filter(seen -> worthTaking(seen, room))
                .findFirst();
    }

    /** Whether there is enough left in it to be worth the walk and the work. */
    private static boolean worthTaking(PerceivedResource node, double room) {
        return node.isAvailable() && Math.min(node.quantity(), room) >= WORTH_FETCHING;
    }

    /**
     * Something the agent wants, and how much of it is still missing.
     *
     * <p>Both halves matter when valuing a particular bush or log. Walking to a
     * stripped one is walking for nothing, and an agent that valued the trip at
     * what it needed rather than at what was there stepped back and forth
     * between a bare log and its supper for hours.
     *
     * @param worth what having the whole of it would be worth
     * @param missing how much more of it the agent needs
     */
    private record Want(double worth, double missing) {

        /** What this particular node is worth: the share of the shortfall it covers. */
        double share(PerceivedResource node, double room) {
            double thereForTheTaking = Math.min(node.quantity(), room);
            return worth * Math.min(1.0, thereForTheTaking / missing);
        }
    }

    /**
     * What is missing for the things worth having, and how much of each thing's
     * worth the missing material carries.
     *
     * <p>Two sources, because there are two reasons to want a material: a
     * thing the agent means to make, and a fire it means to sit at. Both are
     * one step removed from a need and both are valued the same way - a share
     * of what the finished thing would be worth.
     */
    private static Map<ResourceType, Want> whatIsStillNeeded(Perception perception) {
        Map<ResourceType, Want> wanted = new LinkedHashMap<>();
        firewood(perception).ifPresent(want -> wanted.put(ResourceType.WOOD, want));
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
                Want want = new Want(worth * (missing / recipe.totalInput()), missing);
                wanted.merge(type, want, (first, other) ->
                        first.worth() >= other.worth() ? first : other);
            });
        }
        return wanted;
    }

    /**
     * What wood is worth to a cold agent with no fire to put it on.
     *
     * <p>Only while it is actually cold: wood fetched against a chill that has
     * not arrived is a plan, and this layer does not make plans. In practice
     * the evening does the work - the cold comes on before the agent wants to
     * sleep, and there is wood about.
     */
    private static Optional<Want> firewood(Perception perception) {
        double missing = StayProvisionedGoal.FIREWOOD
                - perception.inventory().quantityOf(ResourceType.WOOD);
        if (missing <= 0.0) {
            return Optional.empty();
        }
        // Worth the share of a night's firewood it would make up. Wanted in
        // daylight as much as in the cold: wood gathered only once the agent
        // was already shivering was gathered in the dark and burnt at once.
        return Optional.of(
                new Want(StayProvisionedGoal.firewoodShortfall(perception), missing));
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

    /** Which want this errand serves, which is what the evaluator weighs it against. */
    private static String goalFor(ResourceType type, boolean forWarmth) {
        return forWarmth && type == ResourceType.WOOD
                ? KeepWarmGoal.NAME
                : StayProvisionedGoal.NAME;
    }

    private ActionCandidate pickUp(
            PerceivedResource here, double worth, double sureness, String goal) {
        return new ActionCandidate(
                new GatherAction(here.type(), gatherAmount, gatherEnergyCost, gatherTicks),
                goal,
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

    private ActionCandidate goTo(
            PerceivedResource seen, double worth, double sureness, String goal) {
        int steps = seen.stepsAway();
        return ActionCandidate.heading(
                seen.location(),
                new MoveAction(seen.location(), moveEnergyCost, stepsPerTick),
                goal,
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
