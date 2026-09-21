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
 * Offers a breather: a little energy back, and faster mending when hurt.
 *
 * <p>Not how an agent gets its energy back - that is a night's sleep
 * ({@link SleepProposer}). A rest is what is left when nothing else is worth a
 * tick, or when the agent is hurt and sitting still would help it heal.
 *
 * <p>Two candidates for the same action, one per goal it serves, each weighed
 * against how badly the agent wants that goal. Rest for health is only offered
 * when resting would heal: a starving agent heals nothing by sitting down.
 */
public final class RestProposer implements ActionProposer {

    private final double energyPerTick;
    private final double extraHealingPerTick;

    /** @param metabolism one tick's worth of the body's rates */
    public RestProposer(MetabolismRates metabolism) {
        Objects.requireNonNull(metabolism, "metabolism");
        this.energyPerTick = metabolism.energyChangeFor(ActivityLevel.RESTING);
        this.extraHealingPerTick = metabolism.healingFor(ActivityLevel.RESTING)
                - metabolism.healingFor(ActivityLevel.ACTIVE);
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Needs needs = perception.needs();
        List<ActionCandidate> candidates = new ArrayList<>(2);
        if (needs.energy() < Needs.MAX && energyPerTick > 0.0) {
            candidates.add(ActionCandidate.here(
                    new RestAction(),
                    RecoverEnergyGoal.NAME,
                    ExpectedRelief.fromResting(needs, energyPerTick),
                    0.0,
                    String.format(Locale.ROOT, "energy at %.1f", needs.energy())));
        }
        if (!needs.isFullyHealthy() && needs.isWellEnoughToHeal() && extraHealingPerTick > 0.0) {
            candidates.add(ActionCandidate.here(
                    new RestAction(),
                    RecoverHealthGoal.NAME,
                    ExpectedRelief.fromHealing(needs, extraHealingPerTick),
                    0.0,
                    String.format(Locale.ROOT, "health at %.1f", needs.health())));
        }
        return List.copyOf(candidates);
    }
}
