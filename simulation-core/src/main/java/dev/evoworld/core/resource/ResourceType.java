package dev.evoworld.core.resource;

import dev.evoworld.core.Rates;
import java.time.Duration;

/**
 * What a resource is, and how it behaves over time.
 *
 * <p>The numbers live on the type because they are what the type <em>means</em>:
 * a berry bush that held a thousand berries and regrew instantly would not be a
 * berry bush. They move into configuration only if a run ever needs to tune
 * them, which nothing does yet.
 *
 * <p>Regeneration is what separates a resource you can live off from one you
 * can only use up. It is stated per simulated hour: berries regrow within a
 * day, wood takes the better part of a week, water replenishes almost
 * immediately, and stone never comes back.
 *
 * <p>Nourishment and hydration are what one unit does for a body. A resource
 * with neither is a material rather than a meal, which is how an action decides
 * whether it is edible without a list of special cases.
 */
public enum ResourceType {

    //       label      capacity  regen/h  nourishment  hydration
    BERRIES("berries",      12.0,    1.5,          8.0,       0.0),
    WATER("water",         100.0,   30.0,          0.0,      12.0),
    WOOD("wood",            20.0,    0.3,          0.0,       0.0),
    STONE("stone",          30.0,    0.0,          0.0,       0.0);

    private final String label;
    private final double capacity;
    private final double regenPerHour;
    private final double nourishmentPerUnit;
    private final double hydrationPerUnit;

    ResourceType(
            String label,
            double capacity,
            double regenPerHour,
            double nourishmentPerUnit,
            double hydrationPerUnit) {
        this.label = label;
        this.capacity = capacity;
        this.regenPerHour = regenPerHour;
        this.nourishmentPerUnit = nourishmentPerUnit;
        this.hydrationPerUnit = hydrationPerUnit;
    }

    public String label() {
        return label;
    }

    /** How much a full node of this type holds. */
    public double capacity() {
        return capacity;
    }

    /** How much a node recovers per simulated hour; zero for types that never regrow. */
    public double regenPerHour() {
        return regenPerHour;
    }

    /** How much a node recovers in one tick of {@code tick}. */
    public double regenPerTick(Duration tick) {
        return Rates.perTick(regenPerHour, tick);
    }

    /**
     * Whether an agent needs something to carry this in.
     *
     * <p>True of anything that will not stay in your hands. Berries go in a
     * pocket; water does not, which is why taking water away from a spring
     * needs a skin and why, before there were skins, thirst meant a walk.
     */
    public boolean needsContainer() {
        return this == WATER;
    }

    /** Whether nodes of this type come back after being harvested. */
    public boolean isRenewable() {
        return regenPerHour > 0.0;
    }

    /** How much hunger one unit relieves. */
    public double nourishmentPerUnit() {
        return nourishmentPerUnit;
    }

    /** How much thirst one unit relieves. */
    public double hydrationPerUnit() {
        return hydrationPerUnit;
    }

    public boolean isEdible() {
        return nourishmentPerUnit > 0.0;
    }

    public boolean isDrinkable() {
        return hydrationPerUnit > 0.0;
    }

    /** How many ticks of {@code tick} an empty node needs to reach full again; empty if never. */
    public java.util.OptionalLong ticksToFullFromEmpty(Duration tick) {
        if (!isRenewable()) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of((long) Math.ceil(capacity / regenPerTick(tick)));
    }

    @Override
    public String toString() {
        return label;
    }
}
