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

    /** Sitting still: a breather, which recovers a little. */
    RESTING,

    /** Moving, gathering, or anything else that takes effort. */
    ACTIVE,

    /** Asleep: where energy comes back, and where a body mends fastest. */
    SLEEPING
}
