package dev.evoworld.core.item;

import java.util.Locale;
import java.util.Objects;

/**
 * One thing an agent has, and how much life is left in it.
 *
 * <p>Immutable, like the rest of an agent's state: using an item produces a
 * worn one. Wear is what keeps the world from going quiet after the first
 * waterskin - a skin that lasted forever would be made once and never thought
 * about again, and the wood it took would stop mattering the day it was made.
 *
 * @param kind what it is
 * @param usesLeft how many more times it can be used; zero is worn out
 */
public record Item(ItemKind kind, int usesLeft) {

    public Item {
        Objects.requireNonNull(kind, "kind");
        if (usesLeft < 0) {
            throw new IllegalArgumentException("uses left cannot be negative, was " + usesLeft);
        }
    }

    /** A new one, straight from the making. */
    public static Item made(ItemKind kind) {
        return new Item(kind, kind.uses());
    }

    /** This item after one more use; a worn-out item does not wear further. */
    public Item worn() {
        return usesLeft <= 0 ? this : new Item(kind, usesLeft - 1);
    }

    public boolean isWornOut() {
        return usesLeft <= 0;
    }

    /** How much of its life is left, 0 to 1 - what an observer wants to see. */
    public double condition() {
        return kind.uses() <= 0 ? 0.0 : usesLeft / (double) kind.uses();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (%.0f%%)", kind.label(), condition() * 100);
    }
}
