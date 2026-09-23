package dev.evoworld.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.knowledge.Belief;
import dev.evoworld.core.memory.MemoryKind;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Chunk;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The whole simulation, left to run on its own for weeks of simulated time.
 *
 * <p>Nothing here steers the agent. Each test builds a generated world, puts
 * Atlas in it, runs thousands of ticks and then asks two kinds of question:
 * was the simulation <em>valid</em> the whole way through, and did the agent do
 * the things an autonomous agent is supposed to be able to do.
 *
 * <p>Validity is checked on every single tick, not just at the end - a value
 * that escapes its range for one tick and wanders back would pass an
 * end-of-run check and still be a bug.
 */
class AutonomousSimulationTest {

    /** Twenty simulated days at ten minutes a tick. */
    private static final long LONG_RUN = 2880L;

    /** The most energy one tick's action can spend at the default settings: a step, or a gather. */
    private static final double ONE_TICK_OF_WORK = 1.0;

    /**
     * Watches every tick and writes down anything that should not be possible.
     *
     * <p>Holds the live agent as well as reading snapshots, because memory,
     * knowledge and skills are not in a snapshot and have invariants too.
     */
    private static final class Auditor implements SimulationObserver {
        final List<String> violations = new ArrayList<>();
        final List<SimulationEvent> events = new ArrayList<>();
        final Set<String> succeeded = new HashSet<>();
        final List<String> repeatedMistakes = new ArrayList<>();

        private final Agent agent;
        private final Set<String> knownImpossible = new HashSet<>();
        private long lastTick;
        private AgentSnapshot atDeath;
        private int resourceCount = -1;
        private Action lastChosen;
        private final List<Location> path = new ArrayList<>();
        private int turnsInARow;
        private long lastStepTick = -1;
        private double lastHealth = Needs.MAX;
        double lowestHealth = Needs.MAX;
        int longestDithering;
        long sleepTicks;
        // A meal is a sitting, not a mouthful: four bites in a row is one meal
        // however long a tick is, and counting mouthfuls made the same day look
        // different at ten minutes a tick and at thirty.
        private long lastMealTick = Long.MIN_VALUE;
        private long lastDrinkTick = Long.MIN_VALUE;
        int sleeps;
        int meals;
        int drinks;
        String ditheringSeen = "";

        Auditor(Agent agent) {
            this.agent = agent;
        }

        @Override
        public void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
            lastChosen = chosen.action();
            if (chosen.action().name().equals(SleepAction.NAME)) {
                sleepTicks += chosen.action().durationTicks();
                sleeps++;
            }
            // Having been told something is impossible, choosing it again would
            // mean experience changed nothing.
            if (knownImpossible.contains(signature(chosen.action()))) {
                repeatedMistakes.add("t" + tick + " tried again: " + signature(chosen.action()));
            }
        }

