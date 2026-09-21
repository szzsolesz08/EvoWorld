package dev.evoworld.core.world;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The simulated space and everything in it: a bounded grid holding resource
 * nodes and agents.
 *
 * <p>A tile holds at most one resource node. That is a simplification, not a
 * law of nature - it keeps "what is here?" a single lookup, and nothing yet
 * needs a bush and a stream on the same square.
 *
 * <p>Resources are stored in a {@link LinkedHashMap} rather than a
 * {@link java.util.HashMap} on purpose: iteration order has to be insertion
 * order, or the order resources regenerate in would depend on hash codes and a
 * run would stop being reproducible.
 */
public final class World {

    private final WorldBounds bounds;
    private final Map<Location, ResourceNode> resources = new LinkedHashMap<>();
    private final List<Agent> agents = new ArrayList<>();

    public World(WorldBounds bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    public WorldBounds bounds() {
        return bounds;
    }

    // --- resources --------------------------------------------------------

    /**
     * Places a resource node.
     *
     * @throws IllegalArgumentException if it lies outside the world, or the
     *     tile is already occupied by another node
     */
    public void addResource(ResourceNode node) {
        Objects.requireNonNull(node, "node");
        if (!bounds.contains(node.location())) {
            throw new IllegalArgumentException(
                    "resource " + node.type().label() + " placed outside the world at "
                            + node.location());
        }
        ResourceNode existing = resources.putIfAbsent(node.location(), node);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "tile " + node.location() + " already holds " + existing.type().label());
        }
    }

    /** The node on a tile, if there is one. */
    public Optional<ResourceNode> resourceAt(Location location) {
        return Optional.ofNullable(resources.get(location));
    }

    /** The node on a tile, but only if it has something left to give. */
    public Optional<ResourceNode> availableResourceAt(Location location) {
        return resourceAt(location).filter(ResourceNode::isAvailable);
    }

    /** All nodes, in the order they were placed. */
    public List<ResourceNode> resources() {
        return List.copyOf(resources.values());
    }

    public List<ResourceNode> resourcesOfType(ResourceType type) {
        return resources.values().stream().filter(node -> node.type() == type).toList();
    }

    /** Applies one tick of regrowth to every node. */
    public void regenerateResources() {
        resources.values().forEach(ResourceNode::regenerate);
    }

    // --- agents -----------------------------------------------------------

    public void addAgent(Agent agent) {
        if (!bounds.contains(agent.location())) {
            throw new IllegalArgumentException(
                    "agent " + agent.id() + " spawned outside the world at " + agent.location());
        }
        agents.add(agent);
    }

    /** Agents in insertion order, which keeps tick processing deterministic. */
    public List<Agent> agents() {
        return List.copyOf(agents);
    }

    public Optional<Agent> findAgent(AgentId id) {
        return agents.stream().filter(agent -> agent.id().equals(id)).findFirst();
    }

    // --- observation ------------------------------------------------------

    public WorldState snapshot(SimulationTime time) {
        List<AgentSnapshot> agentSnapshots = agents.stream().map(Agent::snapshot).toList();
        List<ResourceSnapshot> resourceSnapshots =
                resources.values().stream().map(ResourceNode::snapshot).toList();
        return new WorldState(time, bounds, agentSnapshots, resourceSnapshots);
    }
}
