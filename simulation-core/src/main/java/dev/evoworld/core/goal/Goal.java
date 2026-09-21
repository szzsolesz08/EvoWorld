package dev.evoworld.core.goal;

import dev.evoworld.core.perception.Perception;
import java.util.Objects;

/**
 * Something an agent wants.
 *
 * <p>A goal says <em>what</em>, never <em>how</em>. "Satisfy thirst" is a goal;
 * "walk to the spring at (3,8) and drink" is a plan, and plans are not this
 * layer's business. Nothing here looks at what actions exist, and nothing here
 * decides anything - a goal only reports how badly the agent wants it, given
 * how the agent currently is.
 *
 * <p>That separation is what keeps behaviour from being hardcoded. There is no
 * rule saying "if hungry, go to berries": there is a hunger that grows, a want
 * that grows with it, and - in a later milestone - actions that compete to
 * serve whatever the agent wants most.
 *
 * <p>Priority is a number between {@link #MIN_PRIORITY} and
 * {@link #MAX_PRIORITY}, so wants are comparable across goals that have nothing
 * else in common. {@link #assess} is final and does the clamping, so no goal can
 * shout its way to the front by returning a bigger number.
 */
public abstract class Goal {

    public static final double MIN_PRIORITY = 0.0;
    public static final double MAX_PRIORITY = 1.0;

    /** Short stable name, used in logs and, later, in memories. */
    public abstract String name();

    /**
     * How badly the agent wants this right now, from nothing to everything.
     *
     * <p>A pure function of the perception it is given: the same agent in the
     * same state always wants the same things to the same degree.
     */
    protected abstract double computePriority(Perception perception);

    /** Why, in words, for a human reading the log. */
    protected abstract String reasonFrom(Perception perception);

    /** This goal, scored against the agent's current state. */
    public final GoalPriority assess(Perception perception) {
        Objects.requireNonNull(perception, "perception");
        return new GoalPriority(
                this, clamp(computePriority(perception)), reasonFrom(perception));
    }

    public static double clamp(double priority) {
        return Math.min(MAX_PRIORITY, Math.max(MIN_PRIORITY, priority));
    }

    /**
     * Goals are stateless, so one instance of a kind is as good as another.
     * Goals that carry settings override this.
     */
    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return name();
    }
}
