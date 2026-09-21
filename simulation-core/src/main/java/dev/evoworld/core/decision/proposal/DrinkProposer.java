package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
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
        Optional<PerceivedResource> here =
                perception.underfoot().filter(PerceivedResource::isAvailable);
        if (here.isEmpty() || perception.needs().thirst() < Needs.NOTICEABLE) {
            return List.of();
        }

        PerceivedResource source = here.get();
        if (!ExpectedRelief.thinksItIsADrink(perception.knowledge(), source.type())) {
            return List.of();
        }

        // Valued at what the spring can do for this thirst, not at one gulp:
        // the same measure as walking to water somewhere else, so standing in a
        // spring never looks worse than a trip to one. The gulp is what one
        // tick drinks; the next tick weighs whatever thirst is left.
        return List.of(new ActionCandidate(
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
    }

    private double believedConfidence(Perception perception, PerceivedResource source) {
        return Math.max(
                perception.knowledge().confidence(BeliefKind.HYDRATION, source.type()),
                UntestedGuess.CONFIDENCE);
    }
}
