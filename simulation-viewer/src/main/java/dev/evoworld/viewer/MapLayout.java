package dev.evoworld.viewer;

import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.Optional;

/**
 * Where the world goes on screen, for a window of a given size.
 *
 * <p>The map takes the largest square that fits left of the side panel and
 * above the event feed. Screen coordinates start at the bottom left, as they
 * do in LibGDX; world rows start at the top, as they do in the console map, so
 * row 0 is drawn highest.
 *
 * @param originX left edge of the map, in pixels
 * @param originY bottom edge of the map, in pixels
 * @param tile the side of one tile, in pixels
 */
public record MapLayout(WorldBounds bounds, float originX, float originY, float tile) {

    public static final float PANEL_WIDTH = 440f;
    public static final float FEED_HEIGHT = 190f;
    public static final float MARGIN = 16f;

    public static MapLayout fit(WorldBounds bounds, float width, float height) {
        float availableWidth = Math.max(0f, width - PANEL_WIDTH - 2 * MARGIN);
        float availableHeight = Math.max(0f, height - FEED_HEIGHT - 2 * MARGIN);
        float tile = Math.max(1f, Math.min(
                availableWidth / bounds.width(), availableHeight / bounds.height()));
        float mapHeight = tile * bounds.height();
        float originY = height - MARGIN - mapHeight;
        return new MapLayout(bounds, MARGIN, originY, tile);
    }

    /** Left edge of a tile. */
    public float left(Location location) {
        return originX + location.x() * tile;
    }

    /** Bottom edge of a tile. */
    public float bottom(Location location) {
        return originY + (bounds.height() - 1 - location.y()) * tile;
    }

    public float centreX(Location location) {
        return left(location) + tile / 2f;
    }

    public float centreY(Location location) {
        return bottom(location) + tile / 2f;
    }

    public float width() {
        return tile * bounds.width();
    }

    public float height() {
        return tile * bounds.height();
    }

    /** Right edge of the map; the side panel starts after it. */
    public float right() {
        return originX + width();
    }

    /** The tile under a screen point, if the point is on the map. */
    public Optional<Location> tileAt(float x, float y) {
        int column = (int) Math.floor((x - originX) / tile);
        int rowFromBottom = (int) Math.floor((y - originY) / tile);
        int row = bounds.height() - 1 - rowFromBottom;
        Location location = new Location(column, row);
        return bounds.contains(location) && x >= originX && y >= originY
                ? Optional.of(location)
                : Optional.empty();
    }
}
