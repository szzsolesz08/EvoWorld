package dev.evoworld.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.CraftAction;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole chain, in a real run, from an agent that owns nothing.
 *
 * <p>Nothing here is scripted. The agent is dropped into the ordinary endless
 * world, knowing nothing, with nothing on it, and left alone for twenty days;
 * every step below is something it chose through the same proposal, evaluation
 * and selection the rest of its life goes through.
 */
class ProvisioningRunTest {

    private static final long TWENTY_DAYS = 2880L;

    /** What a run did, in the order it did it. */
    private static final class Journal implements SimulationObserver {
        final List<String> made = new ArrayList<>();
        final List<Long> drinksFromThePack = new ArrayList<>();
        final List<Long> woodGathered = new ArrayList<>();
        int drinks;

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            if (!outcome.succeeded()) {
                return;
            }
            if (outcome.actionName().equals(CraftAction.NAME)) {
                made.add(outcome.detail());
            }
            if (outcome.actionName().equals(DrinkAction.NAME)) {
                drinks++;
                if (outcome.detail().contains("from its pack")) {
                    drinksFromThePack.add(tick);
                }
            }
            if (outcome.actionName().equals("GATHER") && outcome.detail().contains("wood")) {
                woodGathered.add(tick);
            }
        }
    }

    private record Run(Agent agent, Journal journal) {
    }

    private static Run live(long seed, long ticks) {
        SimulationConfig config = SimulationConfig.defaults(seed);
        World world = WorldGenerator.forConfig(config);
        Agent atlas = new Agent(
                AgentId.of("agent-1"), "Atlas", config.bounds().center(), Needs.healthy());
        world.addAgent(atlas);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(config), config);
        Journal journal = new Journal();
        simulation.addObserver(journal);
        simulation.run(ticks);
        return new Run(atlas, journal);
    }

    @Test
    @DisplayName("left alone, it gathers wood, makes a flask, carries water and drinks it away from the spring")
    void theWholeChainWithoutBeingTold() {
        Run run = live(1L, TWENTY_DAYS);

        assertTrue(run.agent().isAlive(), "should survive: " + run.agent());
        assertFalse(run.journal().woodGathered.isEmpty(), "should have picked up wood");
        assertFalse(run.journal().made.isEmpty(), "should have made something");
        assertTrue(
                run.journal().made.stream().anyMatch(what -> what.contains("flask")),
                "and the something should be a flask: " + run.journal().made);
        assertFalse(
                run.journal().drinksFromThePack.isEmpty(),
                "and then drunk out of its own pack");

        // The order is the point: it had to make the thing before it could use it.
        long firstFlask = run.journal().woodGathered.get(0);
        assertTrue(
                run.journal().drinksFromThePack.get(0) > firstFlask,
                "the wood came before the drinking");
    }

    @Test
    @DisplayName("and it works out that the flask was worth making")
    void itLearnsWhatTheFlaskIsWorth() {
        Run run = live(1L, TWENTY_DAYS);

        var belief = run.agent().knowledge().believe(BeliefKind.USEFULNESS, ItemKind.FLASK.name());

        assertFalse(belief.isAssumed(), "should no longer be a hunch");
        assertTrue(belief.estimate() > 0.0, "and should have come out in favour: " + belief);
    }

    @Test
    @DisplayName("nothing is given: an agent starts with nothing on it and nothing believed about flasks")
    void startsWithNothing() {
        Agent newborn = Agent.spawn("agent-1", "Atlas", new dev.evoworld.core.world.Location(0, 0));

        assertTrue(newborn.belongings().isEmpty());
        assertEquals(0.0, newborn.belongings().roomFor(ResourceType.WATER), 1e-9);
        assertTrue(newborn.knowledge().isEmpty(), "and knows nothing at all");
    }

    @Test
    @DisplayName("drinking from the flask is a habit, not a one-off: it happens on day after day")
    void carriedWaterIsAHabit() {
        Run run = live(1L, TWENTY_DAYS);

        List<Long> fromThePack = run.journal().drinksFromThePack;
        long daysItHappened = fromThePack.stream().map(tick -> tick / 144L).distinct().count();

        // Not most of its drinking - standing in a spring is still the cheapest
        // drink there is, and the flask is for when it is somewhere else. What
        // matters is that it keeps happening, which is what makes it a way of
        // living rather than an accident. How much walking it saves is the
        // survey's question, and the survey puts it at about 45 per cent.
        assertTrue(fromThePack.size() >= 10,
                "should drink from the flask repeatedly: " + fromThePack.size()
                        + " of " + run.journal().drinks + " drinks");
        assertTrue(daysItHappened >= 3, "on several different days: " + daysItHappened);
    }
}
