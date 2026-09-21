package dev.evoworld.core.world;

import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.agent.Needs;

/**
 * An immutable copy of one agent's observable state at a point in time.
 *
 * @param ticksAlive how long this agent has lived, which stops rising at death
 */
public record AgentSnapshot(
        AgentId id,
        String name,
        Location location,
        Needs needs,
        Inventory inventory,
        long ticksAlive,
        boolean alive) {
}
