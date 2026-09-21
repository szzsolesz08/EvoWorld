package dev.evoworld.runner;

import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.exploration.Discovery;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the simulation as a structured console log.
 *
 * <p>This is the MVP's entire "view layer". It lives in the runner, not in the
 * core, which is what keeps the core free of any presentation concern - a
 * future LibGDX viewer would sit exactly here and implement the same interface.
 *
 * <p>Long runs log per-tick detail only every {@code detailEvery} ticks, so a
 * simulated week stays readable. Filtering is a display choice: the simulation
 * runs identically either way, and deaths are always reported.
 */
public final class ConsoleSimulationObserver implements SimulationObserver {

    private static final Logger log = LoggerFactory.getLogger("evoworld.sim");

    private final long detailEvery;
    private final Duration tickDuration;

    public ConsoleSimulationObserver(long detailEvery, Duration tickDuration) {
        if (detailEvery < 1) {
            throw new IllegalArgumentException("detail interval must be at least 1");
        }
        this.detailEvery = detailEvery;
        this.tickDuration = tickDuration;
    }

    private boolean logsDetailFor(long tick) {
        return tick % detailEvery == 0;
    }

    /** How long an agent has lived, in simulated time rather than ticks. */
    private String ageOf(AgentSnapshot agent) {
        return describe(tickDuration.multipliedBy(agent.ticksAlive()));
    }

    /** Durations as days and hours, which reads better than ISO-8601 over a week. */
    private static String describe(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %dh", days, hours);
        }
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
        }
        return minutes + "m";
    }

    @Override
    public void onSimulationStarted(WorldState state) {
        log.info("world {} with {} agent(s)", state.bounds(), state.agents().size());
        state.agents().forEach(agent -> log.info(
                "  spawn {} ({}) at {} [{}] {}",
                agent.name(), agent.id(), agent.location(), agent.needs(), agent.inventory()));
    }

    @Override
    public void onPerceived(long tick, AgentId agentId, Perception perception) {
        if (!logsDetailFor(tick)) {
            return;
        }
        log.info("t{} {} {}", tick, agentId, perception);
    }

    @Override
    public void onMotivated(long tick, AgentId agentId, Motivation motivation) {
        if (!logsDetailFor(tick)) {
            return;
        }
        log.info("t{} {} {}", tick, agentId, motivation);
    }

    @Override
    public void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
        if (!logsDetailFor(tick)) {
            return;
        }
        String options = considered.stream().map(Decision::toString).collect(Collectors.joining(" | "));
        log.info("t{} {} considered: {}", tick, agentId, options);
        log.info("t{} {} chose {}", tick, agentId, chosen);
    }

    @Override
    public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
        if (!logsDetailFor(tick)) {
            return;
        }
        log.info("t{} {} {}", tick, agentId, outcome);
    }

    @Override
    public void onActionContinued(
            long tick, AgentId agentId, String actionName, long ticksRemaining) {
        if (!logsDetailFor(tick)) {
            return;
        }
        log.info("t{} {} still {} ({} tick(s) to go)", tick, agentId, actionName, ticksRemaining);
    }

    @Override
    public void onDiscovery(long tick, AgentId agentId, Discovery discovery) {
        // Always logged, whatever the detail interval: finding something is
        // the rarest thing that happens in a run.
        log.info("t{} {} DISCOVERED {}", tick, agentId, discovery.describe());
    }

    @Override
    public void onAgentDied(long tick, AgentId agentId) {
        log.warn("t{} {} died", tick, agentId);
    }

    @Override
    public void onTickCompleted(WorldState state) {
        if (!logsDetailFor(state.tick())) {
            return;
        }
        for (AgentSnapshot agent : state.agents()) {
            log.info(
                    "t{} [{}] {} at {} {} {} {}",
                    state.tick(),
                    describe(state.time().elapsed()),
                    agent.name(),
                    agent.location(),
                    agent.needs(),
                    agent.inventory(),
                    "age " + ageOf(agent));
        }
    }

    @Override
    public void onSimulationStopped(WorldState state, String reason) {
        log.info("stopped at {}: {}", state.time(), reason);
        for (AgentSnapshot agent : state.agents()) {
            log.info(
                    "  {} lived {} ({} ticks) and {}",
                    agent.name(),
                    ageOf(agent),
                    agent.ticksAlive(),
                    agent.alive() ? "is still going" : "is dead");
        }
    }
}
