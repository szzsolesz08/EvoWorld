package dev.evoworld.core.memory;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import java.util.Objects;

/**
 * A question put to memory.
 *
 * <p>Always anchored to a moment and a place, because both are part of what
 * makes a memory relevant: an old one is worth less than a fresh one, and a far
 * one is worth less than a near one. The optional filters narrow what counts as
 * an answer at all.
 *
 * @param kind only this kind of memory, or null for any
 * @param subject only memories about this, or null for any
 * @param limit how many to return
 * @param minimumRelevance the floor below which a memory is not worth mentioning
 */
public record RecallQuery(
        SimulationTime now,
        Location from,
        MemoryKind kind,
        String subject,
        int limit,
        double minimumRelevance) {

    /** Below this, a memory is too faint or too far to be worth acting on. */
    public static final double DEFAULT_FLOOR = 0.01;

    public static final int DEFAULT_LIMIT = 8;

    public RecallQuery {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(from, "from");
        if (limit < 1) {
            throw new IllegalArgumentException("a query must ask for at least one memory");
        }
        if (minimumRelevance < 0.0) {
            throw new IllegalArgumentException("the relevance floor must not be negative");
        }
    }

    /** Anything worth remembering from here, right now. */
    public static RecallQuery from(SimulationTime now, Location where) {
        return new RecallQuery(now, where, null, null, DEFAULT_LIMIT, DEFAULT_FLOOR);
    }

    public RecallQuery ofKind(MemoryKind onlyThisKind) {
        return new RecallQuery(now, from, onlyThisKind, subject, limit, minimumRelevance);
    }

    public RecallQuery about(String onlyThisSubject) {
        return new RecallQuery(now, from, kind, onlyThisSubject, limit, minimumRelevance);
    }

    /** Memories about a resource, using the type's name as the subject key. */
    public RecallQuery about(ResourceType type) {
        return about(subjectFor(type));
    }

    public RecallQuery limitedTo(int newLimit) {
        return new RecallQuery(now, from, kind, subject, newLimit, minimumRelevance);
    }

    public RecallQuery atLeast(double floor) {
        return new RecallQuery(now, from, kind, subject, limit, floor);
    }

    /** The one place that decides how a resource is written down in a memory. */
    public static String subjectFor(ResourceType type) {
        return type.name();
    }

    boolean matches(Memory memory) {
        if (kind != null && memory.kind() != kind) {
            return false;
        }
        return subject == null || subject.equals(memory.subject());
    }
}
