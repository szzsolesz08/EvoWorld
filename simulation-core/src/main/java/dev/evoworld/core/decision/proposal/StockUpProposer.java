package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.StayProvisionedGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Offers to fill up while the filling is easy.
 *
 * <p>This is the only proposer that offers something the agent does not want
 * yet. Everything else answers a need that is already being felt; this answers
 * {@link StayProvisionedGoal}, which is the want to have a supply before the
 * need arrives. Standing in a spring with a skin on your back, a mouthful is
 * worth what your thirst is worth - and filling the skin is worth the walk it
 * saves tomorrow.
 *
 * <p>It offers nothing an agent could not already do: a gather, of a thing it
 * believes is worth having, into a container it happens to own. Take the
 * container away and there is no room, so there is no offer; that is why an
 * agent without a waterskin cannot stock up, rather than a rule saying so.
 */
public final class StockUpProposer implements ActionProposer {

    /** The least it is worth stopping to collect. */
    private static final double WORTH_STOPPING_FOR = 1.0;

    private final double amount;
    private final double energyCost;
    private final int ticks;

    public StockUpProposer(double amount, double energyCost, int ticks) {
        this.amount = amount;
        this.energyCost = energyCost;
        this.ticks = ticks;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Optional<PerceivedResource> here =
                perception.underfoot().filter(PerceivedResource::isAvailable);
        if (here.isEmpty()) {
            return List.of();
        }

        ResourceType type = here.get().type();
        double room = perception.belongings().roomFor(type);
        if (room <= 0.0 || !ExpectedRelief.thinksItIsADrink(perception.knowledge(), type)) {
            return List.of();
        }
        double carried = perception.inventory().quantityOf(type);
        double couldTake = Math.min(
                Math.min(amount, room - carried),
                Math.min(here.get().quantity(), perception.inventory().freeSpace()));
        // Not worth unstopping the flask for a mouthful: topping up a nearly
        // full one wears it out for nothing, which in a world where water is
        // always underfoot is a flask a week.
        if (couldTake < WORTH_STOPPING_FOR) {
            return List.of();
        }

        return List.of(new ActionCandidate(
                new GatherAction(type, couldTake, energyCost, ticks),
                StayProvisionedGoal.NAME,
                // How much of the shortfall this would close: a skin filled to
                // the brim answers the want completely, a splash hardly at all.
                Math.min(1.0, couldTake / room),
                // As sure as it is that the stuff is worth carrying, which for
                // something it has never tasted is only a hunch.
                Math.max(
                        perception.knowledge().confidence(BeliefKind.HYDRATION, type),
                        UntestedGuess.CONFIDENCE),
                0,
                energyCost,
                String.format(
                        Locale.ROOT,
                        "filling up: %.1f %s of %.1f",
                        carried + couldTake,
                        type.label(),
                        room)));
    }
}
