package dev.evoworld.core.action;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Put down something the agent is carrying.
 *
 * <p>Exists because experimenting has a cost that outlasts the experiment. An
 * agent that samples wood and stone to find out whether they are food ends up
 * carrying them, and a bag full of things it has learned are useless has no
 * room for the berries it needs. Without a way to put things down, curiosity
 * could starve an agent standing one step from a bush.
 *
 * <p>What is put down is left behind rather than returned to any node: a
 * handful of stone dropped in a field is not an outcrop. That is the one place
 * matter leaves the simulation, and it is here on purpose.
 */
public final class DiscardAction extends Action {

    public static final String NAME = "DISCARD";

    private final ResourceType what;
    private final double amount;

    public DiscardAction(ResourceType what, double amount) {
        this.what = Objects.requireNonNull(what, "what");
        if (amount <= 0.0) {
            throw new IllegalArgumentException("must discard something, was " + amount);
        }
        this.amount = amount;
    }

    public ResourceType what() {
        return what;
    }

    public double amount() {
        return amount;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ActivityLevel activityLevel() {
        // Putting something down is not work.
        return ActivityLevel.RESTING;
    }

    @Override
    public Optional<ResourceType> concerns() {
        return Optional.of(what);
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        if (agent.inventory().quantityOf(what) <= 0.0) {
            return Optional.of(Refusal.now("not carrying any " + what.label()));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        double dropped = agent.take(what, amount);
        return String.format(
                Locale.ROOT,
                "put down %.1f %s, now %s",
                dropped,
                what.label(),
                agent.inventory());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DiscardAction discard
                && what == discard.what
                && Double.compare(amount, discard.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(what, amount);
    }

    @Override
    public String toString() {
        return NAME + " " + what.label();
    }
}
