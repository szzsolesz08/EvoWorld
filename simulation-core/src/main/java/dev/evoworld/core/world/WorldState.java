package dev.evoworld.core.world;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import java.util.List;
import java.util.Optional;

/**
 * An immutable snapshot of the whole world at one moment.
 *
 * <p>This is the contract between the simulation and any observer - a console
 * logger today, a LibGDX viewer or a save file later. Observers never touch
 * live simulation objects, so they can never perturb the simulation.
 *
 * <p>Because every field is an immutable value, two snapshots taken from
 * equivalent runs are equal, which is what makes determinism directly
 * assertable in a test.
 */
public record WorldState(
        SimulationTime time,
        WorldBounds bounds,
        List<AgentSnapshot> agents,
        List<ResourceSnapshot> resources) {

    public WorldState {
        agents = List.copyOf(agents);
        resources = List.copyOf(resources);
    }

    /** Convenience for the common case of only wanting the tick number. */
    public long tick() {
        return time.tick();
    }

    public Optional<ResourceSnapshot> resourceAt(Location location) {
        return resources.stream().filter(node -> node.location().equals(location)).findFirst();
    }

    public List<ResourceSnapshot> resourcesOfType(ResourceType type) {
        return resources.stream().filter(node -> node.type() == type).toList();
    }
}
