package dev.evoworld.core.save;

/** How fast bodies change, per simulated hour, as saved. */
public record SavedMetabolism(
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
}
