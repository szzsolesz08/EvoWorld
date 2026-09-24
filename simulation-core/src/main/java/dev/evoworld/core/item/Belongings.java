package dev.evoworld.core.item;

import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The things an agent has on it, as opposed to the stuff it is carrying.
 *
 * <p>Kept apart from {@link dev.evoworld.core.agent.Inventory} because they
 * answer different questions. The inventory says how much of something there
 * is; this says what the agent is equipped to do - what it can carry water in,
 * what it can cut wood with. They meet in one place: an item takes up room in
 * the pack, so a kit of tools is a pack with less space for berries.
 *
 * <p>Immutable, and ordered by when each thing was acquired, so a save comes
 * back as the same kit rather than a set in whatever order it happened to
 * iterate.
 */
public record Belongings(List<Item> items) {

    public Belongings {
        items = List.copyOf(items);
    }

    public static Belongings nothing() {
        return new Belongings(List.of());
    }

    public static Belongings of(Item... items) {
        return new Belongings(List.of(items));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** How much of the pack these take up. */
    public double bulk() {
        return items.stream().mapToDouble(item -> item.kind().bulk()).sum();
    }

    /**
     * How much {@code type} the agent has anything to carry it in.
     *
     * <p>Zero means it cannot be carried at all, which is the whole point of a
     * container: water is not something you can hold in your hands.
     */
    public double roomFor(ResourceType type) {
        Objects.requireNonNull(type, "type");
        return items.stream()
                .filter(item -> !item.isWornOut() && item.kind().carries(type))
                .mapToDouble(item -> item.kind().holdsUpTo())
                .sum();
    }

    /** The best tool for gathering {@code type}, if the agent has one that still works. */
    public Optional<Item> toolFor(ResourceType type) {
        Objects.requireNonNull(type, "type");
        return items.stream()
                .filter(item -> !item.isWornOut() && item.kind().helpsWith(type))
                .min((first, second) -> Double.compare(
                        first.kind().effortMultiplier(), second.kind().effortMultiplier()));
    }

    /** Whether the agent has a working one of these. */
    public boolean has(ItemKind kind) {
        return items.stream().anyMatch(item -> item.kind() == kind && !item.isWornOut());
    }

    /** How many working ones of this kind the agent has. */
    public int countOf(ItemKind kind) {
        return (int) items.stream()
                .filter(item -> item.kind() == kind && !item.isWornOut())
                .count();
    }

    public Belongings plus(Item item) {
        List<Item> updated = new ArrayList<>(items);
        updated.add(Objects.requireNonNull(item, "item"));
        return new Belongings(updated);
    }

    /**
     * The kit after using one of these once.
     *
     * <p>Wears the most worn working one first, so a kit is used up one item at
     * a time rather than every item at once.
     */
    public Belongings after(ItemKind used) {
        int chosen = -1;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.kind() != used || item.isWornOut()) {
                continue;
            }
            if (chosen < 0 || item.usesLeft() < items.get(chosen).usesLeft()) {
                chosen = i;
            }
        }
        if (chosen < 0) {
            return this;
        }
        List<Item> updated = new ArrayList<>(items);
        updated.set(chosen, updated.get(chosen).worn());
        return new Belongings(updated);
    }

    /** The kit with worn-out things thrown away, which is what an agent does with them. */
    public Belongings withoutWornOut() {
        List<Item> keeping = items.stream().filter(item -> !item.isWornOut()).toList();
        return keeping.size() == items.size() ? this : new Belongings(keeping);
    }

    @Override
    public String toString() {
        return isEmpty()
                ? "nothing of its own"
                : items.stream().map(Item::toString).collect(Collectors.joining(", "));
    }

    /** What an observer prints: kinds and condition, in the order acquired. */
    public String describe() {
        return isEmpty()
                ? "nothing"
                : items.stream()
                        .map(item -> String.format(
                                Locale.ROOT, "%s %.0f%%", item.kind().label(), item.condition() * 100))
                        .collect(Collectors.joining(", "));
    }
}
