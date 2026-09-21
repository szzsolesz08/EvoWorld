package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Eat something the agent is carrying.
 *
 * <p>Food comes out of the inventory, not off the ground: eating is what makes
 * gathering worth doing.
 *
 * <p>Whether a thing is food is a fact about the world, checked here. Whether
 * the <em>agent</em> thinks it is food is a different question entirely, and
 * one this action deliberately does not ask - an agent is free to try eating a
 * stone, and being told it is not food is how it finds out. That refusal is
 * {@link Refusal#never permanent}, which is what makes it worth learning from.
 */
public final class EatAction extends Action {

    public static final String NAME = "EAT";

    private final ResourceType food;
    private final double portion;

    public EatAction(ResourceType food, double portion) {
        this.food = Objects.requireNonNull(food, "food");
        if (portion <= 0.0) {
            throw new IllegalArgumentException("must eat something, was " + portion);
        }
        this.portion = portion;
    }

    public ResourceType food() {
        return food;
    }

    public double portion() {
        return portion;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Optional<ResourceType> concerns() {
        return Optional.of(food);
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        if (!agent.inventory().has(food, portion)) {
            return Optional.of(Refusal.now(String.format(
                    Locale.ROOT,
                    "no %s to eat, carrying %.1f of the %.1f needed",
                    food.label(),
                    agent.inventory().quantityOf(food),
                    portion)));
        }
        // What a thing is comes before how the agent happens to feel.
        if (!food.isEdible()) {
            return Optional.of(Refusal.never(food.label() + " is not food"));
        }
        if (agent.needs().hunger() <= Needs.MIN) {
            return Optional.of(Refusal.now("not hungry"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        double eaten = agent.take(food, portion);
        double nourishment = eaten * food.nourishmentPerUnit();
        double before = agent.needs().hunger();
        agent.eat(nourishment);
        return String.format(
                Locale.ROOT,
                "ate %.1f %s, hunger %.1f -> %.1f",
                eaten,
                food.label(),
                before,
                agent.needs().hunger());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EatAction eat
                && food == eat.food
                && Double.compare(portion, eat.portion) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(food, portion);
    }

    @Override
    public String toString() {
        return NAME + " " + food.label();
    }
}
