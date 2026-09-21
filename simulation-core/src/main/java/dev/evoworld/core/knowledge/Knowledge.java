package dev.evoworld.core.knowledge;

import dev.evoworld.core.resource.ResourceType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an agent has worked out about how the world works.
 *
 * <p>The counterpart to {@link dev.evoworld.core.memory.MemoryStore}, and the
 * distinction is the whole point of this class existing:
 *
 * <ul>
 *   <li>Memory: <em>"I found berries at (4,3) an hour ago."</em> One episode,
 *       tied to a place and a time, which fades.
 *   <li>Knowledge: <em>"Berries are worth about eight hunger a unit."</em> A
 *       general claim, true wherever you meet a berry, which sharpens.
 * </ul>
 *
 * <p>Memory fades with time; knowledge does not. An agent that has not seen a
 * spring for a week is unsure it is still there, but it is not less sure that
 * water quenches thirst. That asymmetry is why these are two systems and not
 * one.
 *
 * <p>Immutable, like {@link dev.evoworld.core.agent.Inventory}: learning
 * produces a new body of knowledge and the agent replaces what it had. A
 * belief nobody has evidence for is not stored at all - asking about it returns
 * the prior, so an untested assumption costs nothing to hold.
 */
public final class Knowledge {

    private final Map<BeliefKey, Belief> beliefs;

    private Knowledge(Map<BeliefKey, Belief> beliefs) {
        this.beliefs = Collections.unmodifiableMap(beliefs);
    }

    /** An agent that has worked nothing out yet. */
    public static Knowledge innocent() {
        return new Knowledge(new LinkedHashMap<>());
    }

    /**
     * Exactly these beliefs, in this order - how a saved agent gets its mind
     * back. Rebuilding them by replaying observations would lose the running
     * mean's history, so they are restored as they were held.
     */
    public static Knowledge of(List<Belief> beliefs) {
        Map<BeliefKey, Belief> held = new LinkedHashMap<>();
        for (Belief belief : beliefs) {
            Belief previous = held.put(new BeliefKey(belief.kind(), belief.subject()), belief);
            if (previous != null) {
                throw new IllegalArgumentException("two beliefs about the same thing: " + belief);
            }
        }
        return new Knowledge(held);
    }

    /**
     * What the agent believes, falling back on the prior for anything it has
     * never had cause to think about.
     */
    public Belief believe(BeliefKind kind, String subject) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        Belief held = beliefs.get(new BeliefKey(kind, subject));
        return held != null ? held : Belief.assumed(kind, subject);
    }

    public Belief believe(BeliefKind kind, ResourceType about) {
        return believe(kind, subjectFor(about));
    }

    /** The agent's estimate, whether learned or assumed. */
    public double estimate(BeliefKind kind, ResourceType about) {
        return believe(kind, about).estimate();
    }

    /** How much the agent trusts that estimate. Zero until it has looked. */
    public double confidence(BeliefKind kind, ResourceType about) {
        return believe(kind, about).confidence();
    }

    /** Whether the agent has ever actually tested this. */
    public boolean hasTested(BeliefKind kind, ResourceType about) {
        return !believe(kind, about).isAssumed();
    }

    /**
     * The knowledge that results from observing one more case.
     *
     * <p>Returns a new body of knowledge; this one is unchanged, so what an
     * agent believed before a meal is still there to compare against.
     */
    public Knowledge learned(BeliefKind kind, String subject, double observed) {
        Belief updated = believe(kind, subject).updatedWith(observed);
        Map<BeliefKey, Belief> next = new LinkedHashMap<>(beliefs);
        next.put(new BeliefKey(kind, subject), updated);
        return new Knowledge(next);
    }

    public Knowledge learned(BeliefKind kind, ResourceType about, double observed) {
        return learned(kind, subjectFor(about), observed);
    }

    /** Everything worked out so far, in the order it was first learned. */
    public List<Belief> beliefs() {
        return List.copyOf(beliefs.values());
    }

    public int size() {
        return beliefs.size();
    }

    public boolean isEmpty() {
        return beliefs.isEmpty();
    }

    /** The one place that decides how a resource is written down in a belief. */
    public static String subjectFor(ResourceType type) {
        return type.name();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Knowledge knowledge && beliefs.equals(knowledge.beliefs);
    }

    @Override
    public int hashCode() {
        return beliefs.hashCode();
    }

    @Override
    public String toString() {
        if (beliefs.isEmpty()) {
            return "knows nothing yet";
        }
        return "knows " + String.join(
                "; ", beliefs.values().stream().map(Belief::describe).toList());
    }

    /** What makes two beliefs the same belief. */
    private record BeliefKey(BeliefKind kind, String subject) {
    }
}
