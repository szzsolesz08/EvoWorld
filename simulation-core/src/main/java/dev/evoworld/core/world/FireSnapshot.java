package dev.evoworld.core.world;

import java.util.Locale;
import java.util.Objects;

/**
 * A fire as an observer sees it: where it burns and how long it has left.
 *
 * <p>A value, unlike the {@link Fire} itself, so a snapshot handed to a
 * renderer cannot burn down while it is being drawn.
 */
public record FireSnapshot(Location location, double fuel, double hoursLeft) {

    public FireSnapshot {
        Objects.requireNonNull(location, "location");
    }

    static FireSnapshot of(Fire fire) {
        return new FireSnapshot(fire.location(), fire.fuel(), fire.hoursLeft());
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "fire at %s (%.1fh)", location, hoursLeft);
    }
}
