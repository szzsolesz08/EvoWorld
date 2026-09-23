package dev.evoworld.viewer;

import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.ViewWindow;
import java.util.Optional;

/**
 * Where the world goes on screen, for a window of a given size.
 *
 * <p>The map takes the largest square that fits left of the side panel and
 * above the event feed. Screen coordinates start at the bottom left, as they
 * do in LibGDX; world rows start at the top, as they do in the console map, so
 * the window's first row is drawn highest.
 *
 * <p>It draws a {@link ViewWindow} rather than the world: an endless world has
 * no edges to fit on a screen. Today the window is the chunk the agent is in;
 * a viewer that scrolls, or follows one of several agents, changes what the
 * window is and nothing else.
 *
 * @param window the stretch of world on screen
 * @param originX left edge of the map, in pixels
 * @param originY bottom edge of the map, in pixels
 * @param tile the side of one tile, in pixels
 */
public record MapLayout(ViewWindow window, float originX, float originY, float tile) {

    public static final float PANEL_WIDTH = 440f;
    public static final float FEED_HEIGHT = 190f;
    public static final float MARGIN = 16f;

    public static MapLayout fit(ViewWindow window, float width, float height) {
        float availableWidth = Math.max(0f, width - PANEL_WIDTH - 2 * MARGIN);
        float availableHeight = Math.max(0f, height - FEED_HEIGHT - 2 * MARGIN);
        float tile = Math.max(1f, Math.min(
                availableWidth / window.across(), availableHeight / window.down()));
        float mapHeight = tile * window.down();
        float originY = height - MARGIN - mapHeight;
        // Centred in the space left over, so a square map in a wide window sits
        // between its margins rather than leaving one long empty column.
        float originX = MARGIN + Math.max(0f, (availableWidth - tile * window.across()) / 2f);
        return new MapLayout(window, originX, originY, tile);
    }

    /** Left edge of a tile. */
    public float left(Location location) {
        return originX + (location.x() - window.origin().x()) * tile;
    }

    /** Bottom edge of a tile. */
    public float bottom(Location location) {
        return originY + (window.down() - 1 - (location.y() - window.origin().y())) * tile;
    }

    public float centreX(Location location) {
        return left(location) + tile / 2f;
    }

    public float centreY(Location location) {
        return bottom(location) + tile / 2f;
    }

    public float width() {
        return tile * window.across();
    }

    public float height() {
        return tile * window.down();
    }

    /** Right edge of the map; the side panel starts after it. */
    public float right() {
        return originX + width();
    }

    /** The tile under a screen point, if the point is on the map. */
    public Optional<Location> tileAt(float x, float y) {
        int column = (int) Math.floor((x - originX) / tile);
        int rowFromBottom = (int) Math.floor((y - originY) / tile);
        int row = window.down() - 1 - rowFromBottom;
        if (column < 0 || row < 0 || column >= window.across() || row >= window.down()
                || x < originX || y < originY) {
            return Optional.empty();
        }
        return Optional.of(window.tileAt(column, row));
    }
}
