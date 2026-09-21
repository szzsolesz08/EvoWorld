package dev.evoworld.core.save;

import java.util.Objects;

/**
 * The configuration a saved run was started with.
 *
 * @param tickDuration ISO-8601, e.g. {@code PT10M}, so it reads back exactly
 */
public record SavedConfig(
        long seed,
        String tickDuration,
        int width,
        int height,
        int perceptionRadius,
        SavedMetabolism metabolism,
        SavedActionSettings actions) {

    public SavedConfig {
        Objects.requireNonNull(tickDuration, "tickDuration");
        Objects.requireNonNull(metabolism, "metabolism");
        Objects.requireNonNull(actions, "actions");
    }
}
