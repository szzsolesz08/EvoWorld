package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Offers a drink from whatever the agent is standing on and believes drinkable.
 *
 * <p>An agent that has never tried anything believes weakly that everything
 * might be - so it will try a stone outcrop once, be told stone is not a drink,
 * and never make that mistake again.
 */
public final class DrinkProposer implements ActionProposer {

    private final double gulp;

    public DrinkProposer(double gulp) {
        this.gulp = gulp;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        if (perception.needs().thirst() < Needs.NOTICEABLE) {
            return List.of();
        }

        List<ActionCandidate> candidates = new ArrayList<>(carried(perception));
        Optional<PerceivedResource> here =
                perception.underfoot().filter(PerceivedResource::isAvailable);
        if (here.isEmpty()) {
            return List.copyOf(candidates);
        }

        PerceivedResource source = here.get();
        if (!ExpectedRelief.thinksItIsADrink(perception.knowledge(), source.type())) {
            return List.copyOf(candidates);
        }

        // Valued at what the spring can do for this thirst, not at one gulp:
        // the same measure as walking to water somewhere else, so standing in a
        // spring never looks worse than a trip to one. The gulp is what one
        // tick drinks; the next tick weighs whatever thirst is left.
        candidates.add(new ActionCandidate(
                new DrinkAction(source.type(), gulp),
                SatisfyThirstGoal.NAME,
                ExpectedRelief.fromDrinking(
                        perception.knowledge(), perception.needs(), source.type(),
                        UntestedGuess.unitsExpected(
                                perception.knowledge(), BeliefKind.HYDRATION, source.type(), source.quantity())),
                // Sure the spring is here; only as sure as experience allows
                // that drinking from it will help.
                believedConfidence(perception, source),
                0,
                0.0,
                String.format(Locale.ROOT, "%s underfoot", source.type().label())));
        return List.copyOf(candidates);
    }

    /**
     * A drink from what the agent carries.
     *
     * <p>The mirror of eating from the pack, and valued on the same measure as
     * a spring underfoot, so the evaluator can weigh "drink what I have"
     * against "walk to water" without either being a special case. Carried
     * water only exists at all if the agent has something to keep it in.
     */
    private List<ActionCandidate> carried(Perception perception) {
        List<ActionCandidate> candidates = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            double held = perception.inventory().quantityOf(type);
            double mouthful = Math.min(gulp, held);
            if (mouthful <= 0.0
                    || !ExpectedRelief.thinksItIsADrink(perception.knowledge(), type)) {
                continue;
            }
            candidates.add(new ActionCandidate(
                    DrinkAction.fromPack(type, mouthful),
                    SatisfyThirstGoal.NAME,
                    ExpectedRelief.fromDrinking(
                            perception.knowledge(), perception.needs(), type,
                            UntestedGuess.unitsExpected(
                                    perception.knowledge(), BeliefKind.HYDRATION, type, held)),
                    Math.max(
                            perception.knowledge().confidence(BeliefKind.HYDRATION, type),
                            UntestedGuess.CONFIDENCE),
                    0,
                    0.0,
                    String.format(Locale.ROOT, "carrying %.1f %s", held, type.label())));
        }
        return candidates;
    }

    private double believedConfidence(Perception perception, PerceivedResource source) {
        return Math.max(
                perception.knowledge().confidence(BeliefKind.HYDRATION, source.type()),
                UntestedGuess.CONFIDENCE);
    }
}
