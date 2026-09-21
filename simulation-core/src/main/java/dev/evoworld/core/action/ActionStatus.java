package dev.evoworld.core.action;

/**
 * How an attempted action ended.
 *
 * <p>Only two outcomes, and the distinction matters: {@link #BLOCKED} means the
 * world said no before anything happened, which is information an agent can
 * learn from. There is deliberately no "failed halfway" - an effect either runs
 * or it does not.
 */
public enum ActionStatus {

    /** The effect ran. */
    SUCCEEDED,

    /** A precondition was not met, so nothing changed. */
    BLOCKED;

    public boolean isSuccess() {
        return this == SUCCEEDED;
    }
}
