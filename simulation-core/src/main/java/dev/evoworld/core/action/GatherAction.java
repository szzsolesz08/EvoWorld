package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.item.Item;
import dev.evoworld.core.item.ItemKind;
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
    /** Twenty minutes at the default ten-minute tick. */
    private static final int DEFAULT_TICKS = 2;

    private final ResourceType target;
    private final double amount;
    private final double energyCost;
    private final int ticks;

    /** A gather of the default length. */
    public GatherAction(ResourceType target, double amount, double energyCost) {
        this(target, amount, energyCost, DEFAULT_TICKS);
    }

    /** @param ticks how many ticks the work takes, from the gather time and the tick length */
    public GatherAction(ResourceType target, double amount, double energyCost, int ticks) {
        this.target = Objects.requireNonNull(target, "target");
        if (amount <= 0.0) {
            throw new IllegalArgumentException("must gather something, was " + amount);
        }
        if (energyCost < 0.0) {
            throw new IllegalArgumentException("gathering cannot give energy back");
        }
        if (ticks < 1) {
            throw new IllegalArgumentException("a gather takes at least one tick, was " + ticks);
        }
        this.amount = amount;
        this.energyCost = energyCost;
        this.ticks = ticks;
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
        return ticks;
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
        if (agent.roomFor(target) <= 0.0) {
            // Not always the pack being full: water needs something to carry it
            // in, and an agent without a skin has no room for it at all.
            return Optional.of(Refusal.now(
                    target.needsContainer() && agent.belongings().roomFor(target) <= 0.0
                            ? "nothing to carry " + target.label() + " in"
                            : "already carrying all it can"));
        }
        if (agent.needs().energy() < energyCost) {
            return Optional.of(Refusal.now("too tired to gather"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        ResourceNode node = world.availableResourceAt(agent.location()).orElseThrow();
        // The right tool takes more per swing for less effort. Nothing here
        // knows what an axe is: it asks what it has for this job and uses it.
        Optional<Item> tool = agent.belongings().toolFor(node.type());
        double withTool = amount * tool.map(held -> held.kind().yieldMultiplier()).orElse(1.0);
        // Never pull more out of the world than the agent can actually hold:
        // anything harvested past that would simply vanish.
        double room = Math.min(withTool, agent.roomFor(node.type()));
        double harvested = node.harvest(room);
        double stored = agent.store(node.type(), harvested);
        // A practised forager spends less on the same haul, and so does one
        // with an axe; the two compound, because they are different savings.
        agent.exert(energyCost
                * agent.skills().effortMultiplier(NAME)
                * tool.map(held -> held.kind().effortMultiplier()).orElse(1.0));
        tool.ifPresent(held -> agent.use(held.kind()));
        // Filling a container wears the container.
        if (stored > 0.0 && node.type().needsContainer()) {
            ItemKind.carrying(node.type()).ifPresent(agent::use);
        }
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
                && Double.compare(energyCost, gather.energyCost) == 0
                && ticks == gather.ticks;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, amount, energyCost, ticks);
    }

    @Override
    public String toString() {
        return NAME + " " + target.label();
    }
}
