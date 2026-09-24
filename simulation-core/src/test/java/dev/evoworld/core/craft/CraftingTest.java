package dev.evoworld.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.CraftAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.item.Item;
import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.knowledge.KnowledgeLearner;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Making things: what it costs, what it needs, and what it teaches. */
class CraftingTest {

    private static final Location HERE = new Location(4, 4);

    private static World world() {
        return new World(WorldBounds.of(16, 16));
    }

    private static Agent agentCarrying(ResourceType type, double amount) {
        Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", HERE, Needs.healthy());
        agent.store(type, amount);
        return agent;
    }

    private static Recipe flask() {
        return Recipes.forItem(ItemKind.FLASK).orElseThrow();
    }

    @Test
    @DisplayName("a recipe has to take something and make something")
    void recipesAreWellFormed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Recipe(ItemKind.FLASK, Map.of(), 60L, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Recipe(ItemKind.FLASK, Map.of(ResourceType.WOOD, 6.0), 0L, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Recipe(ItemKind.FLASK, Map.of(ResourceType.WOOD, -1.0), 60L, 1.0));
    }

    @Test
    @DisplayName("every known recipe makes something out of things the world actually has")
    void everyRecipeIsMakeable() {
        assertFalse(Recipes.known().isEmpty());
        for (Recipe recipe : Recipes.known()) {
            assertFalse(recipe.needs().isEmpty(), recipe + " makes something out of nothing");
            recipe.needs().keySet().forEach(type ->
                    assertTrue(Recipes.isMaterial(type), type + " should count as a material"));
        }
        assertFalse(Recipes.isMaterial(ResourceType.BERRIES), "berries are food, not stuff");
    }

    @Test
    @DisplayName("without the wood there is no flask, and the agent is told why")
    void notEnoughMaterials() {
        Agent agent = agentCarrying(ResourceType.WOOD, 2.0);

        ActionOutcome outcome = new CraftAction(flask(), 6).execute(agent, world());

        assertFalse(outcome.succeeded());
        assertTrue(outcome.detail().contains("not enough wood"), outcome.detail());
        assertTrue(agent.belongings().isEmpty());
        assertEquals(2.0, agent.inventory().quantityOf(ResourceType.WOOD), 1e-9, "and keeps its wood");
    }

    @Test
    @DisplayName("with the wood, the wood becomes a flask and the pack is lighter for it")
    void makingAFlask() {
        Agent agent = agentCarrying(ResourceType.WOOD, 8.0);
        double energyBefore = agent.needs().energy();

        ActionOutcome outcome = new CraftAction(flask(), 6).execute(agent, world());

        assertTrue(outcome.succeeded(), outcome.detail());
        assertTrue(agent.belongings().has(ItemKind.FLASK));
        assertEquals(2.0, agent.inventory().quantityOf(ResourceType.WOOD), 1e-9, "six went into it");
        assertTrue(agent.needs().energy() < energyBefore, "and the work cost something");
        assertTrue(
                agent.inventory().capacity() < dev.evoworld.core.agent.Inventory.DEFAULT_CAPACITY,
                "a flask on your belt is room you no longer have in the pack");
    }

    @Test
    @DisplayName("too tired to make anything is a refusal, not a half-made flask")
    void tooTired() {
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", HERE, new Needs(0.0, 0.0, 1.0, Needs.MAX));
        agent.store(ResourceType.WOOD, 8.0);

        ActionOutcome outcome = new CraftAction(flask(), 6).execute(agent, world());

        assertFalse(outcome.succeeded());
        assertTrue(agent.belongings().isEmpty());
    }

    @Test
    @DisplayName("a tool takes more per swing for less effort, and wears doing it")
    void toolsChangeTheWork() {
        World withWood = world();
        withWood.addResource(ResourceNode.full(ResourceType.WOOD, HERE));
        Agent barehanded = new Agent(AgentId.of("a"), "Atlas", HERE, Needs.healthy());
        Agent withAxe = new Agent(AgentId.of("b"), "Atlas", HERE, Needs.healthy());
        withAxe.acquire(Item.made(ItemKind.AXE));

        new GatherAction(ResourceType.WOOD, 5.0, 2.0).execute(barehanded, withWood);
        World freshWood = world();
        freshWood.addResource(ResourceNode.full(ResourceType.WOOD, HERE));
        new GatherAction(ResourceType.WOOD, 5.0, 2.0).execute(withAxe, freshWood);

        assertTrue(
                withAxe.inventory().quantityOf(ResourceType.WOOD)
                        > barehanded.inventory().quantityOf(ResourceType.WOOD),
                "an axe should bring back more wood");
        assertTrue(
                withAxe.needs().energy() > barehanded.needs().energy(),
                "for less effort");
        assertEquals(
                ItemKind.AXE.uses() - 1,
                withAxe.belongings().items().get(0).usesLeft(),
                "and be a swing closer to firewood itself");
    }

    @Test
    @DisplayName("a thing that did its job is a thing worth having, and the agent notes it")
    void usingSomethingTeachesWhatItIsWorth() {
        Agent agent = new Agent(AgentId.of("a"), "Atlas", HERE, new Needs(0.0, 50.0, 100.0, Needs.MAX));
        agent.acquire(Item.made(ItemKind.FLASK));
        agent.store(ResourceType.WATER, 4.0);
        World world = world();

        assertTrue(agent.knowledge().believe(BeliefKind.USEFULNESS, ItemKind.FLASK.name()).isAssumed(),
                "it starts out guessing");

        dev.evoworld.core.action.DrinkAction drink =
                dev.evoworld.core.action.DrinkAction.fromPack(ResourceType.WATER, 1.0);
        ActionOutcome outcome = drink.execute(agent, world);
        Knowledge learned =
                KnowledgeLearner.learnFrom(agent.knowledge(), drink, outcome, agent.belongings());

        assertTrue(outcome.succeeded(), outcome.detail());
        assertFalse(learned.believe(BeliefKind.USEFULNESS, ItemKind.FLASK.name()).isAssumed(),
                "a drink out of the pack is evidence a flask is worth having");
    }
}
