package dev.evoworld.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The world on screen: square tiles, row 0 at the top, and room for the panel and feed. */
class MapLayoutTest {

    private static final WorldBounds WORLD = WorldBounds.of(16, 16);

    @Test
    @DisplayName("the map is the largest square that fits beside the panel and above the feed")
    void fitsTheSpaceLeft() {
        MapLayout layout = MapLayout.fit(WORLD, 1120, 780);

        float availableHeight = 780 - MapLayout.FEED_HEIGHT - 2 * MapLayout.MARGIN;
        assertEquals(availableHeight / 16, layout.tile(), 1e-4);
        assertTrue(layout.right() <= 1120 - MapLayout.PANEL_WIDTH);
        assertTrue(layout.originY() >= MapLayout.FEED_HEIGHT);
        assertEquals(780 - MapLayout.MARGIN, layout.originY() + layout.height(), 1e-3);
    }

    @Test
    @DisplayName("a narrow window shrinks tiles to fit the width instead, and they stay square")
    void narrowWindow() {
        MapLayout layout = MapLayout.fit(WORLD, 800, 1200);

        float availableWidth = 800 - MapLayout.PANEL_WIDTH - 2 * MapLayout.MARGIN;
        assertEquals(availableWidth / 16, layout.tile(), 1e-4);
        assertEquals(layout.width(), layout.height(), 1e-4);
    }

    @Test
    @DisplayName("row 0 is drawn at the top and column 0 at the left, as in the console map")
    void orientation() {
        MapLayout layout = MapLayout.fit(WORLD, 1120, 780);
        Location topLeft = new Location(0, 0);
        Location bottomRight = new Location(15, 15);

        assertEquals(layout.originX(), layout.left(topLeft), 1e-4);
        assertEquals(layout.originY() + layout.height() - layout.tile(), layout.bottom(topLeft), 1e-3);
        assertEquals(layout.originY(), layout.bottom(bottomRight), 1e-4);
        assertEquals(layout.right() - layout.tile(), layout.left(bottomRight), 1e-3);
    }

    @Test
    @DisplayName("a point on screen maps back to the tile under it, and off the map to nothing")
    void pointsBackToTiles() {
        MapLayout layout = MapLayout.fit(WORLD, 1120, 780);
        for (Location tile : new Location[] {new Location(0, 0), new Location(7, 3), new Location(15, 15)}) {
            assertEquals(Optional.of(tile), layout.tileAt(layout.centreX(tile), layout.centreY(tile)));
        }
        assertEquals(Optional.empty(), layout.tileAt(layout.originX() - 5, layout.originY() + 5));
        assertEquals(Optional.empty(), layout.tileAt(layout.right() + 5, layout.originY() + 5));
        assertEquals(Optional.empty(), layout.tileAt(layout.originX() + 5, layout.originY() - 5));
    }
}
