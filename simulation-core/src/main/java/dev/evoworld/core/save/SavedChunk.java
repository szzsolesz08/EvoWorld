package dev.evoworld.core.save;

import java.util.List;
import java.util.Objects;

/**
 * A chunk of an endless world, as saved.
 *
 * <p>A chunk can be rebuilt from the run's seed and its own coordinates, so a
 * save does not carry its trees and springs - only that it exists, how far its
 * regrowth had got, and the handful of nodes an agent has changed since. A
 * hundred chunks of untouched country cost a hundred lines.
 *
 * @param column which chunk
 * @param row which chunk
 * @param regrownTo the tick its regrowth had been brought up to
 * @param changed the nodes that no longer hold what the terrain would give them
 */
public record SavedChunk(int column, int row, long regrownTo, List<SavedResource> changed) {

    public SavedChunk {
        Objects.requireNonNull(changed, "changed");
        changed = List.copyOf(changed);
    }
}
