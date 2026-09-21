package dev.evoworld.core.save;

/** What actions do, cost and take, as saved. */
public record SavedActionSettings(
        double moveEnergyCost,
        double walkingStepsPerHour,
        double gatherAmount,
        double gatherEnergyCost,
        long gatherMinutes,
        double eatPortion,
        double drinkGulp) {
}
