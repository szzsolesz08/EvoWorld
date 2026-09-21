package dev.evoworld.core.memory;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * One thing an agent remembers.
 *
 * <p>Deliberately flat and readable. Every memory is a sentence an observer can
 * check - "at tick 41 I saw water at (3,8), and I was sure of it" - because a
 * memory an agent acts on that nobody can inspect is a bug waiting to be
 * untraceable.
 *
 * <p>Confidence is recorded as it was when the memory formed and then
 * <em>decays</em>: {@link #confidenceAt} halves it every
 * {@link MemoryKind#halfLifeTicks}. That is the whole model of forgetting. It
 * is arithmetic rather than a mechanism, so an agent's certainty about
 * something is a number anyone can recompute.
 *
 * <p>Every memory has a location. Everything in this world happens somewhere,
 * and requiring it means retrieval can always ask "how far is that from here".
 *
 * @param subject what it is about, as a stable key - a resource type's name, an
 *     action's name, the hazard that nearly did for the agent
 * @param importance how much this particular memory matters, which is what
 *     decides what gets forgotten when the store fills
 */
public record Memory(
        MemoryKind kind,
        SimulationTime formed,
        Location location,
        String subject,
        double confidence,
        double importance,
        String detail) {

    public Memory {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(formed, "formed");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(detail, "detail");
        requireFraction(confidence, "confidence");
        requireFraction(importance, "importance");
    }

    /** A memory formed now, fully believed, at this kind's usual importance. */
    public static Memory formed(
            MemoryKind kind, SimulationTime when, Location where, String subject, String detail) {
        return new Memory(kind, when, where, subject, 1.0, kind.baselineImportance(), detail);
    }

    public Memory withImportance(double newImportance) {
        return new Memory(kind, formed, location, subject, confidence, newImportance, detail);
    }

    /** How many ticks ago this happened. */
    public long ageAt(long tick) {
        return Math.max(0L, tick - formed.tick());
    }

    /**
     * How much the agent still believes this, having had time to forget.
     *
     * <p>Halves every {@link MemoryKind#halfLifeTicks}, so belief fades smoothly
     * rather than expiring, and an old memory of a spring is still worth more
     * than no memory at all.
     */
    public double confidenceAt(long tick) {
        long age = ageAt(tick);
        if (age == 0L) {
            return confidence;
        }
        return confidence * Math.pow(0.5, (double) age / kind.halfLifeTicks());
    }

    /** What this memory is worth keeping, used when something has to go. */
    public double valueAt(long tick) {
        return confidenceAt(tick) * importance;
    }

    /** The key a later memory of the same thing would overwrite. */
    MemoryKey key() {
        return new MemoryKey(kind, location, subject);
    }

    public String describeAt(long tick) {
        return String.format(
                Locale.ROOT,
                "%s at %s: %s (%d ticks ago, %.2f sure)",
                kind,
                location,
                detail,
                ageAt(tick),
                confidenceAt(tick));
    }

    private static void requireFraction(double value, String what) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(what + " must be within [0, 1], was " + value);
        }
    }

    @Override
    public String toString() {
        return kind + " " + subject + " at " + location + " (t" + formed.tick() + ")";
    }

    /** What makes two memories the same memory rather than two of them. */
    record MemoryKey(MemoryKind kind, Location location, String subject) {
    }
}
