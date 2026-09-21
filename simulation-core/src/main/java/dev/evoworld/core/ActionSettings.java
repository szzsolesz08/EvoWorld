package dev.evoworld.core;

/**
 * How much an action does, and what it costs.
 *
 * <p>Split out of {@link SimulationConfig} because these are the numbers that
 * decide what behaviour looks like: how far a meal goes, how much a trip costs,
 * how long a bush takes to strip. Tuning an agent means tuning these.
 *
 * @param moveEnergyCost   energy spent per grid step
 * @param restEnergyGain   energy recovered per tick of rest
 * @param gatherAmount     how much a single gather takes from a node
 * @param gatherEnergyCost energy spent gathering
 * @param eatPortion       how many units of food one meal uses
 * @param drinkGulp        how many units of water one drink uses
 */
public record ActionSettings(
        double moveEnergyCost,
        double restEnergyGain,
        double gatherAmount,
        double gatherEnergyCost,
        double eatPortion,
        double drinkGulp) {

    public static ActionSettings defaults() {
        return new ActionSettings(1.5, 6.0, 10.0, 1.0, 4.0, 4.0);
    }
}
