package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Offers to pick up what the agent is standing on and believes worth carrying.
 *
 * <p>How much it takes scales with how sure it is. An agent loads up on what it
 * knows is food and takes a single sample of what it does not, which keeps a
 * hunch from filling a bag that then has no room for a meal. Caution is a
 * consequence of the confidence number, not a rule about unknown things.
 */
public final class GatherProposer implements ActionProposer {

    /** Always worth taking at least this much, or an experiment is impossible. */
    private static final double SAMPLE = 1.0;

    private final double amount;
    private final double energyCost;

    public GatherProposer(double amount, double energyCost) {
        this.amount = amount;
        this.energyCost = energyCost;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Optional<PerceivedResource> here =
                perception.underfoot().filter(PerceivedResource::isAvailable);
        if (here.isEmpty() || perception.inventory().isFull()) {
            return List.of();
        }

        PerceivedResource node = here.get();
        if (!ExpectedRelief.thinksItIsFood(perception.knowledge(), node.type())) {
            return List.of();
        }

        double sureness = Math.max(
                perception.knowledge().confidence(BeliefKind.NOURISHMENT, node.type()),
                UntestedGuess.CONFIDENCE);
        // Half a haul of something untried, a full one of something proven. A
        // single-unit sample would be scored as worth a single unit and never
        // chosen; what a sample leaves behind if it turns out to be junk is
        // DiscardProposer's problem.
        double haul = Math.max(SAMPLE, amount * sureness);
        double wouldGet = Math.min(
                Math.min(haul, node.quantity()), perception.inventory().spaceFor(haul));
        if (wouldGet <= 0.0) {
            return List.of();
        }

        return List.of(new ActionCandidate(
                new GatherAction(node.type(), haul, energyCost),
                SatisfyHungerGoal.NAME,
                ExpectedRelief.fromEating(
                        perception.knowledge(), perception.needs(), node.type(), wouldGet),
                sureness,
                0,
                energyCost,
                String.format(
                        Locale.ROOT, "%.1f %s underfoot", node.quantity(), node.type().label())));
    }
}
