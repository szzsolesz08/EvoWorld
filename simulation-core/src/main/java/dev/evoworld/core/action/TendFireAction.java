package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Fire;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Optional;

/**
 * Light a fire where the agent stands, or feed the one already burning there.
 *
 * <p>One action for both because they are the same act: wood out of the pack
 * and onto the fire. Which of the two it turned out to be is in the outcome,
 * not in the decision - an agent that walks back to its fire with an armful of
 * wood is doing what it did when it lit it.
 *
 * <p>Unlike everything else an agent makes, a fire stays in the world rather
 * than on the agent. It cannot be carried, it warms whoever is near it, and it
 * burns down whether or not anybody is watching.
 */
public final class TendFireAction extends Action {

    public static final String NAME = "TEND_FIRE";

    private final double wood;
    private final double energyCost;
    private final int ticks;

    public TendFireAction(double wood, double energyCost, int ticks) {
        if (wood <= 0.0) {
            throw new IllegalArgumentException("a fire needs wood, was " + wood);
        }
        if (ticks < 1) {
            throw new IllegalArgumentException("laying a fire takes a tick at least, was " + ticks);
        }
        this.wood = wood;
        this.energyCost = energyCost;
        this.ticks = ticks;
    }

    public double wood() {
        return wood;
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
        return Optional.of(ResourceType.WOOD);
    }

    /** Work you get better at. */
    @Override
    public boolean trainable() {
        return true;
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        double carried = agent.inventory().quantityOf(ResourceType.WOOD);
        double needed = world.fireAt(agent.location()).isPresent() ? wood : Fire.KINDLING;
        if (carried < needed) {
            return Optional.of(Refusal.now(String.format(
                    Locale.ROOT,
                    "not enough wood for a fire: %.1f of the %.1f needed",
                    carried,
                    needed)));
        }
        if (world.fireAt(agent.location()).filter(fire -> fire.roomForFuel() <= 0.0).isPresent()) {
            return Optional.of(Refusal.now("the fire here is already well fed"));
        }
        if (agent.needs().energy() < energyCost) {
            return Optional.of(Refusal.now("too tired to lay a fire"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        boolean wasBurning = world.fireAt(agent.location()).isPresent();
        double offered = Math.min(wood, agent.inventory().quantityOf(ResourceType.WOOD));
        double used = world.tendFire(agent.location(), offered);
        agent.take(ResourceType.WOOD, used);
        agent.exert(energyCost * agent.skills().effortMultiplier(NAME));
        Fire fire = world.fireAt(agent.location()).orElseThrow();
        return String.format(
                Locale.ROOT,
                "%s a fire at %s with %.1f wood, %.1f hours of it left",
                wasBurning ? "fed" : "lit",
                agent.location(),
                used,
                fire.hoursLeft());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TendFireAction tend
                && Double.compare(wood, tend.wood) == 0
                && Double.compare(energyCost, tend.energyCost) == 0
                && ticks == tend.ticks;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(wood, energyCost, ticks);
    }

    @Override
    public String toString() {
        return NAME;
    }
}
