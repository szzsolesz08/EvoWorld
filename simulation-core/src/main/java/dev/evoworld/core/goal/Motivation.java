package dev.evoworld.core.goal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Everything an agent wants right now, strongest first.
 *
 * <p>The whole set is kept, not just the winner, for the same reason
 * {@link dev.evoworld.core.decision.Deliberation} keeps the options it
 * rejected: what an agent nearly wanted explains its behaviour as much as what
 * it settled on, and a log that shows only the dominant goal hides how close
 * the race was.
 *
 * <p>Nothing here chooses an action. A motivation is a statement of wants; what
 * to do about them is the next layer's problem.
 */
public record Motivation(List<GoalPriority> goals, GoalPriority dominant) {

    public Motivation {
        Objects.requireNonNull(dominant, "dominant");
        goals = List.copyOf(goals);
        if (goals.isEmpty()) {
            throw new IllegalArgumentException("an agent must want at least one thing");
        }
        if (!goals.contains(dominant)) {
            throw new IllegalArgumentException("the dominant goal must be one of the goals weighed");
        }
    }

    /** What the agent wants most. */
    public Goal dominantGoal() {
        return dominant.goal();
    }

    public double dominantPriority() {
        return dominant.priority();
    }

    public Optional<GoalPriority> forGoal(String name) {
        return goals.stream().filter(scored -> scored.name().equals(name)).findFirst();
    }

    /** The priority of a named goal, or zero if the agent is not weighing it at all. */
    public double priorityOf(String name) {
        return forGoal(name).map(GoalPriority::priority).orElse(Goal.MIN_PRIORITY);
    }

    /** Goals the agent actually wants, in order. */
    public List<GoalPriority> wanted() {
        return goals.stream().filter(GoalPriority::isWanted).toList();
    }

    @Override
    public String toString() {
        return "wants " + goals.stream().map(GoalPriority::toString).collect(Collectors.joining(", "));
    }
}
