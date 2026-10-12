package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.TendFireAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nights, in a run: what an agent can see, what the cold costs it, and what it
 * does about the cold when there is wood to hand.
 */
class NightAndFireRunTest {

    private static final long TWENTY_DAYS = 2880L;

    private static final class Journal implements SimulationObserver {
        final List<Long> firesLit = new ArrayList<>();
        final List<Long> firesFed = new ArrayList<>();

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            if (outcome.succeeded() && outcome.actionName().equals(TendFireAction.NAME)) {
                (outcome.detail().startsWith("lit") ? firesLit : firesFed).add(tick);
            }
        }
    }

    @Test
    @DisplayName("an agent sees less in the dark than it does at noon")
    void theDarkShortensSight() {
        PerceptionSystem eyes = new PerceptionSystem(PerceptionSystem.DEFAULT_RADIUS);

        int byDay = eyes.sightIn(Sky.atHour(12.0));
        int byNight = eyes.sightIn(Sky.atHour(2.0));

        assertEquals(PerceptionSystem.DEFAULT_RADIUS, byDay, "broad daylight is full sight");
        assertTrue(byNight < byDay, "and the dark is less: " + byNight);
        assertTrue(byNight > 0, "but not blind: " + byNight);
    }

    @Test
    @DisplayName("what it can see at night is really less: the same field, fewer things in it")
    void fewerThingsInViewAtNight() {
        World world = new World(WorldBounds.of(32, 32));
        for (int step = 2; step <= 10; step += 2) {
            world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(16 + step, 16)));
        }
        Agent atlas = new Agent(AgentId.of("a"), "Atlas", new Location(16, 16), Needs.healthy());
        world.addAgent(atlas);
        PerceptionSystem eyes = new PerceptionSystem(PerceptionSystem.DEFAULT_RADIUS);

        int seenAtNoon = eyes.perceive(atlas, world, at(12.0)).resources().size();
        int seenAtNight = eyes.perceive(atlas, world, at(2.0)).resources().size();

        assertTrue(seenAtNoon > seenAtNight, seenAtNoon + " by day, " + seenAtNight + " by night");
    }

    @Test
    @DisplayName("left alone for twenty days, it lights fires, and lights them at night")
    void itLightsFiresAtNight() {
        Journal journal = new Journal();
        SimulationConfig config = SimulationConfig.defaults(1L);
        World world = WorldGenerator.forConfig(config);
        Agent atlas = new Agent(
                AgentId.of("agent-1"), "Atlas", config.bounds().center(), Needs.healthy());
        world.addAgent(atlas);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(config), config);
        simulation.addObserver(journal);

        simulation.run(TWENTY_DAYS);

        assertTrue(atlas.isAlive(), "should live through the nights: " + atlas);
        assertFalse(journal.firesLit.isEmpty(), "should have lit a fire");
        long inTheCold = journal.firesLit.stream()
                .filter(tick -> Sky.atHour(hourOfTick(tick)).temperature() < 15.0)
                .count();
        assertEquals(journal.firesLit.size(), inTheCold,
                "every fire should have been lit because it was cold, not for something to do");
    }

    @Test
    @DisplayName("a fire is worth wood: it turns a cold night into a warm spot")
    void aFireAnswersTheCold() {
        World world = new World(WorldBounds.of(16, 16));
        Location camp = new Location(8, 8);
        Agent atlas = new Agent(AgentId.of("a"), "Atlas", camp, Needs.healthy());
        atlas.store(ResourceType.WOOD, 6.0);
        world.addAgent(atlas);
        PerceptionSystem eyes = new PerceptionSystem(PerceptionSystem.DEFAULT_RADIUS);

        double coldAlone = eyes.perceive(atlas, world, at(4.0)).conditions().comfort();
        new TendFireAction(4.0, 0.5, 2).execute(atlas, world);
        double byTheFire = eyes.perceive(atlas, world, at(4.0)).conditions().comfort();

        assertTrue(byTheFire > coldAlone,
                "the fire should make for a better night: " + byTheFire + " against " + coldAlone);
        assertEquals(2.0, atlas.inventory().quantityOf(ResourceType.WOOD), 1e-9, "four went on it");
        assertTrue(world.fireAt(camp).isPresent());
    }

    @Test
    @DisplayName("a fire, and the wood on it, come back from a save")
    void firesSurviveASave() {
        SimulationConfig config = SimulationConfig.defaults(3L);
        World world = WorldGenerator.forConfig(config);
        Location camp = config.bounds().center();
        world.addAgent(new Agent(AgentId.of("agent-1"), "Atlas", camp, Needs.healthy()));
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(config), config);
        simulation.run(10);
        world.tendFire(camp, 5.0);

        Simulation reloaded = Simulation.load(simulation.save());

        Fire back = reloaded.world().fireAt(camp).orElseThrow();
        assertEquals(5.0, back.fuel(), 1e-9);
        assertEquals(simulation.save(), reloaded.save(), "and the save of the save is the same save");
    }

    private static SimulationTime at(double hour) {
        return new SimulationTime(1L, Duration.ofMinutes((long) (hour * 60)));
    }

    /** Ten-minute ticks, counted from midnight. */
    private static double hourOfTick(long tick) {
        return Sky.hourOf(Duration.ofMinutes(10 * tick));
    }
}
