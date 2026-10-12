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
        List<ResourceSnapshot> resources,
        List<FireSnapshot> fires) {

    public WorldState {
        agents = List.copyOf(agents);
        resources = List.copyOf(resources);
        fires = List.copyOf(fires);
    }

    /** The same without any fires, for the many places that do not have them. */
    public WorldState(
            SimulationTime time,
            WorldBounds bounds,
            List<AgentSnapshot> agents,
            List<ResourceSnapshot> resources) {
        this(time, bounds, agents, resources, List.of());
    }

    /** What is burning at a place, if anything. */
    public java.util.Optional<FireSnapshot> fireAt(Location where) {
        return fires.stream().filter(fire -> fire.location().equals(where)).findFirst();
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
