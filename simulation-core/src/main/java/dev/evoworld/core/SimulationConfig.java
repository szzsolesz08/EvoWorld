package dev.evoworld.core;

import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.world.ChunkMix;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.Objects;

/**
 * Everything that defines a run, in one place.
 *
 * <p>A run is reproducible as a whole configuration, not just a seed: the same
 * seed against a different world size or metabolism is a different run. Keeping
 * all of it in one immutable value is what lets a test assert
 * "same config, same result" in a single line, and what a save file will need
 * to record.
 *
 * @param seed           seed for all randomness in the run
 * @param tickDuration   how much simulated time one tick represents
 * @param bounds           size of the world grid
 * @param perceptionRadius how far an agent can sense, in steps
 * @param metabolism       how fast agents get hungry, thirsty and tired
 * @param actions          what each action does and costs
 */
public record SimulationConfig(
        long seed,
        Duration tickDuration,
        WorldBounds bounds,
        ChunkMix chunks,
        int perceptionRadius,
        MetabolismRates metabolism,
        ActionSettings actions) {

    public static final Duration DEFAULT_TICK_DURATION = Duration.ofMinutes(10);

    public SimulationConfig {
        Objects.requireNonNull(tickDuration, "tickDuration");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(metabolism, "metabolism");
        Objects.requireNonNull(actions, "actions");
        if (tickDuration.isZero() || tickDuration.isNegative()) {
            throw new IllegalArgumentException("tick duration must be positive, was " + tickDuration);
        }
        if (perceptionRadius < 0) {
            throw new IllegalArgumentException(
                    "perception radius must not be negative, was " + perceptionRadius);
        }
    }

    public static SimulationConfig defaults(long seed) {
        return new SimulationConfig(
                seed,
                DEFAULT_TICK_DURATION,
                WorldBounds.endless(),
                ChunkMix.standard(),
                PerceptionSystem.DEFAULT_RADIUS,
                MetabolismRates.defaults(),
                ActionSettings.defaults());
    }

    public SimulationConfig withSeed(long newSeed) {
        return new SimulationConfig(
                newSeed,
                tickDuration,
                bounds,
                chunks,
                perceptionRadius,
                metabolism,
                actions);
    }

    public SimulationConfig withTickDuration(Duration newTickDuration) {
        return new SimulationConfig(
                seed,
                newTickDuration,
                bounds,
                chunks,
                perceptionRadius,
                metabolism,
                actions);
    }

    public SimulationConfig withBounds(WorldBounds newBounds) {
        return new SimulationConfig(
                seed,
                tickDuration,
                newBounds,
                chunks,
                perceptionRadius,
                metabolism,
                actions);
    }

    public SimulationConfig withActions(ActionSettings newActions) {
        return new SimulationConfig(
                seed, tickDuration, bounds, chunks, perceptionRadius, metabolism, newActions);
    }

    /** The same configuration in a world of another shape, endless or walled. */
    public SimulationConfig withWorld(WorldBounds newBounds, ChunkMix newChunks) {
        return new SimulationConfig(
                seed, tickDuration, newBounds, newChunks, perceptionRadius, metabolism, actions);
    }

    public SimulationConfig withPerceptionRadius(int newRadius) {
        return new SimulationConfig(
                seed,
                tickDuration,
                bounds,
                chunks,
                newRadius,
                metabolism,
                actions);
    }
}
