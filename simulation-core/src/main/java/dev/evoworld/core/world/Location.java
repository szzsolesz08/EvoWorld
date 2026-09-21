package dev.evoworld.core.world;

/**
 * An immutable position on the world grid.
 */
public record Location(int x, int y) {

    public static Location of(int x, int y) {
        return new Location(x, y);
    }

    public Location translated(int dx, int dy) {
        return new Location(x + dx, y + dy);
    }

    /** Grid distance where diagonal movement is not allowed. */
    public int manhattanDistanceTo(Location other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    /**
     * One grid step from this location towards {@code target}.
     * Moves along the axis with the larger remaining distance, preferring
     * the x axis on ties so that stepping is deterministic.
     */
    public Location stepTowards(Location target) {
        int dx = target.x - x;
        int dy = target.y - y;
        if (dx == 0 && dy == 0) {
            return this;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return translated(Integer.signum(dx), 0);
        }
        return translated(0, Integer.signum(dy));
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
