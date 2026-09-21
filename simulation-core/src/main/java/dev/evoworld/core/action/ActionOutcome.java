package dev.evoworld.core.action;

import java.util.Objects;

/**
 * What actually happened when an action was attempted.
 *
 * <p>Structured rather than a log line: the status says whether it happened,
 * the {@link AgentChange} says exactly what moved, and the detail is there for
 * a human reading the output. Later milestones will turn these into experiences
 * and memories without having to parse anything.
 */
public record ActionOutcome(
        String actionName,
        ActionStatus status,
        String detail,
        AgentChange change,
        boolean permanentlyImpossible) {

    public ActionOutcome {
        Objects.requireNonNull(actionName, "actionName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(change, "change");
        if (status == ActionStatus.BLOCKED && !change.isNothing()) {
            throw new IllegalArgumentException(
                    "a blocked action must leave the agent untouched, but " + actionName
                            + " changed it");
        }
    }

    static ActionOutcome succeeded(String actionName, String detail, AgentChange change) {
        return new ActionOutcome(actionName, ActionStatus.SUCCEEDED, detail, change, false);
    }

    static ActionOutcome blocked(String actionName, Refusal refusal, AgentChange unchanged) {
        return new ActionOutcome(
                actionName, ActionStatus.BLOCKED, refusal.reason(), unchanged, refusal.permanent());
    }

    /**
     * Whether the refusal was about how the world is rather than how it happens
     * to be. An agent told this once should stop asking.
     */
    public boolean wasImpossible() {
        return permanentlyImpossible;
    }

    public boolean succeeded() {
        return status.isSuccess();
    }

    public boolean wasBlocked() {
        return !succeeded();
    }

    @Override
    public String toString() {
        return actionName + (succeeded() ? " ok: " : " blocked: ") + detail;
    }
}
