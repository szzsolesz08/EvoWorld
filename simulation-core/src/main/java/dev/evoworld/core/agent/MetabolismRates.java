package dev.evoworld.core.agent;

import dev.evoworld.core.Rates;
import java.time.Duration;

/**
 * How fast an agent's body changes.
 *
 * <p>A configuration states these <strong>per simulated hour</strong>, so they
 * mean the same thing at any tick length: {@link #defaults()} is a human day,
 * where thirst hurts after about two days and hunger after about a week. The
 * simulation converts them once with {@link #perTick}, and everything that
 * runs a tick - the body, and the decision layer's projections - is handed
 * that converted copy.
 *
 * <p>Energy changes by activity, and the sign says which way: it drains while
 * active, drains more slowly while resting, and only comes back while
 * sleeping - as for a person, sitting down all day is no substitute for a
 * night's sleep. Healing
 * also depends on activity, and only happens at all when the body is well
 * looked after (see {@link Needs#isWellEnoughToHeal()}).
 *
 * @param hunger          hunger pressure gained
 * @param thirst          thirst pressure gained
 * @param activeEnergy    energy change while doing something; negative
 * @param restingEnergy   energy change while resting
 * @param sleepingEnergy  energy change while asleep
 * @param starvation      health lost while hunger is critical
 * @param dehydration     health lost while thirst is critical
 * @param exhaustion      health lost while energy is depleted
 * @param activeHealing   health regained while active, when well
 * @param restingHealing  health regained while resting, when well
 * @param sleepingHealing health regained while asleep, when well
 */
public record MetabolismRates(
        double hunger,
        double thirst,
        double activeEnergy,
        double restingEnergy,
        double sleepingEnergy,
        double starvation,
        double dehydration,
        double exhaustion,
        double activeHealing,
        double restingHealing,
        double sleepingHealing) {

    public MetabolismRates {
        for (double rate : new double[] {hunger, thirst, starvation, dehydration, exhaustion, activeHealing}) {
            if (rate < 0.0) {
                throw new IllegalArgumentException("rates must not be negative, was " + rate);
            }
        }
        if (activeEnergy > restingEnergy || restingEnergy > sleepingEnergy) {
            throw new IllegalArgumentException(
                    "resting must not cost more energy than being active, nor sleeping more than resting");
        }
        if (activeHealing > restingHealing || restingHealing > sleepingHealing) {
            throw new IllegalArgumentException(
                    "resting must heal at least as much as being active, and sleeping at least as much as resting");
        }
    }

    /** A human body, per simulated hour. */
    public static MetabolismRates defaults() {
        return new MetabolismRates(
                0.7,     // hunger: critical after about five and a half days
                2.0,     // thirst: critical after about two days
                -5.0,    // awake and busy: about fifteen hours from full to sleepy
                -1.5,    // sitting still slows the drain; only sleep gives energy back
                10.0,    // a night's sleep: about seven and a half hours from sleepy to full
                0.6,     // starving: about a week from critical to dead
                4.0,     // parched: about a day
                2.0,     // spent: about two days
                0.25,    // mending on the go
                1.0,     // mending at rest
                2.0);    // mending asleep
    }

    /** These rates, taken as per hour, as what one tick of {@code tick} does. */
    public MetabolismRates perTick(Duration tick) {
        return new MetabolismRates(
                Rates.perTick(hunger, tick),
                Rates.perTick(thirst, tick),
                Rates.perTick(activeEnergy, tick),
                Rates.perTick(restingEnergy, tick),
                Rates.perTick(sleepingEnergy, tick),
                Rates.perTick(starvation, tick),
                Rates.perTick(dehydration, tick),
                Rates.perTick(exhaustion, tick),
                Rates.perTick(activeHealing, tick),
                Rates.perTick(restingHealing, tick),
                Rates.perTick(sleepingHealing, tick));
    }

    /** How energy changes at this level of activity, before any comfort. */
    public double energyChangeFor(ActivityLevel activity) {
        return switch (activity) {
            case ACTIVE -> activeEnergy;
            case RESTING -> restingEnergy;
            case SLEEPING -> sleepingEnergy;
        };
    }

    /** What a body well enough to heal regains at this level of activity, before any comfort. */
    public double healingFor(ActivityLevel activity) {
        return switch (activity) {
            case ACTIVE -> activeHealing;
            case RESTING -> restingHealing;
            case SLEEPING -> sleepingHealing;
        };
    }
}
