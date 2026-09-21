package dev.evoworld.core.resource;

/**
 * What a resource is, and how it behaves over time.
 *
 * <p>The numbers live on the type because they are what the type <em>means</em>:
 * a berry bush that held a thousand berries and regrew instantly would not be a
 * berry bush. They move into configuration only if a run ever needs to tune
 * them, which nothing does yet.
 *
 * <p>Regeneration is what separates a resource you can live off from one you
 * can only use up. Berries regrow within a day, wood takes the better part of a
 * week, water replenishes almost immediately, and stone never comes back.
 *
 * <p>Nourishment and hydration are what one unit does for a body. A resource
 * with neither is a material rather than a meal, which is how an action decides
 * whether it is edible without a list of special cases.
 */
public enum ResourceType {

    //       label      capacity  regen  nourishment  hydration
    BERRIES("berries",      12.0,  0.25,         8.0,       0.0),
    WATER("water",         100.0,  5.00,         0.0,      12.0),
    WOOD("wood",            20.0,  0.05,         0.0,       0.0),
    STONE("stone",          30.0,  0.00,         0.0,       0.0);

    private final String label;
    private final double capacity;
    private final double regenPerTick;
    private final double nourishmentPerUnit;
    private final double hydrationPerUnit;

    ResourceType(
            String label,
            double capacity,
            double regenPerTick,
            double nourishmentPerUnit,
            double hydrationPerUnit) {
        this.label = label;
        this.capacity = capacity;
        this.regenPerTick = regenPerTick;
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

    /** How much a node recovers per tick; zero for types that never regrow. */
    public double regenPerTick() {
        return regenPerTick;
    }

    /** Whether nodes of this type come back after being harvested. */
    public boolean isRenewable() {
        return regenPerTick > 0.0;
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

    /** How many ticks an empty node needs to reach full again, empty if never. */
    public java.util.OptionalLong ticksToFullFromEmpty() {
        if (!isRenewable()) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of((long) Math.ceil(capacity / regenPerTick));
    }

    @Override
    public String toString() {
        return label;
    }
}
