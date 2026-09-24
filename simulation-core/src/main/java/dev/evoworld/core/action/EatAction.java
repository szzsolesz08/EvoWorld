package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Eat something the agent is carrying, or taste what is underfoot.
 *
 * <p>Food normally comes out of the inventory: eating is what makes gathering
 * worth doing. But a thing you cannot carry is still a thing you can put in
 * your mouth where it lies, and that matters more than it sounds. When water
 * became something an agent could not carry without a skin, tasting it became
 * impossible too - so it could never find out that water is not food, and it
 * spent its life walking back and forth to a spring hoping for a meal. You can
 * always taste a thing where you find it, exactly as you can drink where you
 * find it.
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
    private final boolean fromGround;

    public EatAction(ResourceType food, double portion) {
        this(food, portion, false);
    }

    /** A mouthful of what is underfoot, for something that cannot be carried. */
    public static EatAction fromGround(ResourceType food, double portion) {
        return new EatAction(food, portion, true);
    }

    public EatAction(ResourceType food, double portion, boolean fromGround) {
        this.food = Objects.requireNonNull(food, "food");
        if (portion <= 0.0) {
            throw new IllegalArgumentException("must eat something, was " + portion);
        }
        this.portion = portion;
        this.fromGround = fromGround;
    }

    /** Whether this eats what is underfoot rather than what is carried. */
    public boolean isFromGround() {
        return fromGround;
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
        if (fromGround) {
            boolean thereToTaste = world.availableResourceAt(agent.location())
                    .filter(node -> node.type() == food)
                    .isPresent();
            if (!thereToTaste) {
                return Optional.of(Refusal.now(
                        "no " + food.label() + " to taste at " + agent.location()));
            }
        } else if (!agent.inventory().has(food, portion)) {
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
        double eaten = fromGround
                ? world.availableResourceAt(agent.location()).orElseThrow().harvest(portion)
                : agent.take(food, portion);
        double nourishment = eaten * food.nourishmentPerUnit();
        double before = agent.needs().hunger();
        agent.eat(nourishment);
        return String.format(
                Locale.ROOT,
                "ate %.1f %s%s, hunger %.1f -> %.1f",
                eaten,
                food.label(),
                fromGround ? " where it lay" : "",
                before,
                agent.needs().hunger());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EatAction eat
                && food == eat.food
                && fromGround == eat.fromGround
                && Double.compare(portion, eat.portion) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(food, portion, fromGround);
    }

    @Override
    public String toString() {
        return NAME + " " + food.label();
    }
}
