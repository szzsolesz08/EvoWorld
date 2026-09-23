package dev.evoworld.core.save;

import java.util.Objects;

/**
 * The run's settings, as saved.
 *
 * <p>The world is saved as its corners rather than a width and a height,
 * because an endless world has neither: it runs from the negative limit to the
 * positive one, and its ground is described by the chunk mix instead.
 */
public record SavedConfig(
        long seed,
        String tickDuration,
        int minX,
        int minY,
        int maxX,
        int maxY,
        SavedChunkMix chunks,
        int perceptionRadius,
        SavedMetabolism metabolism,
        SavedActionSettings actions) {

    public SavedConfig {
        Objects.requireNonNull(tickDuration, "tickDuration");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(metabolism, "metabolism");
        Objects.requireNonNull(actions, "actions");
    }
}
