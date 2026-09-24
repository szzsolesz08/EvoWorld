package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.craft.Recipe;
import dev.evoworld.core.item.Item;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Make something out of what the agent is carrying.
 *
 * <p>The one action that turns materials into a thing with a use. Like a
 * gather it takes real time and real effort, and like everything else it can
 * be refused: without the wood there is no flask, and being told so is how an
 * agent finds out it needs to go and find some.
 *
 * <p>What comes out is not assumed to be worth having. The outcome says what
 * was made and at what cost; whether that was a good idea is something the
 * agent works out later, by using the thing.
 */
public final class CraftAction extends Action {

    public static final String NAME = "CRAFT";

    private final Recipe recipe;
    private final int ticks;

    public CraftAction(Recipe recipe, int ticks) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (ticks < 1) {
            throw new IllegalArgumentException("making something takes a tick at least, was " + ticks);
        }
        this.ticks = ticks;
    }

    public Recipe recipe() {
        return recipe;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int durationTicks() {
        return ticks;
    }

    /** Work you get better at, like gathering. */
    @Override
    public boolean trainable() {
        return true;
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        for (Map.Entry<ResourceType, Double> needed : recipe.needs().entrySet()) {
            if (!agent.inventory().has(needed.getKey(), needed.getValue())) {
                return Optional.of(Refusal.now(String.format(
                        Locale.ROOT,
                        "not enough %s for a %s: %.1f of the %.1f needed",
                        needed.getKey().label(),
                        recipe.makes().label(),
                        agent.inventory().quantityOf(needed.getKey()),
                        needed.getValue())));
            }
        }
        if (agent.needs().energy() < recipe.energyCost()) {
            return Optional.of(Refusal.now("too tired to make a " + recipe.makes().label()));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        recipe.needs().forEach(agent::take);
        agent.acquire(Item.made(recipe.makes()));
        // Practice makes the work cheaper, the same as it does for gathering.
        agent.exert(recipe.energyCost() * agent.skills().effortMultiplier(NAME));
        return String.format(
                Locale.ROOT,
                "made a %s out of %s",
                recipe.makes().label(),
                recipe.needs().entrySet().stream()
                        .map(entry -> String.format(
                                Locale.ROOT, "%.0f %s", entry.getValue(), entry.getKey().label()))
                        .reduce((first, second) -> first + " and " + second)
                        .orElse("nothing"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CraftAction craft
                && recipe.equals(craft.recipe)
                && ticks == craft.ticks;
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipe, ticks);
    }

    @Override
    public String toString() {
        return NAME + " " + recipe.makes().label();
    }
}
