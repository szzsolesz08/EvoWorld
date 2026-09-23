package dev.evoworld.core.world;

/**
 * The stretch of ground a world covers, as the corners of a rectangle.
 *
 * <p>Two kinds, and the same type describes both. A <strong>finite</strong>
 * world is the old one: a rectangle from the origin, every tile of it
 * generated up front. An <strong>endless</strong> one runs from
 * {@code -LIMIT} to {@code LIMIT} in both directions, and is generated a chunk
 * at a time as an agent walks into it.
 *
 * <p>The limit is far short of {@link Integer#MAX_VALUE}, on purpose: it leaves
 * room for the difference between any two locations to be an {@code int}
 * without overflowing, so distances need no special care. It is still 268
 * million tiles each way - about five million years' walking - which is as good
 * as endless.
 *
 * @param minX the leftmost column, inclusive
 * @param minY the topmost row, inclusive
 * @param maxX the rightmost column, inclusive
 * @param maxY the bottommost row, inclusive
 */
public record WorldBounds(int minX, int minY, int maxX, int maxY) {

    /** How far an endless world runs in each direction from the origin. */
    public static final int LIMIT = 1 << 28;

    public WorldBounds {
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException(
                    "world must have a positive size, was " + minX + ".." + maxX
                            + " by " + minY + ".." + maxY);
        }
        if (minX < -LIMIT || minY < -LIMIT || maxX > LIMIT || maxY > LIMIT) {
            throw new IllegalArgumentException("world runs past the edge of the world");
        }
    }

    /** A finite world of this size, with its corner at the origin. */
    public static WorldBounds of(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "world must have a positive size, was " + width + "x" + height);
        }
        return new WorldBounds(0, 0, width - 1, height - 1);
    }

    /** As much world as there is: everything within the limit, in every direction. */
    public static WorldBounds endless() {
        return new WorldBounds(-LIMIT, -LIMIT, LIMIT, LIMIT);
    }

    public boolean isEndless() {
        return minX == -LIMIT && minY == -LIMIT && maxX == LIMIT && maxY == LIMIT;
    }

    public boolean contains(Location location) {
        return location.x() >= minX
                && location.x() <= maxX
                && location.y() >= minY
                && location.y() <= maxY;
    }

    /** The nearest location inside the bounds. */
    public Location clamp(Location location) {
        return new Location(
                Math.min(maxX, Math.max(minX, location.x())),
                Math.min(maxY, Math.max(minY, location.y())));
    }

    /** The middle of the map; the origin in an endless one. */
    public Location center() {
        return isEndless() ? new Location(0, 0) : new Location(width() / 2, height() / 2);
    }

    /** How many tiles across, which is 536 million in an endless world. */
    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    /** How many tiles in all. A long, because an endless world has rather a lot. */
    public long tileCount() {
        return (long) width() * height();
    }

    @Override
    public String toString() {
        if (isEndless()) {
            return "endless";
        }
        return minX == 0 && minY == 0
                ? width() + "x" + height()
                : width() + "x" + height() + " at (" + minX + "," + minY + ")";
    }
}
