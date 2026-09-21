package dev.evoworld.runner;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.observer.SimulationObserver;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One line per action an agent completes, for the verbose level.
 *
 * <p>Between the event log's rare moments and the debug trace's every
 * calculation: what the agent actually did and how it went, without the
 * options it turned down.
 */
public final class ActionLog implements SimulationObserver {

    private static final Logger log = LoggerFactory.getLogger("evoworld.actions");

    private final Duration tickDuration;

    public ActionLog(Duration tickDuration) {
        this.tickDuration = tickDuration;
    }

    @Override
    public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
        SimulationTime when = new SimulationTime(tick, tickDuration.multipliedBy(tick));
        log.info("[{}] {} {}", EventLog.when(when), agentId, outcome);
    }
}
