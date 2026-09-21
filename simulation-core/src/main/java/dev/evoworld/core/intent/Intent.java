package dev.evoworld.core.intent;

import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * A commitment to reach a place, for the sake of a goal.
 *
 * <p>Formed when an agent sets off somewhere, and what stops it turning round.
 * Without one, every tick is a fresh decision, and two targets in opposite
 * directions that score almost the same can each look better once the agent
 * steps towards the other - so it oscillates between them until it starves.
 * An intent makes carrying on worth a little more than switching.
 *
 * <p>Only a little: the commitment is weighed in the decision layer, not
 * obeyed. An intent to walk to berries does not survive the agent stopping
 * being hungry, because a want near zero stays near zero however loyal the
 * agent is.
 *
 * @param goalName the goal the trip serves
 * @param target where the agent is heading
 * @param formedAtTick when it set off
 * @param startingDistance how far away the target was then, in steps
 */
public record Intent(String goalName, Location target, long formedAtTick, int startingDistance) {

    /** Extra ticks allowed beyond a generous multiple of the distance. */
    private static final int SLACK_TICKS = 10;

    /** How many times the straight-line trip it may take before it counts as stuck. */
    private static final int PATIENCE_MULTIPLE = 3;

    public Intent {
        Objects.requireNonNull(goalName, "goalName");
        Objects.requireNonNull(target, "target");
        if (formedAtTick < 0) {
            throw new IllegalArgumentException("formed before time began: " + formedAtTick);
        }
        if (startingDistance < 0) {
            throw new IllegalArgumentException("distance must not be negative: " + startingDistance);
        }
    }

    /** Setting off now for somewhere, from here. */
    public static Intent formed(String goalName, Location target, Location from, long tick) {
        return new Intent(goalName, target, tick, from.manhattanDistanceTo(target));
    }

    /** Whether this is the same undertaking: the same goal, the same place. */
    public boolean isFor(String otherGoal, Location otherTarget) {
        return goalName.equals(otherGoal) && target.equals(otherTarget);
    }

    public boolean isReachedFrom(Location where) {
        return target.equals(where);
    }

    /**
     * Whether the trip has gone on far longer than it should have.
     *
     * <p>A guard, not a strategy: a straight walk takes {@code startingDistance}
     * ticks, pauses to rest or eat take some more, and three times the distance
     * plus slack is well past anything a healthy trip needs. Beyond it the agent
     * is stuck, and holding on would only keep it stuck.
     */
    public boolean isStale(long now) {
        return now - formedAtTick > (long) startingDistance * PATIENCE_MULTIPLE + SLACK_TICKS;
    }

    public long ticksUnderway(long now) {
        return Math.max(0L, now - formedAtTick);
    }

    /** "heading for (2,8) to satisfy thirst" */
    public String describe() {
        return "heading for " + target + " to "
                + goalName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    public String toString() {
        return describe();
    }
}
