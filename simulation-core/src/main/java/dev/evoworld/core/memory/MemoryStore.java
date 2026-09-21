package dev.evoworld.core.memory;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.world.Location;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one agent remembers, and how it gets back out.
 *
 * <p>Bounded on purpose. An unbounded log would grow without limit over a
 * hundred simulated days, and - worse - it would let an agent be perfectly
 * informed about everything it had ever glanced at, which is not memory, it is
 * a database. When the store is full the least valuable memory goes: faint
 * things and unimportant things are forgotten before vivid ones, which is the
 * behaviour the word implies.
 *
 * <p>Seeing the same bush again does not add a second memory of it. A memory
 * with the same kind, place and subject <em>replaces</em> the older one, which
 * is the difference between remembering a thing and remembering every time you
 * noticed it.
 *
 * <p>Retrieval is {@link #recall} and nothing else - see {@link MemoryRecall}
 * for why. {@link #contents()} exists for observers and tests, and no decision
 * ever goes near it.
 */
public final class MemoryStore implements MemoryRecall {

    /** Enough to know a small world well, not enough to know it perfectly. */
    public static final int DEFAULT_CAPACITY = 48;

    /** Steps at which a memory's relevance halves for being far away. */
    public static final double PROXIMITY_SCALE = 8.0;

    private final int capacity;

    /** Insertion-ordered, so eviction never depends on hash codes. */
    private final Map<Memory.MemoryKey, Memory> memories = new LinkedHashMap<>();

    public MemoryStore() {
        this(DEFAULT_CAPACITY);
    }

    public MemoryStore(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("a memory must hold at least one thing");
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int size() {
        return memories.size();
    }

    public boolean isEmpty() {
        return memories.isEmpty();
    }

    /**
     * Commits a memory, replacing any earlier memory of the same thing.
     *
     * <p>If that pushes the store past capacity, the least valuable memory is
     * dropped - judged as of the moment the new one arrived, so what counts as
     * valuable accounts for what has already faded.
     */
    public void remember(Memory memory) {
        Objects.requireNonNull(memory, "memory");
        // Remove first so a repeat sighting moves to the back of the queue
        // rather than keeping its original place in it.
        memories.remove(memory.key());
        memories.put(memory.key(), memory);
        if (memories.size() > capacity) {
            forgetLeastValuable(memory.formed().tick());
        }
    }

    /**
     * The few memories most worth mentioning right now.
     *
     * <p>Relevance as recall judges it, with the routine left out: how much was
     * in a bush an hour ago, and which actions went fine, are what memory is
     * mostly made of and what nobody watching wants to read. Places, finds,
     * failures and close calls are what is left.
     */
    public List<Recollection> highlights(SimulationTime now, Location from, int limit) {
        return recall(RecallQuery.from(now, from).limitedTo(capacity)).stream()
                .filter(found -> found.kind() != MemoryKind.OBSERVED_RESOURCE)
                .filter(found -> found.kind() != MemoryKind.SUCCESSFUL_ACTION)
                .limit(limit)
                .toList();
    }

    /** Everything held, oldest first. For observers and tests, never for deciding. */
    public List<Memory> contents() {
        return List.copyOf(memories.values());
    }

    @Override
    public List<Recollection> recall(RecallQuery query) {
        Objects.requireNonNull(query, "query");
        long tick = query.now().tick();

        List<Recollection> found = new ArrayList<>();
        for (Memory memory : memories.values()) {
            if (!query.matches(memory)) {
                continue;
            }
            double confidence = memory.confidenceAt(tick);
            double relevance = relevanceOf(memory, confidence, query.from());
            if (relevance >= query.minimumRelevance()) {
                found.add(new Recollection(memory, relevance, confidence));
            }
        }

        found.sort(byRelevanceThenPosition());
        return found.size() <= query.limit()
                ? List.copyOf(found)
                : List.copyOf(found.subList(0, query.limit()));
    }

    /**
     * How much a memory is worth bringing up right now.
     *
     * <p>Three things an agent would weigh without thinking about it: do I
     * still believe it, does it matter, and is it anywhere near here. Distance
     * discounts hyperbolically, the same shape the decision layer uses for
     * time, so a memory of water twenty steps away is faint rather than absent.
     */
    private double relevanceOf(Memory memory, double confidence, Location from) {
        int distance = from.manhattanDistanceTo(memory.location());
        double proximity = 1.0 / (1.0 + distance / PROXIMITY_SCALE);
        return confidence * memory.importance() * proximity;
    }

    /** Strongest first; ties settled by position so recall never wobbles. */
    private static Comparator<Recollection> byRelevanceThenPosition() {
        return Comparator.comparingDouble(Recollection::relevance)
                .reversed()
                .thenComparingInt(found -> found.location().x())
                .thenComparingInt(found -> found.location().y())
                .thenComparing(found -> found.memory().subject());
    }

    private void forgetLeastValuable(long tick) {
        Memory weakest = null;
        for (Memory memory : memories.values()) {
            // Strictly less, so the earliest of equally worthless memories goes.
            if (weakest == null || memory.valueAt(tick) < weakest.valueAt(tick)) {
                weakest = memory;
            }
        }
        if (weakest != null) {
            memories.remove(weakest.key());
        }
    }

    @Override
    public String toString() {
        return "remembers " + memories.size() + " of " + capacity + " things";
    }
}
