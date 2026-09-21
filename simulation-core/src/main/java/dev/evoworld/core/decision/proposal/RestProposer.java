package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.RecoverEnergyGoal;
import dev.evoworld.core.goal.RecoverHealthGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Offers to sit still: for energy when tired, and for health when hurt.
 *
 * <p>Two candidates for the same action, one per goal it serves, so each is
 * weighed against how badly the agent wants that goal. Rest for health is only
 * offered when resting would heal - a starving agent heals nothing by sitting
 * down, and its time is better spent finding food.
 */
public final class RestProposer implements ActionProposer {

    private final double energyGain;
    private final double extraHealing;

    public RestProposer(double energyGain, MetabolismRates metabolism) {
        Objects.requireNonNull(metabolism, "metabolism");
        this.energyGain = energyGain;
        this.extraHealing = metabolism.healingFor(ActivityLevel.RESTING)
                - metabolism.healingFor(ActivityLevel.ACTIVE);
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Needs needs = perception.needs();
        List<ActionCandidate> candidates = new ArrayList<>(2);
        if (needs.energy() < Needs.MAX) {
            candidates.add(ActionCandidate.here(
                    new RestAction(energyGain),
                    RecoverEnergyGoal.NAME,
                    ExpectedRelief.fromResting(needs, energyGain),
                    0.0,
                    String.format(Locale.ROOT, "energy at %.1f", needs.energy())));
        }
        if (!needs.isFullyHealthy() && needs.isWellEnoughToHeal() && extraHealing > 0.0) {
            candidates.add(ActionCandidate.here(
                    new RestAction(energyGain),
                    RecoverHealthGoal.NAME,
                    ExpectedRelief.fromHealing(needs, extraHealing),
                    0.0,
                    String.format(Locale.ROOT, "health at %.1f", needs.health())));
        }
        return List.copyOf(candidates);
    }
}
