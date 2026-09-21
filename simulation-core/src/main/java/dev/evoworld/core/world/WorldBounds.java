package dev.evoworld.core.world;

/**
 * The extent of the world grid, and the rules for staying inside it.
 *
 * <p>Coordinates run from {@code (0, 0)} in one corner to
 * {@code (width - 1, height - 1)} in the other, with x increasing to the east
 * and y increasing to the south. Keeping that convention in one value object
 * means "is this on the map" and "pull this back onto the map" have exactly one
 * implementation rather than one per caller.
 */
public record WorldBounds(int width, int height) {

    public WorldBounds {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "world must have a positive size, was " + width + "x" + height);
        }
    }

    public static WorldBounds of(int width, int height) {
        return new WorldBounds(width, height);
    }

    public boolean contains(Location location) {
        return location.x() >= 0
                && location.x() < width
                && location.y() >= 0
                && location.y() < height;
    }

    /** The nearest location inside the bounds. */
    public Location clamp(Location location) {
        int x = Math.min(width - 1, Math.max(0, location.x()));
        int y = Math.min(height - 1, Math.max(0, location.y()));
        return new Location(x, y);
    }

    /** The middle of the map, biased towards the origin on even sizes. */
    public Location center() {
        return new Location(width / 2, height / 2);
    }

    public int tileCount() {
        return width * height;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
