package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.RecoverEnergyGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.List;
import java.util.Locale;

/** Offers to sit still, which is always possible unless already fully rested. */
public final class RestProposer implements ActionProposer {

    private final double energyGain;

    public RestProposer(double energyGain) {
        this.energyGain = energyGain;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        if (perception.needs().energy() >= Needs.MAX) {
            return List.of();
        }
        return List.of(ActionCandidate.here(
                new RestAction(energyGain),
                RecoverEnergyGoal.NAME,
                ExpectedRelief.fromResting(perception.needs(), energyGain),
                0.0,
                String.format(Locale.ROOT, "energy at %.1f", perception.needs().energy())));
    }
}
