package dev.evoworld.core;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Exertion;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DecisionMaker;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.event.SimulationEvent.Danger;
import dev.evoworld.core.goal.GoalPriority;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.goal.MotivationSystem;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.knowledge.Belief;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.knowledge.KnowledgeLearner;
import dev.evoworld.core.memory.MemoryRecorder;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.save.SaveFormat;
import dev.evoworld.core.save.SavedAgent;
import dev.evoworld.core.save.SavedSimulation;
import dev.evoworld.core.skill.SkillLevel;
import dev.evoworld.core.status.AgentStatus;
import dev.evoworld.core.skill.Skills;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the world forward one tick at a time.
 *
 * <p>A tick runs fifteen phases in a fixed order, and {@link #tick()} is
 * written to read as that list:
 *
 * <ol>
 *   <li>advance world time
 *   <li>update the environment - resources regrow
 *   <li>update each agent's needs for the tick just lived
 *   <li>perceive the environment
 *   <li>retrieve relevant memories - recalled as part of what is perceived
 *   <li>update goals
 *   <li>generate candidate actions
 *   <li>evaluate them
 *   <li>select one
 *   <li>execute it
 *   <li>process the consequences
 *   <li>create memories
 *   <li>update knowledge
 *   <li>update skills
 *   <li>report what was worth reporting
 * </ol>
 *
 * <p>The world and the body change <em>before</em> the mind looks at them, so
 * an agent always decides on the world as it now is. One consequence: an agent
 * whose needs killed it in the night does not get to act in the morning.
 *
 * <p>Phases 7 to 9 are the decision maker's; this class calls it once and does
 * not know how it thinks. Everything else is orchestration, and the phases are
 * private methods named for what they do.
 *
 * <p>The simulation derives its clock and its random stream from the
 * configuration rather than accepting them ready-made, which is what makes
 * "same config, same run" true by construction.
 */
public final class Simulation {

    private static final Logger log = LoggerFactory.getLogger(Simulation.class);

    private final SimulationConfig config;
    private final World world;
    private final SimulationClock clock;
    private final SimulationRandom random;
    private final PerceptionSystem senses;
    private final MotivationSystem motivations;
    private final DecisionMaker decisionMaker;
    private final List<SimulationObserver> observers = new ArrayList<>();
    private final long ticksPerDay;

    // Per-agent working state. Insertion-ordered so that iterating it could
    // never make a run depend on hash codes.

    /** What each agent is part-way through. */
    private final Map<AgentId, ActionInProgress> underway = new LinkedHashMap<>();

    /** How hard each agent worked last tick, which is what the tick cost it. */
    private final Map<AgentId, Exertion> lastActivity = new LinkedHashMap<>();

    /** Which of its needs are currently doing an agent damage. */
    private final Map<AgentId, Set<Danger>> dangers = new LinkedHashMap<>();

    /**
     * What each agent last wanted and last chose, kept only so a status display
     * can say. Not saved: it is recomputed at the agent's next decision, and
     * nothing the simulation does depends on it.
     */
    private final Map<AgentId, GoalPriority> lastWant = new LinkedHashMap<>();
    private final Map<AgentId, Decision> lastDecision = new LinkedHashMap<>();

    private boolean announced;

    /** The configured body, per hour, converted once to what one tick does to it. */
    private final MetabolismRates bodyPerTick;

    /** Whether {@link #start()} has been called; not saved, it only guards the call. */
    private boolean steppingStarted;

    public Simulation(World world, DecisionMaker decisionMaker, SimulationConfig config) {
        this(
                world,
                decisionMaker,
                config,
                new SimulationClock(Objects.requireNonNull(config, "config").tickDuration()),
                SimulationRandom.seededWith(config.seed()));
    }

    /** The shared path for a fresh run and a resumed one. */
    private Simulation(
            World world,
            DecisionMaker decisionMaker,
            SimulationConfig config,
            SimulationClock clock,
            SimulationRandom random) {
        this.world = Objects.requireNonNull(world, "world");
        this.decisionMaker = Objects.requireNonNull(decisionMaker, "decisionMaker");
        this.config = Objects.requireNonNull(config, "config");
        if (!world.bounds().equals(config.bounds())) {
            throw new IllegalArgumentException(
                    "world is " + world.bounds() + " but the configuration says " + config.bounds());
        }
        this.clock = clock;
        this.random = random;
        this.senses = new PerceptionSystem(config.perceptionRadius());
        this.motivations = MotivationSystem.standard();
        this.ticksPerDay = Math.max(1L, Duration.ofDays(1).dividedBy(config.tickDuration()));
        this.bodyPerTick = config.metabolism().perTick(config.tickDuration());
    }

    public void addObserver(SimulationObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public SimulationConfig config() {
        return config;
    }

    public World world() {
        return world;
    }

    public long currentTick() {
        return clock.currentTick();
    }

    public SimulationTime time() {
        return clock.now();
    }

    /** How many ticks make a simulated day at this configuration's tick duration. */
    public long ticksPerDay() {
        return ticksPerDay;
    }

    /** How many random values this run has consumed - a cheap fingerprint of the run. */
    public long randomDrawCount() {
        return random.drawCount();
    }

    public WorldState snapshot() {
        return world.snapshot(clock.now());
    }

    public boolean anyoneAlive() {
        return world.agents().stream().anyMatch(Agent::isAlive);
    }

    /**
     * Runs until {@code ticks} have passed or every agent is dead.
     *
     * @return the number of ticks actually processed
     */
    public long run(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative, was " + ticks);
        }
        begin();
        log.info("simulation started seed={} ticks={} world={} resources={} tickDuration={}",
                config.seed(), ticks, world.bounds(), world.resources().size(),
                config.tickDuration());

        long processed = 0;
        String reason = "reached the requested tick count";
        for (long i = 0; i < ticks; i++) {
            if (!anyoneAlive()) {
                reason = "no agents left alive";
                break;
            }
            tick();
            processed++;
        }

        WorldState finalState = snapshot();
        log.info("simulation stopped after {} ticks ({} simulated): {}",
                processed, clock.elapsed(), reason);
        String stopReason = reason;
        notifyAll(observer -> observer.onSimulationStopped(finalState, stopReason));
        return processed;
    }

    /**
     * Gets a simulation ready to be stepped by hand, one {@link #tick()} at a
     * time, the way a live viewer drives it.
     *
     * <p>Does what {@link #run} does before its first tick: tells observers the
     * run has started and announces who is in the world. Only the first call
     * does anything, so a viewer may call it without keeping track.
     */
    public void start() {
        if (steppingStarted) {
            return;
        }
        steppingStarted = true;
        begin();
    }

    private void begin() {
        notifyAll(observer -> observer.onSimulationStarted(snapshot()));
        announceArrivals();
    }

    // --- status -----------------------------------------------------------

    /** How many memories a status mentions; the rest are there but not news. */
    private static final int HIGHLIGHTED_MEMORIES = 5;

    /**
     * How every agent is doing, as immutable values an observer may keep.
     *
     * <p>Reading it changes nothing - recall is a question, not an edit - so a
     * display can ask as often as it likes without touching the run.
     */
    public List<AgentStatus> status() {
        SimulationTime now = clock.now();
        List<AgentStatus> report = new ArrayList<>();
        for (Agent agent : world.agents()) {
            ActionInProgress busy = underway.get(agent.id());
            report.add(new AgentStatus(
                    now,
                    agent.snapshot(),
                    Optional.ofNullable(lastWant.get(agent.id())),
                    Optional.ofNullable(lastDecision.get(agent.id())),
                    busy == null ? 0L : busy.ticksRemaining(),
                    agent.memory().highlights(now, agent.location(), HIGHLIGHTED_MEMORIES),
                    agent.knowledge().beliefs().stream()
                            .filter(belief -> !belief.isAssumed())
                            .toList(),
                    agent.skills(),
                    agent.exploration().visitedInView(agent.location()),
                    agent.exploration().regionsInView(agent.location()),
                    PerceptionSystem.plentyAround(agent.exploration(), agent.location(), now)));
        }
        return List.copyOf(report);
    }

    // --- saving and loading -----------------------------------------------

    /**
     * Everything needed to carry on from exactly here.
     *
     * <p>Taken between ticks; nothing is half-done at that point except
     * multi-tick actions, and those are saved with how much of them is left.
     * The decision maker is not saved: the standard one is stateless, so
     * rebuilding it from the configuration gives back the same mind.
     */
    public SavedSimulation save() {
        List<SavedAgent> agents = new ArrayList<>();
        for (Agent agent : world.agents()) {
            ActionInProgress current = underway.get(agent.id());
            agents.add(SaveFormat.save(
                    agent,
                    current == null ? null : current.action(),
                    current == null ? 0L : current.ticksRemaining(),
                    lastActivity.getOrDefault(agent.id(), Exertion.of(ActivityLevel.RESTING)),
                    List.copyOf(dangers.getOrDefault(agent.id(), EnumSet.noneOf(Danger.class)))));
        }
        return new SavedSimulation(
                SavedSimulation.FORMAT_VERSION,
                SaveFormat.save(config),
                clock.currentTick(),
                random.state(),
                random.drawCount(),
                announced,
                world.generatesItself() ? List.of() : world.resources().stream().map(SaveFormat::save).toList(),
                world.generatesItself() ? SaveFormat.saveChunks(world, config) : List.of(),
                agents);
    }

    /** A saved simulation, carrying on with the standard autonomous mind. */
    public static Simulation load(SavedSimulation saved) {
        return load(saved, DeliberativeDecisionMaker.from(SaveFormat.load(saved.config())));
    }

    /**
     * A saved simulation, carrying on exactly where it stopped.
     *
     * <p>Run for n more ticks, it does precisely what the original would have
     * done over its next n ticks - same choices, same random draws, same
     * events. There are tests that hold it to that.
     */
    public static Simulation load(SavedSimulation saved, DecisionMaker decisionMaker) {
        Objects.requireNonNull(saved, "saved");
        if (saved.formatVersion() != SavedSimulation.FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "cannot read save format " + saved.formatVersion()
                            + "; this version reads format " + SavedSimulation.FORMAT_VERSION);
        }
        SimulationConfig config = SaveFormat.load(saved.config());

        World world = WorldGenerator.forConfig(config);
        if (world.generatesItself()) {
            SaveFormat.restoreChunks(world, saved.chunks(), config);
        } else {
            saved.resources().forEach(resource -> world.addResource(SaveFormat.load(resource)));
        }
        for (SavedAgent agent : saved.agents()) {
            world.addAgent(SaveFormat.load(agent, config.bounds()));
        }

        Simulation simulation = new Simulation(
                world,
                decisionMaker,
                config,
                new SimulationClock(config.tickDuration(), saved.tick()),
                SimulationRandom.resumedAt(config.seed(), saved.randomState(), saved.randomDraws()));
        simulation.announced = saved.announced();
        for (SavedAgent agent : saved.agents()) {
            AgentId id = AgentId.of(agent.id());
            if (agent.underway() != null) {
                simulation.underway.put(
                        id,
                        new ActionInProgress(
                                SaveFormat.load(agent.underway()), agent.underwayTicksRemaining()));
            }
            simulation.lastActivity.put(id, new Exertion(agent.lastActivity(), agent.lastComfort()));
            Set<Danger> active = EnumSet.noneOf(Danger.class);
            active.addAll(agent.dangers());
            simulation.dangers.put(id, active);
        }
        return simulation;
    }

    // --- the tick ---------------------------------------------------------

    /** Advances the world by exactly one tick. */
    public void tick() {
        // 1. advance world time
        SimulationTime now = clock.advance();
        notifyAll(observer -> observer.onTickStarted(now.tick()));

        // 2. update the environment: ground near anybody keeps growing, and
        // ground nobody is near catches up when somebody comes back.
        List<Location> whereEveryoneIs = world.agents().stream()
                .filter(Agent::isAlive)
                .map(Agent::location)
                .toList();
        world.regenerateResources(
                config.tickDuration(), whereEveryoneIs, config.perceptionRadius(), now.tick());

        for (Agent agent : world.agents()) {
            if (!agent.isAlive()) {
                continue;
            }
            // 3. update needs for the tick just lived
            liveThroughTheTick(agent, now);
            if (!agent.isAlive()) {
                continue;
            }
            // 4 - 14. the agent perceives, decides, acts and learns
            act(agent, now);
        }

        // 15. report - the daily summary is the one event on a schedule
        if (now.tick() % ticksPerDay == 0) {
            reportTheDay(now);
        }

        WorldState state = snapshot();
        notifyAll(observer -> observer.onTickCompleted(state));
    }

    /**
     * Phase 3: the body pays for the tick that has just gone by.
     *
     * <p>Charged at the effort of what the agent was doing through it. Harm is
     * remembered, a need crossing into or out of doing damage is reported, and
     * an agent that does not survive stops here.
     */
    private void liveThroughTheTick(Agent agent, SimulationTime now) {
        Needs before = agent.needs();
        Exertion effort = lastActivity.getOrDefault(agent.id(), Exertion.of(ActivityLevel.RESTING));
        agent.live(bodyPerTick, effort);
        double healthLost = before.health() - agent.needs().health();
        MemoryRecorder.rememberHarm(
                agent.memory(), agent.location(), now, agent.needs(), healthLost);
        reportDangerChanges(agent, now);
        if (!before.isFullyHealthy() && agent.needs().isFullyHealthy()) {
            emit(new SimulationEvent.Recovered(
                    now, agent.id(), agent.name(), agent.needs().health()));
        }

        if (!agent.isAlive()) {
            // Nothing half-finished survives the agent that was doing it.
            underway.remove(agent.id());
            agent.giveUpIntent();
            String cause = causeOfDeath(agent.needs());
            log.info("tick {}: agent {} died of {}", now.tick(), agent.id(), cause);
            notifyAll(observer -> observer.onAgentDied(now.tick(), agent.id()));
            emit(new SimulationEvent.Died(
                    now, agent.id(), agent.name(), cause, agent.ticksAlive()));
        }
    }

    /** Phases 4 to 14 for one agent. */
    private void act(Agent agent, SimulationTime now) {
        ActionInProgress current = underway.get(agent.id());
        if (current == null) {
            // Free to choose only when not already busy with something.
            current = ActionInProgress.starting(decide(agent, now));
        }

        Action action = current.action();
        lastActivity.put(agent.id(), action.exertion());
        current = current.afterATick();
        if (!current.isFinished()) {
            underway.put(agent.id(), current);
            long remaining = current.ticksRemaining();
            notifyAll(observer -> observer.onActionContinued(
                    now.tick(), agent.id(), action.name(), remaining));
            return;
        }
        underway.remove(agent.id());

        // 10. execute - the precondition is checked again here, because a
        // multi-tick action can be overtaken by events and must then block.
        ActionOutcome outcome = action.execute(agent, world);
        // 11. process the consequences
        settleIntent(agent, now.tick());
        notifyAll(observer -> observer.onActionPerformed(now.tick(), agent.id(), outcome));
        // 12. create memories
        MemoryRecorder.rememberOutcome(agent.memory(), action, outcome, agent.location(), now);
        // 13. update knowledge
        learnFrom(agent, action, outcome, now);
        // 14. update skills
        practise(agent, action, outcome, now);
    }

    /**
     * Phases 4 to 9: perceive, recall, want, and choose.
     *
     * <p>Recall (phase 5) happens inside perception: remembered places arrive
     * as part of what the agent knows, beside what it can see. What it takes
     * in is filed immediately, so the decision is made on the world and the
     * memory of the world in the same state.
     */
    private Action decide(Agent agent, SimulationTime now) {
        long tick = now.tick();
        settleIntent(agent, tick);

        // The ground an agent is about to look at has to exist first. In an
        // endless world this is where it comes into being, a chunk at a time.
        world.ensureAround(agent.location(), config.perceptionRadius(), tick);

        // 4, 5. perceive, with relevant memories recalled alongside
        Perception perception = senses.perceive(agent, world, now);
        MemoryRecorder.rememberSightings(agent.memory(), perception);
        noticeDiscoveries(agent, perception);
        notifyAll(observer -> observer.onPerceived(tick, agent.id(), perception));

        // 6. update goals
        Motivation motivation = motivations.assess(perception);
        lastWant.put(agent.id(), motivation.dominant());
        notifyAll(observer -> observer.onMotivated(tick, agent.id(), motivation));

        // 7, 8, 9. candidates, evaluation, selection
        Deliberation deliberation = decisionMaker.deliberate(perception, motivation, random);
        lastDecision.put(agent.id(), deliberation.chosen());
        followThrough(agent, deliberation.chosen(), tick);
        notifyAll(observer -> observer.onDecision(
                tick, agent.id(), deliberation.chosen(), deliberation.considered()));
        return deliberation.chosen().action();
    }

    /**
     * An intent that has been fulfilled, or has dragged on far too long, is let
     * go. Checked as soon as an action lands, so nobody watching sees an agent
     * still heading for where it is standing, and again before deciding, so a
     * spent intent cannot tip a decision it no longer deserves to.
     */
    private static void settleIntent(Agent agent, long tick) {
        agent.intent()
                .filter(intent -> intent.isReachedFrom(agent.location()) || intent.isStale(tick))
                .ifPresent(intent -> agent.giveUpIntent());
    }

    /**
     * After choosing: heading somewhere new sets the agent out on a new intent;
     * heading where it was already going keeps the one it has. A choice that
     * heads nowhere - a rest, a meal on the way - is a pause, not a change of
     * mind, and leaves the intent where it was.
     */
    private static void followThrough(Agent agent, Decision chosen, long tick) {
        if (chosen.target().isEmpty() || chosen.goalName().isEmpty()) {
            return;
        }
        String goal = chosen.goalName().get();
        Location target = chosen.target().get();
        boolean carryingOn = agent.intent()
                .filter(intent -> intent.isFor(goal, target))
                .isPresent();
        if (!carryingOn) {
            agent.commitTo(Intent.formed(goal, target, agent.location(), tick));
        }
    }

    // --- consequences -----------------------------------------------------

    /**
     * Phase 13, and the one event it can raise: the first time a belief rests
     * on evidence rather than assumption.
     */
    private void learnFrom(Agent agent, Action action, ActionOutcome outcome, SimulationTime now) {
        Knowledge before = agent.knowledge();
        Knowledge after = KnowledgeLearner.learnFrom(before, action, outcome, agent.belongings());
        agent.updateKnowledge(after);
        for (Belief belief : after.beliefs()) {
            if (belief.observations() == 1
                    && before.believe(belief.kind(), belief.subject()).isAssumed()) {
                emit(new SimulationEvent.Learned(now, agent.id(), agent.name(), belief));
            }
        }
    }

    /** Phase 14: success at something trainable counts as practice. */
    private void practise(Agent agent, Action action, ActionOutcome outcome, SimulationTime now) {
        if (!action.trainable() || !outcome.succeeded()) {
            return;
        }
        Skills before = agent.skills();
        Skills after = before.practised(action.name());
        agent.updateSkills(after);
        SkillLevel was = before.level(action.name());
        SkillLevel reached = after.level(action.name());
        if (reached != was) {
            emit(new SimulationEvent.SkillImproved(
                    now, agent.id(), agent.name(), action.name(), reached));
        }
    }

    /**
     * Anything in sight the agent has not found here before.
     *
     * <p>Nobody wrote down a list of discoveries. A discovery is simply a
     * sighting that the agent's own coverage has no record of, so the set of
     * findable things is whatever the world happens to contain.
     */
    private void noticeDiscoveries(Agent agent, Perception perception) {
        long tick = perception.tick();
        for (PerceivedResource seen : perception.resources()) {
            agent.exploration()
                    .notice(seen.location(), seen.type(), perception.time())
                    .ifPresent(discovery -> {
                        MemoryRecorder.rememberDiscovery(agent.memory(), discovery);
                        notifyAll(observer -> observer.onDiscovery(tick, agent.id(), discovery));
                        emit(new SimulationEvent.Discovered(
                                perception.time(), agent.id(), agent.name(), discovery));
                    });
        }
    }

    // --- reporting --------------------------------------------------------

    /** Once, at the start of a run, for everyone already in the world. */
    private void announceArrivals() {
        if (announced) {
            return;
        }
        announced = true;
        for (Agent agent : world.agents()) {
            emit(new SimulationEvent.Born(clock.now(), agent.id(), agent.name(), agent.location()));
        }
    }

    /** Reports each need that has just started, or just stopped, doing damage. */
    private void reportDangerChanges(Agent agent, SimulationTime now) {
        Set<Danger> was = dangers.getOrDefault(agent.id(), EnumSet.noneOf(Danger.class));
        Set<Danger> is = dangersFrom(agent.needs());
        double health = agent.needs().health();
        for (Danger danger : Danger.values()) {
            if (is.contains(danger) && !was.contains(danger)) {
                emit(new SimulationEvent.DangerBegan(now, agent.id(), agent.name(), danger, health));
            } else if (!is.contains(danger) && was.contains(danger) && agent.isAlive()) {
                emit(new SimulationEvent.DangerPassed(now, agent.id(), agent.name(), danger, health));
            }
        }
        dangers.put(agent.id(), is);
    }

    private void reportTheDay(SimulationTime now) {
        long day = now.tick() / ticksPerDay;
        for (Agent agent : world.agents()) {
            if (!agent.isAlive()) {
                continue;
            }
            emit(new SimulationEvent.DayPassed(
                    now,
                    agent.id(),
                    agent.name(),
                    day,
                    agent.snapshot(),
                    (int) agent.knowledge().beliefs().stream()
                            .filter(belief -> !belief.isAssumed())
                            .count(),
                    agent.exploration().visitedInView(agent.location()),
                    agent.exploration().regionsInView(agent.location()),
                    agent.skills().toString()));
        }
    }

    private static Set<Danger> dangersFrom(Needs needs) {
        Set<Danger> found = EnumSet.noneOf(Danger.class);
        if (needs.isStarving()) {
            found.add(Danger.STARVING);
        }
        if (needs.isDehydrated()) {
            found.add(Danger.DEHYDRATED);
        }
        if (needs.isExhausted()) {
            found.add(Danger.EXHAUSTED);
        }
        return found;
    }

    private static String causeOfDeath(Needs needs) {
        if (needs.isDehydrated()) {
            return "thirst";
        }
        if (needs.isStarving()) {
            return "hunger";
        }
        if (needs.isExhausted()) {
            return "exhaustion";
        }
        return "its injuries";
    }

    private void emit(SimulationEvent event) {
        notifyAll(observer -> observer.onEvent(event));
    }

    private void notifyAll(Consumer<SimulationObserver> event) {
        for (SimulationObserver observer : observers) {
            event.accept(observer);
        }
    }
}