        @Override
        public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
            if (outcome.succeeded()) {
                succeeded.add(outcome.actionName());
                if (outcome.actionName().equals(EatAction.NAME)) {
                    if (tick != lastMealTick + 1) {
                        meals++;
                    }
                    lastMealTick = tick;
                }
                if (outcome.actionName().equals(DrinkAction.NAME)) {
                    if (tick != lastDrinkTick + 1) {
                        drinks++;
                    }
                    lastDrinkTick = tick;
                }
            }
            if (outcome.wasImpossible() && lastChosen != null) {
                knownImpossible.add(signature(lastChosen));
            }
        }

        @Override
        public void onEvent(SimulationEvent event) {
            events.add(event);
        }

        @Override
        public void onTickCompleted(WorldState state) {
            long tick = state.tick();
            check(tick == lastTick + 1, tick, "time skipped from t" + lastTick);
            lastTick = tick;

            checkResources(state, tick);
            for (AgentSnapshot snapshot : state.agents()) {
                checkBody(snapshot, state, tick);
            }
            checkMind(tick);
        }

        private void checkResources(WorldState state, long tick) {
            // No fixed tally: a snapshot of an endless world carries the ground
            // around the agent, and there is more of it as the agent walks into
            // country that did not exist before. What must hold is that every
            // node in view is a sane node in a real place.
            resourceCount = Math.max(resourceCount, state.resources().size());
            for (ResourceSnapshot node : state.resources()) {
                check(node.quantity() >= 0.0, tick, "negative resource " + node);
                check(node.quantity() <= node.capacity() + 1e-9, tick, "overfull resource " + node);
                check(state.bounds().contains(node.location()), tick, "resource off the map " + node);
            }
        }

        private void checkBody(AgentSnapshot snapshot, WorldState state, long tick) {
            Needs needs = snapshot.needs();
            for (double value : new double[] {needs.hunger(), needs.thirst(), needs.energy(), needs.health()}) {
                check(value >= Needs.MIN && value <= Needs.MAX, tick, "need out of range " + needs);
            }
            check(state.bounds().contains(snapshot.location()), tick, "agent off the map");
            check(snapshot.inventory().total() <= snapshot.inventory().capacity() + 1e-9,
                    tick, "carrying more than it can " + snapshot.inventory());
            check(snapshot.inventory().contents().values().stream().allMatch(q -> q >= 0.0),
                    tick, "negative inventory " + snapshot.inventory());
            check(snapshot.alive() == (needs.health() > Needs.MIN), tick, "alive flag disagrees with health");

            if (!snapshot.alive()) {
                if (atDeath == null) {
                    atDeath = snapshot;
                } else {
                    // The dead do not move, carry things off, or get older.
                    check(snapshot.location().equals(atDeath.location()), tick, "a dead agent moved");
                    check(snapshot.inventory().equals(atDeath.inventory()), tick, "a dead agent's things changed");
                    check(snapshot.ticksAlive() == atDeath.ticksAlive(), tick, "a dead agent aged");
                }
            } else {
                check(snapshot.ticksAlive() == tick, tick,
                        "age " + snapshot.ticksAlive() + " is not the ticks lived");
            }
            checkIntent(snapshot, state, tick);
            checkHealing(snapshot, tick);
            followPath(snapshot, tick);
        }

        /** A body only mends when it is looked after, and never while something is hurting it. */
        private void checkHealing(AgentSnapshot snapshot, long tick) {
            Needs needs = snapshot.needs();
            // The body heals before the tick's action runs, and an action can
            // spend a little energy after it, so energy is allowed that margin.
            if (needs.health() > lastHealth) {
                check(needs.hunger() <= Needs.WELL_PRESSURE
                                && needs.thirst() <= Needs.WELL_PRESSURE
                                && needs.energy() >= Needs.WELL_ENERGY - ONE_TICK_OF_WORK,
                        tick, "healed while neglected " + needs);
            }
            lastHealth = needs.health();
            lowestHealth = Math.min(lowestHealth, needs.health());
        }

        private void checkIntent(AgentSnapshot snapshot, WorldState state, long tick) {
            check(snapshot.alive() || snapshot.intent().isEmpty(), tick, "the dead still intend");
            snapshot.intent().ifPresent(intent -> {
                check(state.bounds().contains(intent.target()), tick, "heading off the map " + intent);
                check(intent.formedAtTick() <= tick, tick, "an intent from the future " + intent);
                check(!intent.isReachedFrom(snapshot.location()), tick, "still heading for where it is " + intent);
            });
        }

        /**
         * Counts steps that undo the step before - A to B, then on the very
         * next tick straight back to A. One is a change of mind; several in a
         * row is dithering. Stepping back to gather from a bush just passed,
         * then carrying on, has work in between and is neither.
         */
        private void followPath(AgentSnapshot snapshot, long tick) {
            Location here = snapshot.location();
            if (!path.isEmpty() && path.get(path.size() - 1).equals(here)) {
                return;
            }
            boolean turnedBack = path.size() >= 2
                    && path.get(path.size() - 2).equals(here)
                    && lastStepTick == tick - 1;
            turnsInARow = turnedBack ? turnsInARow + 1 : 0;
            lastStepTick = tick;
            path.add(here);
            if (turnsInARow > longestDithering) {
                longestDithering = turnsInARow;
                ditheringSeen = "t" + snapshot.ticksAlive() + " "
                        + path.subList(Math.max(0, path.size() - 6), path.size());
            }
        }

        private void checkMind(long tick) {
            check(agent.memory().size() <= agent.memory().capacity(), tick, "memory outgrew itself");
            for (Belief belief : agent.knowledge().beliefs()) {
                check(belief.confidence() >= 0.0 && belief.confidence() < 1.0,
                        tick, "impossible confidence " + belief);
                check(Double.isFinite(belief.estimate()), tick, "non-finite belief " + belief);
            }
            double walking = agent.skills().effortMultiplier(MoveAction.NAME);
            check(walking > 0.0 && walking <= 1.0, tick, "skill made effort free or dearer");
        }

        private void check(boolean ok, long tick, String what) {
            if (!ok && violations.size() < 20) {
                violations.add("t" + tick + ": " + what);
            }
        }

        private static String signature(Action action) {
            return action.name() + action.concerns().map(type -> " " + type.label()).orElse("");
        }

        <T extends SimulationEvent> long count(Class<T> kind) {
            return events.stream().filter(kind::isInstance).count();
        }
    }

    private record Run(Simulation simulation, Agent agent, Auditor auditor, long ticksRun) {
    }

    private Run runAutonomously(long seed, long ticks) {
        return runAutonomously(SimulationConfig.defaults(seed), ticks);
    }

    private Run runAutonomously(SimulationConfig config, long ticks) {
        return runAutonomously(config, Needs.healthy(), ticks);
    }

    private Run runAutonomously(SimulationConfig config, Needs startingAs, long ticks) {
        World world = WorldGenerator.forConfig(config);
        Agent agent = new Agent(
                AgentId.of("agent-1"), "Atlas", config.bounds().center(), startingAs);
        world.addAgent(agent);
        Simulation simulation =
                new Simulation(world, DeliberativeDecisionMaker.from(config), config);
        Auditor auditor = new Auditor(agent);
        simulation.addObserver(auditor);
        long ran = simulation.run(ticks);
        return new Run(simulation, agent, auditor, ran);
    }

    // --- validity ---------------------------------------------------------

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L})
    @DisplayName("twenty simulated days, every invariant holding on every tick")
    void remainsValidThroughoutALongRun(long seed) {
        Run run = runAutonomously(seed, LONG_RUN);

        assertEquals(List.of(), run.auditor().violations, "invariants broken for seed " + seed);
        assertTrue(run.auditor().longestDithering < 2,
                "walked back and forth " + run.auditor().longestDithering + " times in a row: "
                        + run.auditor().ditheringSeen);
        assertTrue(run.ticksRun() > 0);
        assertTrue(
                run.ticksRun() == LONG_RUN || !run.simulation().anyoneAlive(),
                "a run may only stop early because nobody is left alive");
    }

    @Test
    @DisplayName("a long run is exactly reproducible, event for event")
    void longRunsAreDeterministic() {
        Run first = runAutonomously(7L, LONG_RUN);
        Run second = runAutonomously(7L, LONG_RUN);

        assertEquals(
                first.auditor().events.stream().map(SimulationEvent::describe).toList(),
                second.auditor().events.stream().map(SimulationEvent::describe).toList());
        assertEquals(first.simulation().snapshot(), second.simulation().snapshot());
        assertEquals(first.agent().knowledge(), second.agent().knowledge());
        assertEquals(first.agent().skills(), second.agent().skills());
    }

    @Test
    @DisplayName("seed 10, which starved dithering between two bushes, now lives out the run")
    void noLongerStarvesBetweenTwoBushes() {
        // The MVP's one known death: at t300 Atlas stood between two berry
        // bushes, turning back towards whichever was out of sight, and died of
        // hunger at t358.
        Run run = runAutonomously(
                SimulationConfig.defaults(10L).withTickDuration(Duration.ofMinutes(60)),
                2000L);

        assertTrue(run.agent().isAlive(), "died after " + run.agent().ticksAlive() + " ticks");
        assertEquals(2000L, run.ticksRun());
        assertEquals(List.of(), run.auditor().violations);
        assertTrue(run.auditor().longestDithering < 2,
                "walked back and forth " + run.auditor().longestDithering + " times in a row: "
                        + run.auditor().ditheringSeen);
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L})
    @DisplayName("a close call is no longer for life: starting badly hurt, it mends and lives on")
    void recoversFromACloseCall(long seed) {
        Run run = runAutonomously(
                SimulationConfig.defaults(seed), new Needs(0.0, 0.0, 100.0, 30.0), LONG_RUN);

        assertEquals(List.of(), run.auditor().violations);
        assertTrue(run.agent().isAlive(), "died after " + run.agent().ticksAlive() + " ticks");
        assertTrue(run.agent().needs().health() >= 95.0, "still hurt: " + run.agent().needs());
        long recoveries = run.auditor().count(SimulationEvent.Recovered.class);
        long dangers = run.auditor().count(SimulationEvent.DangerBegan.class);
        assertTrue(recoveries >= 1 && recoveries <= dangers + 1,
                recoveries + " recoveries for " + dangers + " close calls");
    }

    @Test
    @DisplayName("a newborn works out what food is before hunger can hurt it")
    void theFirstWeekIsSafe() {
        // Seed 42 is the runner's default. When needs rose by the tick, a
        // newborn starved on day one while it learned berries were food. With a
        // realistic body it has days, and it uses them.
        SimulationConfig config = SimulationConfig.defaults(42L);
        Run run = runAutonomously(config, 7 * 144L);

        assertEquals(Needs.MAX, run.auditor().lowestHealth, 1e-9, "never hurt");
        assertEquals(0L, run.auditor().count(SimulationEvent.DangerBegan.class));
        assertFalse(run.agent().knowledge()
                        .believe(dev.evoworld.core.knowledge.BeliefKind.NOURISHMENT, ResourceType.BERRIES)
                        .isAssumed(),
                "should have tasted berries by now");
        assertEquals(List.of(), run.auditor().violations);
    }

    /** A day in a run, averaged: what the body asked of it. */
    private record Rhythm(double sleepHours, double sleeps, double meals, double drinks) {

        static Rhythm of(Run run, Duration tick) {
            double days = run.ticksRun() * tick.getSeconds() / 86_400.0;
            double hours = run.auditor().sleepTicks * tick.getSeconds() / 3_600.0;
            return new Rhythm(hours / days, run.auditor().sleeps / days,
                    run.auditor().meals / days, run.auditor().drinks / days);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L})
    @DisplayName("a day like a person's: one night's sleep, a few meals and drinks, and no danger")
    void livesARealisticDay(long seed) {
        Run run = runAutonomously(seed, LONG_RUN);
        Rhythm day = Rhythm.of(run, SimulationConfig.DEFAULT_TICK_DURATION);

        assertEquals(List.of(), run.auditor().violations);
        assertEquals(0L, run.auditor().count(SimulationEvent.DangerBegan.class), "never in danger");
        assertTrue(day.sleepHours() >= 7.0 && day.sleepHours() <= 10.0, "sleep " + day);
        assertTrue(day.sleeps() >= 0.9 && day.sleeps() <= 1.5, "about one night a day " + day);
        // Sittings a day, not mouthfuls: a big meal and sometimes a second,
        // and a couple of stops at water.
        assertTrue(day.meals() >= 1.0 && day.meals() <= 4.0, "meals " + day);
        assertTrue(day.drinks() >= 1.5 && day.drinks() <= 6.0, "drinks " + day);
    }

    @Test
    @DisplayName("the day looks the same whether time is cut into 10- or 30-minute ticks")
    void theRhythmDoesNotDependOnTheTickLength() {
        Duration fine = Duration.ofMinutes(10);
        Duration coarse = Duration.ofMinutes(30);
        double[] atFine = new double[3];
        double[] atCoarse = new double[3];
        for (long seed = 1; seed <= 4; seed++) {
            Rhythm a = Rhythm.of(runAutonomously(SimulationConfig.defaults(seed).withTickDuration(fine),
                    Needs.healthy(), 20 * 144L), fine);
            Rhythm b = Rhythm.of(runAutonomously(SimulationConfig.defaults(seed).withTickDuration(coarse),
                    Needs.healthy(), 20 * 48L), coarse);
            atFine[0] += a.sleepHours();
            atFine[1] += a.meals();
            atFine[2] += a.drinks();
            atCoarse[0] += b.sleepHours();
            atCoarse[1] += b.meals();
            atCoarse[2] += b.drinks();
        }
        String[] what = {"sleep hours", "meals", "drinks"};
        for (int i = 0; i < 3; i++) {
            double ratio = atCoarse[i] / atFine[i];
            assertTrue(ratio > 0.75 && ratio < 1.25,
                    what[i] + " a day: " + atFine[i] / 4 + " at 10 minutes, " + atCoarse[i] / 4 + " at 30");
        }
    }

    // --- capability -------------------------------------------------------

    @Test
    @DisplayName("left alone, the agent survives, forages, drinks, sleeps, explores, learns and improves")
    void doesEverythingAnAutonomousAgentShould() {
        Run run = runAutonomously(7L, LONG_RUN);
        Agent atlas = run.agent();
        Auditor seen = run.auditor();

        // surviving
        assertTrue(atlas.isAlive(), "should survive twenty days: " + atlas);
        assertEquals(LONG_RUN, atlas.ticksAlive());

        // moving, gathering, eating, drinking, resting
        for (String action : List.of(
                MoveAction.NAME, GatherAction.NAME, EatAction.NAME, DrinkAction.NAME, SleepAction.NAME)) {
            assertTrue(seen.succeeded.contains(action), "never managed to " + action);
        }

        // exploring
        assertTrue(atlas.exploration().regionsVisited() >= 9,
                "should have covered ground: " + atlas.exploration());

        // remembering discoveries
        assertTrue(seen.count(SimulationEvent.Discovered.class) > 0);
        assertTrue(atlas.memory().contents().stream().anyMatch(m -> m.kind() == MemoryKind.DISCOVERY),
                "discoveries should be among what it remembers");

        // learning basic facts - and learning them right
        assertTrue(seen.count(SimulationEvent.Learned.class) > 0);
        assertEquals(8.0, atlas.knowledge().estimate(
                dev.evoworld.core.knowledge.BeliefKind.NOURISHMENT, ResourceType.BERRIES), 1.0,
                "should have worked out roughly what berries are worth");
        assertEquals(0.0, atlas.knowledge().estimate(
                dev.evoworld.core.knowledge.BeliefKind.NOURISHMENT, ResourceType.STONE), 1e-9,
                "and that stone is worth nothing");

        // getting better at things
        assertTrue(seen.count(SimulationEvent.SkillImproved.class) > 0);
        assertTrue(atlas.skills().effortMultiplier(MoveAction.NAME) < 1.0);

        // a report every simulated day
        assertEquals(LONG_RUN / run.simulation().ticksPerDay(), seen.count(SimulationEvent.DayPassed.class));
    }

    @Test
    @DisplayName("twenty days take it well beyond the chunk it was born in, and the world keeps up")
    void aLifeCoversRealGround() {
        Run run = runAutonomously(7L, LONG_RUN);
        World world = run.simulation().world();
        Chunk born = Chunk.containing(world.bounds().center());
        Chunk ended = Chunk.containing(run.agent().location());

        assertTrue(world.chunkCount() > 1, "the world should have grown as it walked");
        assertNotEquals(born, ended, "a life should not fit in one chunk any more: " + ended);
        assertTrue(
                run.agent().exploration().regionsVisited() > 0,
                "and it should remember the country it crossed");
    }

    @Test
    @DisplayName("once told something is impossible, the agent never tries it again")
    void experienceChangesFutureDecisions() {
        for (long seed = 1L; seed <= 8L; seed++) {
            Run run = runAutonomously(seed, LONG_RUN);
            assertEquals(List.of(), run.auditor().repeatedMistakes,
                    "seed " + seed + " repeated a lesson it had already been taught");
        }
    }

    @Test
    @DisplayName("the event stream is meaningful, not a log of every calculation")
    void eventsAreRareAndMeaningful() {
        Run run = runAutonomously(7L, LONG_RUN);
        List<SimulationEvent> events = run.auditor().events;

        assertFalse(events.isEmpty());
        assertTrue(events.get(0) instanceof SimulationEvent.Born, "a run opens with an arrival");
        assertTrue(events.size() < LONG_RUN / 10,
                events.size() + " events in " + LONG_RUN + " ticks is chatter, not narration");
        assertEquals(1, run.auditor().count(SimulationEvent.Born.class));

        Optional<SimulationEvent> first = events.stream()
                .filter(e -> e instanceof SimulationEvent.Learned).findFirst();
        assertTrue(first.isPresent());
        assertTrue(first.get().describe().startsWith("Atlas learned"), first.get().describe());
    }

    @Test
    @DisplayName("a death, when it comes, is reported once and stops the run")
    void deathIsReportedAndEndsTheRun() {
        SimulationConfig config = SimulationConfig.defaults(3L);
        World world = WorldGenerator.forConfig(config);
        Agent doomed = new Agent(
                AgentId.of("agent-1"), "Atlas", config.bounds().center(),
                new Needs(Needs.MAX, Needs.MAX, 50.0, 10.0));
        world.addAgent(doomed);
        Simulation simulation = new Simulation(world, DeliberativeDecisionMaker.from(config), config);
        Auditor auditor = new Auditor(doomed);
        simulation.addObserver(auditor);

        long ran = simulation.run(LONG_RUN);

        assertTrue(ran < LONG_RUN, "should stop once nobody is alive");
        assertEquals(1, auditor.count(SimulationEvent.Died.class));
        assertTrue(auditor.count(SimulationEvent.DangerBegan.class) > 0,
                "the danger should have been reported before the death");
        assertEquals(List.of(), auditor.violations);
    }
}
