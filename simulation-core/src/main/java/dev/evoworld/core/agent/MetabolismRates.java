package dev.evoworld.core.agent;

/**
 * How fast an agent's body changes per simulation tick.
 *
 * <p>Energy has two drain rates because resting has to be worth doing: an agent
 * that burned the same energy asleep as on the move could never come out ahead
 * by stopping.
 *
 * @param hungerPerTick       hunger pressure gained each tick
 * @param thirstPerTick       thirst pressure gained each tick
 * @param activeEnergyPerTick energy spent each tick while doing something
 * @param restingEnergyPerTick energy spent each tick while resting
 * @param starvationDamage    health lost per tick while hunger is critical
 * @param dehydrationDamage   health lost per tick while thirst is critical
 * @param exhaustionDamage    health lost per tick while energy is depleted
 */
public record MetabolismRates(
        double hungerPerTick,
        double thirstPerTick,
        double activeEnergyPerTick,
        double restingEnergyPerTick,
        double starvationDamage,
        double dehydrationDamage,
        double exhaustionDamage) {

    public MetabolismRates {
        if (restingEnergyPerTick > activeEnergyPerTick) {
            throw new IllegalArgumentException(
                    "resting must not cost more energy than being active");
        }
    }

    public static MetabolismRates defaults() {
        return new MetabolismRates(0.8, 1.2, 0.5, 0.1, 1.5, 2.0, 1.0);
    }

    /** What one tick of the given activity costs in energy. */
    public double energyDrainFor(ActivityLevel activity) {
        return switch (activity) {
            case ACTIVE -> activeEnergyPerTick;
            case RESTING -> restingEnergyPerTick;
        };
    }
}
