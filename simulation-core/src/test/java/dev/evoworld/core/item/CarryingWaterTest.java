package dev.evoworld.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.ChunkMix;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Taking water with you: the whole point of a container.
 *
 * <p>The world here is a walled one with a single spring in a corner, which is
 * the problem this milestone is about - water in one place and an agent whose
 * business is everywhere else.
 */
class CarryingWaterTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(7L)
            .withWorld(WorldBounds.of(24, 24), ChunkMix.standard());
    private static final Location SPRING = new Location(1, 1);
    private static final Location FAR_AWAY = new Location(22, 22);

    /** Everything a run says about what was done and what it did. */
    private static final class Journal implements SimulationObserver {
        final List<ActionOutcome> outcomes = new ArrayList<>();

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            outcomes.add(outcome);
        }

        boolean drankFromThePack() {
            return outcomes.stream()
                    .anyMatch(outcome -> outcome.succeeded()
                            && outcome.actionName().equals(DrinkAction.NAME)
                            && outcome.detail().contains("from its pack"));
        }
    }

    /** An agent that knows what water is, because this is not a test about learning. */
    private static Agent experiencedAgent(Location where, Needs needs) {
        Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", where, needs);
        Knowledge learned = agent.knowledge();
        for (int i = 0; i < 20; i++) {
            learned = learned
                    .learned(BeliefKind.HYDRATION, ResourceType.WATER, 12.0)
                    .learned(BeliefKind.LOCATION_RELIABILITY, ResourceType.WATER, 1.0);
        }
        agent.updateKnowledge(learned);
        return agent;
    }

    private static World worldWithASpring() {
        World world = new World(CONFIG.bounds());
        world.addResource(ResourceNode.full(ResourceType.WATER, SPRING));
        return world;
    }

    @Test
    @DisplayName("water cannot be carried in bare hands, however empty the pack")
    void waterNeedsSomethingToCarryItIn() {
        Agent agent = experiencedAgent(SPRING, Needs.healthy());

        assertEquals(0.0, agent.store(ResourceType.WATER, 5.0), 1e-9);
        assertEquals(0.0, agent.inventory().quantityOf(ResourceType.WATER), 1e-9);
        assertTrue(agent.inventory().freeSpace() > 5.0, "the pack is empty; there is simply nowhere to put it");
    }

    @Test
    @DisplayName("a skin is what makes water carryable, and only up to what it holds")
    void aSkinHoldsWater() {
        Agent agent = experiencedAgent(SPRING, Needs.healthy());
        agent.acquire(Item.made(ItemKind.FLASK));

        double stored = agent.store(ResourceType.WATER, 20.0);

        assertEquals(ItemKind.FLASK.holdsUpTo(), stored, 1e-9);
        assertEquals(0.0, agent.store(ResourceType.WATER, 5.0), 1e-9, "a full skin is full");
    }

    @Test
    @DisplayName("thirsty and far from water, it drinks what it carries instead of walking back")
    void drinksFromThePackAwayFromWater() {
        World world = worldWithASpring();
        Agent atlas = experiencedAgent(FAR_AWAY, new Needs(0.0, 60.0, 100.0, Needs.MAX));
        atlas.acquire(Item.made(ItemKind.FLASK));
        atlas.store(ResourceType.WATER, 8.0);
        world.addAgent(atlas);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);
        Journal journal = new Journal();
        simulation.addObserver(journal);

        simulation.run(6);

        assertTrue(journal.drankFromThePack(), "should have drunk what it was carrying");
        assertTrue(atlas.needs().thirst() < 60.0, "and be less thirsty for it: " + atlas.needs());
        assertTrue(
                atlas.location().manhattanDistanceTo(FAR_AWAY) < 6,
                "without setting off for the spring: " + atlas.location());
    }

    @Test
    @DisplayName("standing in a spring with a skin, it fills up before it leaves")
    void fillsUpWhileItIsThere() {
        World world = worldWithASpring();
        // Watered, so nothing about this is thirst: a full agent filling a skin
        // is the whole idea - it is provisioning, not drinking.
        Agent atlas = experiencedAgent(SPRING, new Needs(0.0, 0.0, 100.0, Needs.MAX));
        atlas.acquire(Item.made(ItemKind.FLASK));
        world.addAgent(atlas);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);

        simulation.run(10);

        assertTrue(
                atlas.inventory().quantityOf(ResourceType.WATER) > 0.0,
                "should have filled the skin while standing in the water");
    }

    @Test
    @DisplayName("a skin it does not have is a skin it cannot fill")
    void noSkinNoStockingUp() {
        World world = worldWithASpring();
        Agent atlas = experiencedAgent(SPRING, new Needs(0.0, 0.0, 100.0, Needs.MAX));
        world.addAgent(atlas);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(CONFIG), CONFIG);

        simulation.run(10);

        assertFalse(
                atlas.inventory().quantityOf(ResourceType.WATER) > 0.0,
                "nothing to carry it in, so nothing carried");
    }
}
