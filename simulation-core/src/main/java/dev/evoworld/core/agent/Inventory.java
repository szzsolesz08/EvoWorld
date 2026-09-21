package dev.evoworld.core.agent;

import dev.evoworld.core.resource.ResourceType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What an agent is carrying.
 *
 * <p>Immutable, like {@link Needs}: every change produces a new inventory. The
 * agent holds one and replaces it, so a snapshot handed to an observer can
 * never change underneath them.
 *
 * <p>Carrying capacity is shared across everything held rather than being per
 * resource. An agent loaded with stone has no room for berries, which is the
 * constraint that will later make "what do I pick up?" a real decision instead
 * of "everything".
 *
 * <p>Entries at zero are dropped, so an inventory that has been emptied equals
 * one that was never filled.
 */
public record Inventory(Map<ResourceType, Double> contents, double capacity) {

    public static final double DEFAULT_CAPACITY = 20.0;

    public Inventory {
        if (capacity <= 0.0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        // An EnumMap keeps iteration in declaration order; Map.copyOf would not
        // promise any order, and an unordered log line is a broken replay.
        Map<ResourceType, Double> copy = new EnumMap<>(ResourceType.class);
        double total = 0.0;
        for (Map.Entry<ResourceType, Double> entry : contents.entrySet()) {
            double amount = entry.getValue();
            if (amount < 0.0) {
                throw new IllegalArgumentException(
                        "cannot hold a negative amount of " + entry.getKey().label());
            }
            if (amount > 0.0) {
                copy.put(entry.getKey(), amount);
                total += amount;
            }
        }
        if (total > capacity) {
            throw new IllegalArgumentException(
                    "carrying " + total + " exceeds a capacity of " + capacity);
        }
        contents = Collections.unmodifiableMap(copy);
    }

    public static Inventory empty() {
        return empty(DEFAULT_CAPACITY);
    }

    public static Inventory empty(double capacity) {
        return new Inventory(Map.of(), capacity);
    }

    public double quantityOf(ResourceType type) {
        return contents.getOrDefault(type, 0.0);
    }

    public boolean has(ResourceType type, double amount) {
        return quantityOf(type) >= amount;
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /** Everything carried, of every type. */
    public double total() {
        return contents.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double freeSpace() {
        return capacity - total();
    }

    public boolean isFull() {
        return freeSpace() <= 0.0;
    }

    /** How much of an offered amount would actually fit. */
    public double spaceFor(double offered) {
        return Math.min(Math.max(offered, 0.0), freeSpace());
    }

    /**
     * Adds what fits and silently leaves the rest behind.
     *
     * <p>Compare the result's {@link #total()} with this one's to find out how
     * much was taken, or ask {@link #spaceFor(double)} first.
     */
    public Inventory plus(ResourceType type, double amount) {
        requireNotNegative(amount);
        double stored = spaceFor(amount);
        if (stored == 0.0) {
            return this;
        }
        Map<ResourceType, Double> updated = mutableCopy();
        updated.merge(type, stored, Double::sum);
        return new Inventory(updated, capacity);
    }

    /** Removes up to {@code amount}; taking more than is held simply empties it. */
    public Inventory minus(ResourceType type, double amount) {
        requireNotNegative(amount);
        double removed = Math.min(amount, quantityOf(type));
        if (removed == 0.0) {
            return this;
        }
        Map<ResourceType, Double> updated = mutableCopy();
        updated.merge(type, -removed, Double::sum);
        return new Inventory(updated, capacity);
    }

    /**
     * A working copy of the contents.
     *
     * <p>Built by hand rather than with {@code new EnumMap<>(contents)}: that
     * constructor cannot infer the key type from an empty map that is not
     * itself an EnumMap, and the contents are wrapped as unmodifiable.
     */
    private Map<ResourceType, Double> mutableCopy() {
        Map<ResourceType, Double> copy = new EnumMap<>(ResourceType.class);
        copy.putAll(contents);
        return copy;
    }

    private static void requireNotNegative(double amount) {
        if (amount < 0.0) {
            throw new IllegalArgumentException("amount must not be negative, was " + amount);
        }
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "carrying nothing";
        }
        return contents.entrySet().stream()
                .map(entry -> String.format(
                        Locale.ROOT, "%s %.1f", entry.getKey().label(), entry.getValue()))
                .collect(Collectors.joining(", ", "carrying ", ""));
    }
}
