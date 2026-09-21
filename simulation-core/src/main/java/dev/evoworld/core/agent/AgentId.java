package dev.evoworld.core.agent;

import java.util.Objects;

/**
 * Stable identity of an agent, independent of its mutable state.
 */
public record AgentId(String value) {

    public AgentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("agent id must not be blank");
        }
    }

    public static AgentId of(String value) {
        return new AgentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
