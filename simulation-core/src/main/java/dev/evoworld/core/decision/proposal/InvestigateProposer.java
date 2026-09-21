package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.ExploreGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Offers to try something the agent has never tried, for the sake of finding out.
 *
 * <p>The other proposers offer food because the agent is hungry. This one
 * offers it because the agent does not know what it is - an experiment rather
 * than a meal, serving {@link ExploreGoal} rather than a bodily need. That is
 * why a comfortable agent investigates and a desperate one does not: curiosity
 * only wins when nothing is pressing, which is exactly when it should.
 *
 * <p>The reward is what there is to find out, {@code 1 - confidence}, so a
 * complete unknown is worth the most and something already tested is worth
 * nothing at all. The risk is the ordinary one the evaluator already charges:
 * an experiment costs a tick and possibly a mouthful of something useless, and
 * an agent whose needs are about to start doing damage will not spend either.
 * Nothing special-cases experiments as risky - they simply lose when survival
 * has a claim on the same tick.
 */
public final class InvestigateProposer implements ActionProposer {

    private final double sample;

    public InvestigateProposer(double sample) {
        this.sample = sample;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Knowledge knowledge = perception.knowledge();
        List<ActionCandidate> candidates = new ArrayList<>();

        Optional<PerceivedResource> underfoot =
                perception.underfoot().filter(PerceivedResource::isAvailable);
        // An experiment has to be able to show something. Drinking when not
        // thirsty relieves nothing, so it would read as "worth nothing" and
        // teach the agent something false.
        boolean canReadThirst = perception.needs().thirst() > Needs.MIN;
        boolean canReadHunger = perception.needs().hunger() > Needs.MIN;

        underfoot.ifPresent(here -> {
            if (canReadThirst && !knowledge.hasTested(BeliefKind.HYDRATION, here.type())) {
                candidates.add(experiment(
                        new DrinkAction(here.type(), Math.min(sample, here.quantity())),
                        knowledge.confidence(BeliefKind.HYDRATION, here.type()),
                        "never tried drinking " + here.type().label()));
            }
        });

        for (ResourceType type : ResourceType.values()) {
            double carried = perception.inventory().quantityOf(type);
            if (!canReadHunger
                    || carried <= 0.0
                    || knowledge.hasTested(BeliefKind.NOURISHMENT, type)) {
                continue;
            }
            candidates.add(experiment(
                    new EatAction(type, Math.min(sample, carried)),
                    knowledge.confidence(BeliefKind.NOURISHMENT, type),
                    "never tried eating " + type.label()));
        }
        return List.copyOf(candidates);
    }

    private ActionCandidate experiment(
            dev.evoworld.core.action.Action action, double confidence, String why) {
        return ActionCandidate.here(
                action,
                ExploreGoal.NAME,
                // Worth exactly what there is left to find out.
                1.0 - confidence,
                0.0,
                why);
    }
}
