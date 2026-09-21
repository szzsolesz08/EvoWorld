package dev.evoworld.core.save;

import java.util.Objects;

/**
 * Where an agent had set out for, and since when.
 *
 * @param goalName the goal the trip serves
 * @param x where it was heading
 * @param y where it was heading
 * @param formedAtTick when it set off
 * @param startingDistance how far it had to go then, in steps
 */
public record SavedIntent(String goalName, int x, int y, long formedAtTick, int startingDistance) {

    public SavedIntent {
        Objects.requireNonNull(goalName, "goalName");
    }
}
