package dev.evoworld.core.craft;

import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.resource.ResourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ways of making things that an agent knows.
 *
 * <p>Deliberately known rather than discovered. What a thing is <em>worth</em>
 * is the interesting question and the agent has to answer it by experience,
 * the same as it does with berries; how to lash a stone to a stick is a skill
 * of the hands, and an agent that had to discover the idea of an axe before it
 * could try one would simply never try one. If recipe discovery ever earns its
 * keep it belongs here, behind the same call.
 *
 * <p>Only three, and each answers a problem the world already poses: water you
 * cannot take with you, and the wood and stone that everything is made of.
 */
public final class Recipes {

    private static final List<Recipe> KNOWN = List.of(
            new Recipe(ItemKind.FLASK, needing(ResourceType.WOOD, 6.0), 60L, 4.0),
            new Recipe(ItemKind.AXE, needing(ResourceType.WOOD, 4.0, ResourceType.STONE, 3.0), 90L, 6.0),
            new Recipe(ItemKind.PICKAXE, needing(ResourceType.WOOD, 4.0, ResourceType.STONE, 4.0), 90L, 6.0));

    private Recipes() {
    }

    /** Everything an agent knows how to make, in a fixed order. */
    public static List<Recipe> known() {
        return KNOWN;
    }

    /** How to make one of these, if there is a way. */
    public static Optional<Recipe> forItem(ItemKind kind) {
        return KNOWN.stream().filter(recipe -> recipe.makes() == kind).findFirst();
    }

    /** Whether anything at all is made out of this, which is what makes it worth picking up. */
    public static boolean isMaterial(ResourceType type) {
        return KNOWN.stream().anyMatch(recipe -> recipe.needs().containsKey(type));
    }

    private static Map<ResourceType, Double> needing(ResourceType type, double amount) {
        Map<ResourceType, Double> needs = new LinkedHashMap<>();
        needs.put(type, amount);
        return needs;
    }

    private static Map<ResourceType, Double> needing(
            ResourceType first, double firstAmount, ResourceType second, double secondAmount) {
        Map<ResourceType, Double> needs = needing(first, firstAmount);
        needs.put(second, secondAmount);
        return needs;
    }
}
