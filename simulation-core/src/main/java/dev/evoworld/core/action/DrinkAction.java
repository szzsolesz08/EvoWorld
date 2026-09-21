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
 * Drink from a source the agent is standing on.
 *
 * <p>The mirror of {@link EatAction}: food is carried, water is somewhere you
 * have to be. That asymmetry is deliberate - you can take berries with you, and
 * you cannot take a spring.
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

    public DrinkAction(ResourceType source, double gulp) {
        this.source = Objects.requireNonNull(source, "source");
        if (gulp <= 0.0) {
            throw new IllegalArgumentException("must drink something, was " + gulp);
        }
        this.gulp = gulp;
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
        Optional<ResourceNode> here = world.availableResourceAt(agent.location());
        if (here.isEmpty() || here.get().type() != source) {
            return Optional.of(
                    Refusal.now("no " + source.label() + " to drink at " + agent.location()));
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
        ResourceNode node = world.availableResourceAt(agent.location()).orElseThrow();
        double drunk = node.harvest(gulp);
        double before = agent.needs().thirst();
        agent.drink(drunk * source.hydrationPerUnit());
        return String.format(
                Locale.ROOT,
                "drank %.1f %s at %s, thirst %.1f -> %.1f",
                drunk,
                source.label(),
                agent.location(),
                before,
                agent.needs().thirst());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DrinkAction drink
                && source == drink.source
                && Double.compare(gulp, drink.gulp) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, gulp);
    }

    @Override
    public String toString() {
        return NAME + " " + source.label();
    }
}
