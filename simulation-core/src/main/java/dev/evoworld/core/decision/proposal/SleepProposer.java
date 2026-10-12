package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.SleepAction;
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
 * Offers a night's sleep, once the agent is tired enough to need one.
 *
 * <p>One sleep, sized when it is offered: long enough to bring energy back to
 * full at the body's sleeping rate, but no longer than a night, and never so
 * long that hunger or thirst would cross into doing damage before the agent
 * woke. An agent that is parched drinks first; it does not lie down and wake
 * up hurt.
 *
 * <p>Sleep serves energy, and health too when the agent is hurt and well
 * enough to mend - sleep is where a body heals fastest. A badly hurt agent is
 * offered sleep for its health even when it is not tired.
 */
public final class SleepProposer implements ActionProposer {

    /**
     * Below this energy an agent is tired enough to sleep; above it, it cannot.
     * Low on purpose: a person stays up until the day is spent, then sleeps
     * once, rather than napping whenever a little tired.
     */
    public static final double SLEEPY = 25.0;

    /**
     * At or below this health an agent is hurt enough to lie down whatever
     * its energy: bed rest. Healing is slow, and only sleep makes it much
     * faster, so a badly hurt agent that could only sleep when tired would
     * walk its wounds around all day.
     */
    public static final double BADLY_HURT = 50.0;

    private final MetabolismRates metabolism;
    private final int longestSleep;

    /**
     * @param metabolism one tick's worth of the body's rates
     * @param longestSleep the most ticks one sleep may last
     */
    public SleepProposer(MetabolismRates metabolism, int longestSleep) {
        this.metabolism = Objects.requireNonNull(metabolism, "metabolism");
        if (longestSleep < 1) {
            throw new IllegalArgumentException("a sleep lasts at least one tick, was " + longestSleep);
        }
        this.longestSleep = longestSleep;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        Needs needs = perception.needs();
        boolean tired = needs.energy() <= SLEEPY;
        boolean bedRest = needs.health() <= BADLY_HURT && willMend(needs);
        if (!tired && !bedRest) {
            return List.of();
        }
        // How good a night this would be: warm or cold, dark or broad daylight,
        // beside a fire or not. A poor night restores less per hour, so it is
        // worth less and takes longer - which is why an agent with a choice
        // sleeps in the dark, and by a fire when it has one.
        double comfort = perception.conditions().comfort();
        double gainPerTick = metabolism.energyChangeFor(ActivityLevel.SLEEPING) * comfort;
        int wanted = tired ? ticksToFull(needs.energy(), gainPerTick) : longestSleep;
        int ticks = Math.min(wanted, ticksBeforeHarm(needs));
        if (ticks < 1) {
            return List.of();
        }
        SleepAction sleep = new SleepAction(ticks, comfort);

        List<ActionCandidate> candidates = new ArrayList<>(2);
        if (tired) {
            candidates.add(ActionCandidate.here(
                    sleep,
                    RecoverEnergyGoal.NAME,
                    Math.min(gainPerTick * ticks, Needs.MAX - needs.energy()) / Needs.MAX,
                    0.0,
                    String.format(Locale.ROOT, "energy at %.1f, %d ticks' sleep", needs.energy(), ticks)));
        }

        double extraHealing = metabolism.healingFor(ActivityLevel.SLEEPING) * comfort
                - metabolism.healingFor(ActivityLevel.ACTIVE);
        if (!needs.isFullyHealthy() && willMend(needs) && extraHealing > 0.0) {
            candidates.add(ActionCandidate.here(
                    sleep,
                    RecoverHealthGoal.NAME,
                    Math.min(extraHealing * ticks, Needs.MAX - needs.health()) / Needs.MAX,
                    0.0,
                    String.format(Locale.ROOT, "health at %.1f", needs.health())));
        }
        return List.copyOf(candidates);
    }

    /**
     * Whether a sleep would let this body mend. Only hunger and thirst can
     * stop it: sleep is what brings energy back, so a tired body that goes to
     * sleep is soon rested enough to heal.
     */
    private static boolean willMend(Needs needs) {
        return needs.isAlive()
                && needs.hunger() <= Needs.WELL_PRESSURE
                && needs.thirst() <= Needs.WELL_PRESSURE;
    }

    private int ticksToFull(double energy, double gainPerTick) {
        if (gainPerTick <= 0.0) {
            return 0;
        }
        return Math.min(longestSleep, (int) Math.ceil((Needs.MAX - energy) / gainPerTick));
    }

    /**
     * How many ticks asleep before hunger or thirst would start doing damage.
     * A need that is already doing damage sets no limit: staying awake would
     * not help it, and exhaustion would only add to the harm.
     */
    private int ticksBeforeHarm(Needs needs) {
        return Math.min(
                ticksBelow(needs.hunger(), metabolism.hunger()),
                ticksBelow(needs.thirst(), metabolism.thirst()));
    }

    private static int ticksBelow(double pressure, double risePerTick) {
        if (risePerTick <= 0.0) {
            return Integer.MAX_VALUE;
        }
        double room = Needs.CRITICAL_PRESSURE - pressure;
        return room <= 0.0 ? Integer.MAX_VALUE : (int) Math.floor(room / risePerTick) - 1;
    }
}
