package dev.evoworld.core.action;

import java.util.Objects;

/**
 * Why an action cannot happen, and whether that is likely to change.
 *
 * <p>The distinction earns its keep once an agent can learn. "Not hungry" and
 * "there is nothing here right now" are facts about this moment and teach
 * nothing. "Stone is not food" is a fact about stone, and an agent that hears
 * it once should never ask again.
 *
 * @param permanent whether this refusal is about the nature of the thing rather
 *     than the state of the world
 */
public record Refusal(String reason, boolean permanent) {

    public Refusal {
        Objects.requireNonNull(reason, "reason");
    }

    /** Not right now - conditions may change. */
    public static Refusal now(String reason) {
        return new Refusal(reason, false);
    }

    /** Not ever - this is how the world is. */
    public static Refusal never(String reason) {
        return new Refusal(reason, true);
    }

    @Override
    public String toString() {
        return reason;
    }
}
