package dev.evoworld.core.save;

import dev.evoworld.core.resource.ResourceType;
import java.util.Objects;

/** One resource node, as it stood. */
public record SavedResource(ResourceType type, int x, int y, double capacity, double quantity) {

    public SavedResource {
        Objects.requireNonNull(type, "type");
    }
}
