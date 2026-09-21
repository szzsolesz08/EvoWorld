package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Harvest what is on this tile into the agent's own hands.
 *
 * <p>The one action that moves something from the world into an inventory, so
 * it is where conservation has to hold: whatever leaves the node arrives in the
 * agent, exactly once. It takes more than a tick because picking a bush clean
 * is work.
 *
 * <p>Names the resource it means to gather, so a refusal says which expectation
 * was disappointed. Going somewhere for berries and finding none is exactly the
 * experience an agent should learn from.
 */
public final class GatherAction extends Action {

    public static final String NAME = "GATHER";
    private static final int TICKS = 2;

    private final ResourceType target;
    private final double amount;
    private final double energyCost;

    public GatherAction(ResourceType target, double amount, double energyCost) {
        this.target = Objects.requireNonNull(target, "target");
        if (amount <= 0.0) {
            throw new IllegalArgumentException("must gather something, was " + amount);
        }
        if (energyCost < 0.0) {
            throw new IllegalArgumentException("gathering cannot give energy back");
        }
        this.amount = amount;
        this.energyCost = energyCost;
    }

    public ResourceType target() {
        return target;
    }

    public double amount() {
        return amount;
    }

    public double energyCost() {
        return energyCost;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int durationTicks() {
        return TICKS;
    }

    @Override
    public Optional<ResourceType> concerns() {
        return Optional.of(target);
    }

    @Override
    public boolean trainable() {
        return true;
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        Optional<ResourceNode> node = world.availableResourceAt(agent.location());
        if (node.isEmpty() || node.get().type() != target) {
            return Optional.of(
                    Refusal.now("no " + target.label() + " left to gather at " + agent.location()));
        }
        if (agent.inventory().isFull()) {
            return Optional.of(Refusal.now("already carrying all it can"));
        }
        if (agent.needs().energy() < energyCost) {
            return Optional.of(Refusal.now("too tired to gather"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        ResourceNode node = world.availableResourceAt(agent.location()).orElseThrow();
        // Never pull more out of the world than the agent can actually hold:
        // anything harvested past that would simply vanish.
        double room = agent.inventory().spaceFor(amount);
        double harvested = node.harvest(room);
        double stored = agent.store(node.type(), harvested);
        // A practised forager spends less on the same haul.
        agent.exert(energyCost * agent.skills().effortMultiplier(NAME));
        return String.format(
                Locale.ROOT,
                "gathered %.1f %s at %s, %.1f left in the node",
                stored,
                node.type().label(),
                agent.location(),
                node.quantity());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GatherAction gather
                && target == gather.target
                && Double.compare(amount, gather.amount) == 0
                && Double.compare(energyCost, gather.energyCost) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, amount, energyCost);
    }

    @Override
    public String toString() {
        return NAME + " " + target.label();
    }
}
