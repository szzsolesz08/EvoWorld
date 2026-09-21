package dev.evoworld.core.perception;

import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Direction;
import dev.evoworld.core.world.Location;
import java.util.Objects;

/**
 * Another agent within sight.
 *
 * <p>Nothing reacts to these yet - there is only ever one agent - but the
 * world's other inhabitants are exactly the kind of thing perception exists to
 * filter, and leaving them out would mean rebuilding the boundary later.
 */
public record PerceivedAgent(AgentSnapshot agent, int stepsAway, Direction direction) {

    public PerceivedAgent {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(direction, "direction");
        if (stepsAway < 0) {
            throw new IllegalArgumentException("distance must not be negative, was " + stepsAway);
        }
    }

    public static PerceivedAgent seenFrom(Location viewer, AgentSnapshot agent) {
        return new PerceivedAgent(
                agent,
                viewer.manhattanDistanceTo(agent.location()),
                Direction.from(viewer, agent.location()));
    }

    public AgentId id() {
        return agent.id();
    }

    public String name() {
        return agent.name();
    }

    public Location location() {
        return agent.location();
    }

    public boolean isAlive() {
        return agent.alive();
    }

    public String describe() {
        return agent.name() + " " + stepsAway + " steps " + direction.label();
    }

    @Override
    public String toString() {
        return describe();
    }
}
