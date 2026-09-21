package dev.evoworld.core.agent;

/**
 * How hard an agent is working this tick.
 *
 * <p>Being alive costs energy whatever you do; doing something costs more.
 * Every {@link dev.evoworld.core.action.Action} declares which level it
 * represents, so the body charges the right amount without knowing what the
 * action was.
 */
public enum ActivityLevel {

    /** Sitting still and recovering. */
    RESTING,

    /** Moving, gathering, or anything else that takes effort. */
    ACTIVE
}
