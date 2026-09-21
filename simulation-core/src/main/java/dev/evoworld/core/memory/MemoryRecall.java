package dev.evoworld.core.memory;

import java.util.List;

/**
 * The only way to get at what an agent remembers.
 *
 * <p>Deliberately not a list. A decision layer handed the raw log would grow to
 * depend on everything in it - the order, the duplicates, the things that
 * should have been forgotten - and "the agent recalled that" would stop being a
 * statement anyone could check. Asking a question and getting a ranked answer
 * keeps retrieval explainable and keeps the store free to change how it stores
 * things.
 */
public interface MemoryRecall {

    /** The most relevant memories matching a query, strongest first. */
    List<Recollection> recall(RecallQuery query);
}
