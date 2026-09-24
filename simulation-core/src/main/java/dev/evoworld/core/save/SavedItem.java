package dev.evoworld.core.save;

import dev.evoworld.core.item.ItemKind;
import java.util.Objects;

/**
 * One thing an agent had, and how worn it was.
 *
 * @param kind what it is
 * @param usesLeft how much life was left in it
 */
public record SavedItem(ItemKind kind, int usesLeft) {

    public SavedItem {
        Objects.requireNonNull(kind, "kind");
    }
}
