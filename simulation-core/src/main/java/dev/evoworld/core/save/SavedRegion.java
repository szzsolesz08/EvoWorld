package dev.evoworld.core.save;

import dev.evoworld.core.resource.ResourceType;
import java.util.List;

/** How recently a region was seen and what was found there. */
public record SavedRegion(int column, int row, long lastSeenTick, List<ResourceType> found) {

    public SavedRegion {
        found = List.copyOf(found);
    }
}
