package dev.evoworld.core.save;

/** What actions do and cost, as saved. */
public record SavedActionSettings(
        double moveEnergyCost,
        double restEnergyGain,
        double gatherAmount,
        double gatherEnergyCost,
        double eatPortion,
        double drinkGulp) {
}
