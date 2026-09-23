package dev.evoworld.core.save;

import dev.evoworld.core.resource.ResourceType;
import java.util.List;

/**
 * How recently a region was seen, what was found there, and how much of it.
 *
 * @param plenty the most of each kind seen at once, and when - what makes one
 *     remembered region a berry field and the next a bare slope
 */
public record SavedRegion(
        int column,
        int row,
        long lastSeenTick,
        List<ResourceType> found,
        List<SavedPlenty> plenty) {

    public SavedRegion {
        found = List.copyOf(found);
        plenty = List.copyOf(plenty);
    }

    public SavedRegion(int column, int row, long lastSeenTick, List<ResourceType> found) {
        this(column, row, lastSeenTick, found, List.of());
    }

    /** How much of one kind of thing was seen at once in a region, and when. */
    public record SavedPlenty(ResourceType type, int count, long tick) {
    }
}
