package dev.evoworld.core;

import java.time.Duration;

/**
 * How much an action does, what it costs, and how long it takes.
 *
 * <p>Split out of {@link SimulationConfig} because these are the numbers that
 * decide what behaviour looks like: how far a meal goes, how much a trip costs,
 * how long a bush takes to strip. Times are in simulated time, so a walk or a
 * gather takes as long at one tick length as at another; they are turned into
 * ticks here.
 *
 * <p>How the body recovers - resting, sleeping - is not here. That is the
 * metabolism's business ({@link dev.evoworld.core.agent.MetabolismRates}),
 * because it happens tick by tick for as long as the agent is at rest.
 *
 * @param moveEnergyCost      energy spent per step walked
 * @param walkingStepsPerHour how many tiles an agent walks in an hour
 * @param gatherAmount        how much a single gather takes from a node
 * @param gatherEnergyCost    energy spent gathering
 * @param gatherMinutes       how long a gather takes
 * @param eatPortion          how many units of food one meal uses: a single bite's worth
 * @param drinkGulp           how many units of water one drink uses
 */
public record ActionSettings(
        double moveEnergyCost,
        double walkingStepsPerHour,
        double gatherAmount,
        double gatherEnergyCost,
        long gatherMinutes,
        double eatPortion,
        double drinkGulp) {

    public ActionSettings {
        if (walkingStepsPerHour <= 0.0) {
            throw new IllegalArgumentException("an agent must be able to walk, was " + walkingStepsPerHour);
        }
        if (gatherMinutes < 1) {
            throw new IllegalArgumentException("a gather takes some time, was " + gatherMinutes);
        }
    }

    /**
     * A person on foot: six tiles an hour - a tile is ten minutes' walk - who
     * eats and drinks a unit at a time rather than a feast at a gulp.
     *
     * <p>A meal and a drink stay one unit whatever the tick length. Scaling
     * them with time made a single drink at hour-long ticks six times what
     * any thirst needed, and a reading that always runs out of thirst teaches
     * nothing (ADR 26) - so an agent never learned what water was. At long
     * ticks a meal simply takes a whole tick.
     */
    public static ActionSettings defaults() {
        return new ActionSettings(0.3, 6.0, 10.0, 0.5, 20L, 1.0, 1.0);
    }

    /** How many tiles fit in one tick of {@code tick}; never fewer than one. */
    public int stepsPerTick(Duration tick) {
        return Math.max(1, (int) Math.round(Rates.perTick(walkingStepsPerHour, tick)));
    }

    /** How many ticks of {@code tick} a gather occupies; never fewer than one. */
    public int gatherTicks(Duration tick) {
        long seconds = Math.max(1L, tick.getSeconds());
        return (int) Math.max(1L, (gatherMinutes * 60L + seconds - 1) / seconds);
    }
}
