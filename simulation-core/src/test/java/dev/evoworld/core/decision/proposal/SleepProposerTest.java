package dev.evoworld.core.decision.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.goal.RecoverEnergyGoal;
import dev.evoworld.core.goal.RecoverHealthGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** When a night's sleep is on offer, how long it is, and what it is for. */
class SleepProposerTest {

    private static final Duration TICK = Duration.ofMinutes(10);
    private static final MetabolismRates BODY = MetabolismRates.defaults().perTick(TICK);
    /** Ten hours. */
    private static final int NIGHT = 60;

    private static List<ActionCandidate> offered(Needs needs) {
        Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", new Location(8, 8), needs);
        Perception perception = new PerceptionSystem(6).perceive(
                agent, new World(WorldBounds.of(16, 16)), new SimulationTime(1L, TICK));
        return new SleepProposer(BODY, NIGHT).propose(perception, SimulationRandom.seededWith(1L));
    }

    private static int ticksOf(ActionCandidate candidate) {
        return ((SleepAction) candidate.action()).durationTicks();
    }

    @Test
    @DisplayName("not tired, not hurt: no sleep on offer")
    void freshAgentsDoNotSleep() {
        assertEquals(List.of(), offered(new Needs(0, 0, SleepProposer.SLEEPY + 1, 100)));
    }

    @Test
    @DisplayName("tired: one sleep, long enough to wake full")
    void sleepsToFull() {
        List<ActionCandidate> candidates = offered(new Needs(0, 0, 20, 100));

        assertEquals(1, candidates.size());
        assertEquals(RecoverEnergyGoal.NAME, candidates.get(0).goalName());
        double needed = (Needs.MAX - 20) / BODY.sleepingEnergy();
        assertEquals((int) Math.ceil(needed), ticksOf(candidates.get(0)));
        assertEquals(0.8, candidates.get(0).expectedBenefit(), 1e-9);
    }

    @Test
    @DisplayName("never longer than a night")
    void cappedAtANight() {
        MetabolismRates slowSleeper = new MetabolismRates(
                0.7, 2.0, -5.0, -1.5, 1.0, 0.6, 4.0, 2.0, 0.25, 1.0, 2.0).perTick(TICK);
        Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", new Location(8, 8), new Needs(0, 0, 5, 100));
        Perception perception = new PerceptionSystem(6).perceive(
                agent, new World(WorldBounds.of(16, 16)), new SimulationTime(1L, TICK));

        List<ActionCandidate> candidates =
                new SleepProposer(slowSleeper, NIGHT).propose(perception, SimulationRandom.seededWith(1L));

        assertEquals(NIGHT, ticksOf(candidates.get(0)));
    }

    @Test
    @DisplayName("parched: the sleep is cut short so thirst cannot start hurting before it wakes")
    void wakesBeforeThirstHurts() {
        List<ActionCandidate> candidates = offered(new Needs(0, 85, 10, 100));

        int ticks = ticksOf(candidates.get(0));
        assertTrue(85 + ticks * BODY.thirst() < Needs.CRITICAL_PRESSURE,
                "would wake at thirst " + (85 + ticks * BODY.thirst()));
        assertTrue(ticks < (int) Math.ceil(90 / BODY.sleepingEnergy()), "shorter than a full night");
    }

    @Test
    @DisplayName("a need already hurting sets no limit: staying awake would not help it")
    void alreadyHurtingDoesNotForbidSleep() {
        List<ActionCandidate> candidates = offered(new Needs(99, 0, 10, 80));

        assertEquals(1, candidates.size());
        assertTrue(ticksOf(candidates.get(0)) > 1);
    }

    @Test
    @DisplayName("badly hurt but not tired: bed rest, for health alone")
    void bedRest() {
        List<ActionCandidate> candidates = offered(new Needs(0, 0, 100, 30));

        assertEquals(1, candidates.size());
        assertEquals(RecoverHealthGoal.NAME, candidates.get(0).goalName());
        assertEquals(NIGHT, ticksOf(candidates.get(0)));
    }

    @Test
    @DisplayName("tired and hurt: the same sleep serves both")
    void servesBothGoals() {
        List<ActionCandidate> candidates = offered(new Needs(0, 0, 20, 70));

        assertEquals(List.of(RecoverEnergyGoal.NAME, RecoverHealthGoal.NAME),
                candidates.stream().map(ActionCandidate::goalName).toList());
        assertEquals(candidates.get(0).action(), candidates.get(1).action());
    }

    @Test
    @DisplayName("a sleep is worth something from its first tick, not only in the morning")
    void benefitStartsAtOnce() {
        ActionCandidate sleep = offered(new Needs(0, 0, 20, 100)).get(0);

        assertEquals(1, sleep.ticksToBenefit());
        assertTrue(sleep.action().durationTicks() > 1);
    }
}
