package dev.evoworld.core.save;

import dev.evoworld.core.resource.ResourceType;
import java.util.Objects;

/**
 * An action, written down as data rather than as a class.
 *
 * <p>One flat shape for every kind, because the save format should not need to
 * know the action hierarchy. Fields a kind does not use are left null or zero.
 *
 * @param kind the action's name, e.g. {@code MOVE}
 * @param resource what it concerns, if anything
 * @param x where it is headed, for a move
 * @param y where it is headed, for a move
 * @param amount the quantity it deals in: a portion, a gulp, a haul, a rest
 * @param cost the energy it costs, where it has one
 * @param ticks how long it was chosen to last, for actions whose length is
 *     decided when they start, such as a sleep; zero otherwise
 */
public record SavedAction(
        String kind, ResourceType resource, Integer x, Integer y, double amount, double cost, int ticks) {

    public SavedAction {
        Objects.requireNonNull(kind, "kind");
    }
}
