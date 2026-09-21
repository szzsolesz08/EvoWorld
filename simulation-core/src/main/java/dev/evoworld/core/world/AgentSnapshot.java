package dev.evoworld.core.world;

import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
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
        long ticksAlive,
        boolean alive,
        Optional<Intent> intent) {

    public AgentSnapshot {
        Objects.requireNonNull(intent, "intent");
    }

    /** An agent not heading anywhere in particular. */
    public AgentSnapshot(
            AgentId id,
            String name,
            Location location,
            Needs needs,
            Inventory inventory,
            long ticksAlive,
            boolean alive) {
        this(id, name, location, needs, inventory, ticksAlive, alive, Optional.empty());
    }
}
