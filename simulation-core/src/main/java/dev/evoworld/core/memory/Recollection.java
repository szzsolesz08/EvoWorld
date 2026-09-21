package dev.evoworld.core.memory;

import java.util.Locale;
import java.util.Objects;

/**
 * A memory that came back, and how strongly.
 *
 * <p>The relevance travels with the memory so a decision built on it can say
 * why that memory and not another one. Nothing about recall is hidden.
 */
public record Recollection(Memory memory, double relevance, double confidence) {

    public Recollection {
        Objects.requireNonNull(memory, "memory");
    }

    public MemoryKind kind() {
        return memory.kind();
    }

    public String subject() {
        return memory.subject();
    }

    public dev.evoworld.core.world.Location location() {
        return memory.location();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (relevance %.3f)", memory, relevance);
    }
}
