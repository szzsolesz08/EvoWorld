package dev.evoworld.core;

import dev.evoworld.core.action.Action;

/**
 * An action an agent has committed to but not yet finished.
 *
 * <p>Actions that take more than a tick occupy the agent: it keeps working
 * rather than reconsidering every tick, and the effect lands when the work is
 * done. That is what {@link Action#durationTicks()} means in practice.
 *
 * @param action         what the agent is doing
 * @param ticksRemaining how many more ticks it needs, including this one
 */
record ActionInProgress(Action action, long ticksRemaining) {

    static ActionInProgress starting(Action action) {
        return new ActionInProgress(action, action.durationTicks());
    }

    ActionInProgress afterATick() {
        return new ActionInProgress(action, ticksRemaining - 1);
    }

    boolean isFinished() {
        return ticksRemaining <= 0;
    }
}
