package dev.evoworld.core.save;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What an average chunk of this world holds, and how much chunks vary, as
 * saved. Enough to build any chunk the run has not reached yet.
 *
 * @param average how many nodes of each resource, by name
 */
public record SavedChunkMix(Map<String, Integer> average, double leanest, double richest) {

    public SavedChunkMix {
        Objects.requireNonNull(average, "average");
        // Insertion order, not Map.copyOf: the JDK's immutable maps iterate
        // in an order they randomise per process, and a save file should not
        // depend on which process wrote it.
        average = Collections.unmodifiableMap(new LinkedHashMap<>(average));
    }
}
