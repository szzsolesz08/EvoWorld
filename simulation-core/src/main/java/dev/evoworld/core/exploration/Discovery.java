package dev.evoworld.core.exploration;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Chunk;
import dev.evoworld.core.world.Location;
import java.util.Objects;

/**
 * The moment an agent finds something it had not found before.
 *
 * <p>Not a scripted event and not a list anyone wrote down: a discovery is
 * simply the first sighting of a kind of thing, or the first sighting of it in
 * a part of the world the agent had not found it in before. It falls out of
 * comparing what is in front of the agent with what it has already seen.
 *
 * @param firstEver whether the agent had never seen this kind of thing at all,
 *     as opposed to never having seen it here
 */
public record Discovery(
        ResourceType what, Location where, Chunk chunk, SimulationTime when, boolean firstEver) {

    public Discovery {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(when, "when");
    }

    public String describe() {
        return firstEver
                ? "first ever sight of " + what.label() + " at " + where
                : what.label() + " in a new part of the world, " + chunk + " at " + where;
    }

    @Override
    public String toString() {
        return describe();
    }
}
