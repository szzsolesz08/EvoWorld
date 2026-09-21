package dev.evoworld.core.goal;

import dev.evoworld.core.perception.Perception;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Weighs every goal an agent has against how it currently is.
 *
 * <p>The full set is scored every time rather than short-circuiting on the
 * first urgent one, because the ranking is the useful part: an agent that is
 * slightly hungry and desperately thirsty is in a different situation from one
 * that is only thirsty, even though both want water most.
 *
 * <p>Deterministic by construction. Every priority is a pure function of the
 * perception, and equal priorities keep the order the goals were registered in,
 * so the same state always produces the same ranking.
 */
public final class MotivationSystem {

    private final List<Goal> goals;

    public MotivationSystem(List<Goal> goals) {
        Objects.requireNonNull(goals, "goals");
        if (goals.isEmpty()) {
            throw new IllegalArgumentException("an agent needs at least one goal");
        }
        this.goals = List.copyOf(goals);
    }

    /**
     * The four wants of the MVP.
     *
     * <p>Registration order is the tie-break, so it reads as a standing
     * preference: when two wants are exactly equal, survival comes before
     * comfort and comfort before curiosity.
     */
    public static MotivationSystem standard() {
        return new MotivationSystem(List.of(
                new SatisfyThirstGoal(),
                new SatisfyHungerGoal(),
                new RecoverEnergyGoal(),
                new ExploreGoal()));
    }

    public List<Goal> goals() {
        return goals;
    }

    /** What the agent wants, given what it can tell about itself and the world. */
    public Motivation assess(Perception perception) {
        Objects.requireNonNull(perception, "perception");
        // A stable sort, so ties fall back on registration order rather than
        // on whatever the sort happened to do.
        List<GoalPriority> ranked = goals.stream()
                .map(goal -> goal.assess(perception))
                .sorted(Comparator.comparingDouble(GoalPriority::priority).reversed())
                .toList();
        return new Motivation(ranked, ranked.get(0));
    }
}
