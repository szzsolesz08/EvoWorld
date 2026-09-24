package dev.evoworld.core.perception;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one agent can tell about the world at one moment.
 *
 * <p>This is the whole boundary between the world and the mind. A perception
 * holds no reference to the live world, no {@code WorldState}, and no way to
 * reach anything outside the radius it was built with - so a decision layer
 * cannot consult a berry bush it has no business knowing about, and cannot
 * change anything it can see. Every field is an immutable value.
 *
 * <p>It carries three kinds of knowledge, and the distinction matters:
 *
 * <ul>
 *   <li><strong>self</strong> - the agent's own body, which it always knows in
 *       full;
 *   <li><strong>sensed</strong> - resources and other agents within
 *       {@link #radius}, and nothing beyond it;
 *   <li><strong>known</strong> - the size of the world, the places the agent
 *       remembers but cannot currently see, everything it has worked out about
 *       how the world works, and how much of the map it has covered. Recalled
 *       rather than sensed, and carrying a confidence below one because a
 *       belief is not a sighting.
 * </ul>
 *
 * @param radius how far the agent can sense, in steps
 */
public record Perception(
        SimulationTime time,
        AgentSnapshot self,
        WorldBounds bounds,
        int radius,
        List<PerceivedResource> resources,
        List<PerceivedAgent> others,
        List<RememberedResource> remembered,
        Knowledge knowledge,
        ExplorationView exploration) {

    public Perception {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(bounds, "bounds");
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative, was " + radius);
        }
        resources = List.copyOf(resources);
        others = List.copyOf(others);
        remembered = List.copyOf(remembered);
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(exploration, "exploration");
    }

    // --- the agent's own state --------------------------------------------

    public AgentId agentId() {
        return self.id();
    }

    public Location location() {
        return self.location();
    }

    public Needs needs() {
        return self.needs();
    }

    public Inventory inventory() {
        return self.inventory();
    }

    /** How much more of something the agent could carry, pack and containers together. */
    public double roomFor(dev.evoworld.core.resource.ResourceType type) {
        return self.roomFor(type);
    }

    /** What the agent has on it: its containers and tools, always known in full. */
    public Belongings belongings() {
        return self.belongings();
    }

    public long tick() {
        return time.tick();
    }

    // --- what it can see --------------------------------------------------

    /** Whether a tile is close enough to sense at all. */
    public boolean canSee(Location location) {
        return this.location().manhattanDistanceTo(location) <= radius;
    }

    public List<PerceivedResource> resourcesOfType(ResourceType type) {
        return resources.stream().filter(seen -> seen.type() == type).toList();
    }

    /**
     * The closest resource of a type with something left in it.
     *
     * <p>Resources arrive sorted nearest first, so this is the first match.
     */
    public Optional<PerceivedResource> nearest(ResourceType type) {
        return resources.stream()
                .filter(seen -> seen.type() == type && seen.isAvailable())
                .findFirst();
    }

    /** The resource the agent is standing on, if any. */
    public Optional<PerceivedResource> underfoot() {
        return resources.stream().filter(PerceivedResource::isUnderfoot).findFirst();
    }

    public boolean seesAnything() {
        return !resources.isEmpty() || !others.isEmpty();
    }

    // --- what it remembers ------------------------------------------------

    /** Remembered places of a type, most believed first. */
    public List<RememberedResource> rememberedOfType(ResourceType type) {
        return remembered.stream().filter(place -> place.type() == type).toList();
    }

    /** The place the agent would head for if it had to go on memory alone. */
    public Optional<RememberedResource> bestRemembered(ResourceType type) {
        return rememberedOfType(type).stream().findFirst();
    }

    public boolean remembersAnything() {
        return !remembered.isEmpty();
    }

    /** How much there is left to find out about the world. */
    public double unexploredFraction() {
        return exploration.unexploredFraction();
    }

    /** Everywhere nearby the agent remembers plenty of something. */
    public List<KnownPlenty> plenty() {
        return exploration.plenty();
    }

    /** One readable line per thing sensed, nearest first. */
    public List<String> describe() {
        List<String> lines =
                new ArrayList<>(resources.size() + others.size() + remembered.size());
        resources.forEach(seen -> lines.add(seen.describe()));
        others.forEach(seen -> lines.add(seen.describe()));
        remembered.forEach(place -> lines.add("recalls " + place.describe()));
        return lines;
    }

    @Override
    public String toString() {
        if (!seesAnything() && !remembersAnything()) {
            return "sees nothing within " + radius + " steps";
        }
        return "sees " + String.join(", ", describe());
    }
}
