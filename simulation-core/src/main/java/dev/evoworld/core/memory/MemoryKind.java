package dev.evoworld.core.memory;

/**
 * What a memory is about, and how long it stays worth believing.
 *
 * <p>The half-life is the point of the enum. Some things stay true: a spring
 * does not wander off, so where it is keeps almost all its confidence for
 * thousands of ticks. Some things go stale immediately: how full that spring
 * was is worth much less by tomorrow. Separating the place from its state is
 * what lets an agent be sure where the water is and unsure whether there is any
 * left.
 *
 * @param description what this kind of memory records, for a human reading it
 * @param halfLifeTicks how long until confidence in it halves
 * @param baselineImportance how much this kind of thing matters, before the
 *     particular memory adjusts it
 */
public enum MemoryKind {

    /** Where something is. Places do not move. */
    RESOURCE_LOCATION("where something is", 4000, 0.80),

    /** How much of it there was, which is true only for a while. */
    OBSERVED_RESOURCE("how much was there", 200, 0.40),

    /** Something that worked. */
    SUCCESSFUL_ACTION("what worked", 600, 0.45),

    /** Something that did not, which is worth more than something that did. */
    FAILED_ACTION("what did not work", 600, 0.60),

    /** A close call. Remembered longest of the episodic kinds, for obvious reasons. */
    DANGER("a close call", 1500, 0.95),

    /** Something that happened to the world rather than to the agent. */
    WORLD_EVENT("something that changed", 800, 0.50),

    /**
     * The first sight of something, somewhere.
     *
     * <p>Remembered nearly as long as a close call and valued nearly as
     * highly: finding out that a thing exists, or that this part of the world
     * holds it, is the sort of fact worth keeping when the store fills.
     */
    DISCOVERY("something found for the first time", 2000, 0.85);

    private final String description;
    private final long halfLifeTicks;
    private final double baselineImportance;

    MemoryKind(String description, long halfLifeTicks, double baselineImportance) {
        this.description = description;
        this.halfLifeTicks = halfLifeTicks;
        this.baselineImportance = baselineImportance;
    }

    public String description() {
        return description;
    }

    public long halfLifeTicks() {
        return halfLifeTicks;
    }

    public double baselineImportance() {
        return baselineImportance;
    }

    /** Whether this kind describes a standing fact rather than a passing event. */
    public boolean isDurable() {
        return this == RESOURCE_LOCATION;
    }
}
