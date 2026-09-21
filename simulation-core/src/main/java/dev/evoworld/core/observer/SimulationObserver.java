package dev.evoworld.core.observer;

import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.exploration.Discovery;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.world.WorldState;
import java.util.List;

/**
 * Read-only window onto a running simulation.
 *
 * <p>Every callback receives immutable values only, so an observer can render,
 * log or record the simulation without being able to change it. The console
 * logger in the runner module is one implementation; a LibGDX viewer would be
 * another, and the simulation core would not need to know it exists.
 *
 * <p>All methods default to doing nothing so implementations subscribe only to
 * what they care about.
 */
public interface SimulationObserver {

    default void onSimulationStarted(WorldState state) {}

    default void onTickStarted(long tick) {}

    /** What an agent could tell about the world before it chose. */
    default void onPerceived(long tick, AgentId agentId, Perception perception) {}

    /** What an agent wants, weighed against how it currently is. */
    default void onMotivated(long tick, AgentId agentId, Motivation motivation) {}

    /** An agent found something it had not found before. */
    default void onDiscovery(long tick, AgentId agentId, Discovery discovery) {}

    /**
     * Something worth telling someone about.
     *
     * <p>The curated stream. Everything else on this interface is the working
     * of the simulation, reported for anyone debugging it; events are the
     * moments a narrator would mention. An observer that wants to watch a life
     * rather than a calculation listens here and nowhere else.
     */
    default void onEvent(SimulationEvent event) {}

    default void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {}

    default void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {}

    /** An action that takes more than a tick is still under way. */
    default void onActionContinued(
            long tick, AgentId agentId, String actionName, long ticksRemaining) {}

    default void onAgentDied(long tick, AgentId agentId) {}

    default void onTickCompleted(WorldState state) {}

    default void onSimulationStopped(WorldState state, String reason) {}
}
