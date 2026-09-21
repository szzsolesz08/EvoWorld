package dev.evoworld.core.save;

/** How fast bodies change, as saved. */
public record SavedMetabolism(
        double hungerPerTick,
        double thirstPerTick,
        double activeEnergyPerTick,
        double restingEnergyPerTick,
        double starvationDamage,
        double dehydrationDamage,
        double exhaustionDamage) {
}
