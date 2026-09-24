package dev.evoworld.core.world;

import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.intent.Intent;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable copy of one agent's observable state at a point in time.
 *
 * @param ticksAlive how long this agent has lived, which stops rising at death
 * @param intent where the agent has set out for, if anywhere
 */
public record AgentSnapshot(
        AgentId id,
        String name,
        Location location,
        Needs needs,
        Inventory inventory,
        Belongings belongings,
        long ticksAlive,
        boolean alive,
        Optional<Intent> intent) {

    public AgentSnapshot {
        Objects.requireNonNull(belongings, "belongings");
        Objects.requireNonNull(intent, "intent");
    }

    /**
     * How much more of something this agent could actually take.
     *
     * <p>Room in the pack, and - for anything that will not stay in your hands -
     * room in whatever it has to carry that in. The one place this is decided,
     * because a gather that takes more out of the world than the agent can hold
     * would destroy the difference.
     */
    public double roomFor(ResourceType type) {
        double inThePack = inventory.freeSpace();
        if (!type.needsContainer()) {
            return inThePack;
        }
        double inAContainer = belongings.roomFor(type) - inventory.quantityOf(type);
        return Math.max(0.0, Math.min(inThePack, inAContainer));
    }

    /** An agent not heading anywhere in particular. */
    public AgentSnapshot(
            AgentId id,
            String name,
            Location location,
            Needs needs,
            Inventory inventory,
            Belongings belongings,
            long ticksAlive,
            boolean alive) {
        this(id, name, location, needs, inventory, belongings, ticksAlive, alive, Optional.empty());
    }
}
