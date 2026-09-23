package dev.evoworld.core.world;

import java.util.Objects;

/**
 * A rectangle of the world, for looking at.
 *
 * <p>A world with no edges cannot be drawn whole, so everything that draws one
 * draws a window on it: the chunk somebody is standing in, a patch of ground
 * around them, or - later - wherever a viewer has scrolled to.
 *
 * @param origin the north-west corner, the lowest coordinate in view
 * @param across how many tiles wide
 * @param down how many tiles tall
 */
public record ViewWindow(Location origin, int across, int down) {

    public ViewWindow {
        Objects.requireNonNull(origin, "origin");
        if (across < 1 || down < 1) {
            throw new IllegalArgumentException("a window shows something, was " + across + "x" + down);
        }
    }

    /** The whole of a chunk. */
    public static ViewWindow of(Chunk chunk) {
        return new ViewWindow(chunk.origin(), Chunk.SIZE, Chunk.SIZE);
    }

    /** A rectangle centred on a place, clipped to a world that has edges. */
    public static ViewWindow around(Location centre, int across, int down, WorldBounds bounds) {
        Location wanted = new Location(centre.x() - across / 2, centre.y() - down / 2);
        if (bounds.isEndless()) {
            return new ViewWindow(wanted, across, down);
        }
        int width = Math.min(across, bounds.width());
        int height = Math.min(down, bounds.height());
        int x = Math.min(Math.max(wanted.x(), bounds.minX()), bounds.maxX() - width + 1);
        int y = Math.min(Math.max(wanted.y(), bounds.minY()), bounds.maxY() - height + 1);
        return new ViewWindow(new Location(x, y), width, height);
    }

    public boolean contains(Location location) {
        return location.x() >= origin.x()
                && location.x() < origin.x() + across
                && location.y() >= origin.y()
                && location.y() < origin.y() + down;
    }

    /** The tile at a column and row of the window, counted from its corner. */
    public Location tileAt(int column, int row) {
        return new Location(origin.x() + column, origin.y() + row);
    }

    @Override
    public String toString() {
        return across + "x" + down + " at " + origin;
    }
}
