package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Drink, either from a source underfoot or from what the agent carries.
 *
 * <p>It used to be only the first: food was carried and water was somewhere you
 * had to be. That asymmetry was in the world, not in the body - you cannot take
 * a spring with you, but you can take a skin of water - so it belongs to
 * whether the agent has anything to carry water in, not to what drinking is.
 * Given a container, drinking from the pack is the exact mirror of eating from
 * it, and the decision layer needs no new idea to weigh one against the other.
 *
 * <p>Names the source it means to drink from rather than taking whatever is
 * underfoot, so an outcome says what it was about without anyone having to
 * guess. Trying to drink a stone outcrop is refused permanently, which is how
 * an agent learns that stone is not a drink.
 */
public final class DrinkAction extends Action {

    public static final String NAME = "DRINK";

    private final ResourceType source;
    private final double gulp;
    private final boolean fromPack;

    public DrinkAction(ResourceType source, double gulp) {
        this(source, gulp, false);
    }

    /** A drink from what the agent is carrying rather than from the ground. */
    public static DrinkAction fromPack(ResourceType source, double gulp) {
        return new DrinkAction(source, gulp, true);
    }

    public DrinkAction(ResourceType source, double gulp, boolean fromPack) {
        this.source = Objects.requireNonNull(source, "source");
        if (gulp <= 0.0) {
            throw new IllegalArgumentException("must drink something, was " + gulp);
        }
        this.gulp = gulp;
        this.fromPack = fromPack;
    }

    /** Whether this drinks what is carried rather than what is underfoot. */
    public boolean isFromPack() {
        return fromPack;
    }

    public ResourceType source() {
        return source;
    }

    public double gulp() {
        return gulp;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Optional<ResourceType> concerns() {
        return Optional.of(source);
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        if (fromPack) {
            if (agent.inventory().quantityOf(source) <= 0.0) {
                return Optional.of(Refusal.now("no " + source.label() + " left to drink"));
            }
        } else {
            Optional<ResourceNode> here = world.availableResourceAt(agent.location());
            if (here.isEmpty() || here.get().type() != source) {
                return Optional.of(
                        Refusal.now("no " + source.label() + " to drink at " + agent.location()));
            }
        }
        // What a thing is comes before how the agent happens to feel: stone is
        // not a drink whether or not anyone is thirsty, and saying so first is
        // what lets even a comfortable agent learn it.
        if (!source.isDrinkable()) {
            return Optional.of(Refusal.never(source.label() + " is not a drink"));
        }
        if (agent.needs().thirst() <= Needs.MIN) {
            return Optional.of(Refusal.now("not thirsty"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        double drunk = fromPack
                ? agent.take(source, gulp)
                : world.availableResourceAt(agent.location()).orElseThrow().harvest(gulp);
        double before = agent.needs().thirst();
        agent.drink(drunk * source.hydrationPerUnit());
        return String.format(
                Locale.ROOT,
                "drank %.1f %s %s, thirst %.1f -> %.1f",
                drunk,
                source.label(),
                fromPack ? "from its pack" : "at " + agent.location(),
                before,
                agent.needs().thirst());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DrinkAction drink
                && source == drink.source
                && fromPack == drink.fromPack
                && Double.compare(gulp, drink.gulp) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, gulp, fromPack);
    }

    @Override
    public String toString() {
        return NAME + " " + source.label() + (fromPack ? " (carried)" : "");
    }
}
