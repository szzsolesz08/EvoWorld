package dev.evoworld.core.save;

import dev.evoworld.core.memory.MemoryKind;

/**
 * One memory, with when it formed.
 *
 * @param elapsed ISO-8601 simulated time since the run began, e.g. {@code PT6H40M}
 */
public record SavedMemory(
        MemoryKind kind,
        long tick,
        String elapsed,
        int x,
        int y,
        String subject,
        double confidence,
        double importance,
        String detail) {
}
