package dev.evoworld.core;

import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.perception.PerceptionSystem;
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
        int perceptionRadius,
        MetabolismRates metabolism,
        ActionSettings actions) {

    public static final Duration DEFAULT_TICK_DURATION = Duration.ofMinutes(10);

    public SimulationConfig {
        Objects.requireNonNull(tickDuration, "tickDuration");
        Objects.requireNonNull(bounds, "bounds");
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
                WorldBounds.of(16, 16),
                PerceptionSystem.DEFAULT_RADIUS,
                MetabolismRates.defaults(),
                ActionSettings.defaults());
    }

    public SimulationConfig withSeed(long newSeed) {
        return new SimulationConfig(
                newSeed,
                tickDuration,
                bounds,
                perceptionRadius,
                metabolism,
                actions);
    }

    public SimulationConfig withTickDuration(Duration newTickDuration) {
        return new SimulationConfig(
                seed,
                newTickDuration,
                bounds,
                perceptionRadius,
                metabolism,
                actions);
    }

    public SimulationConfig withBounds(WorldBounds newBounds) {
        return new SimulationConfig(
                seed,
                tickDuration,
                newBounds,
                perceptionRadius,
                metabolism,
                actions);
    }

    public SimulationConfig withActions(ActionSettings newActions) {
        return new SimulationConfig(
                seed, tickDuration, bounds, perceptionRadius, metabolism, newActions);
    }

    public SimulationConfig withPerceptionRadius(int newRadius) {
        return new SimulationConfig(
                seed,
                tickDuration,
                bounds,
                newRadius,
                metabolism,
                actions);
    }
}
