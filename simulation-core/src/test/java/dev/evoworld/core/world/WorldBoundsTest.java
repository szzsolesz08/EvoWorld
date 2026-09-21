package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldBoundsTest {

    private final WorldBounds bounds = WorldBounds.of(4, 3);

    @Test
    @DisplayName("coordinates run from the origin to width-1 by height-1")
    void knowsWhatIsInside() {
        assertTrue(bounds.contains(new Location(0, 0)));
        assertTrue(bounds.contains(new Location(3, 2)));
        assertFalse(bounds.contains(new Location(4, 2)), "width is exclusive");
        assertFalse(bounds.contains(new Location(3, 3)), "height is exclusive");
        assertFalse(bounds.contains(new Location(-1, 0)));
        assertFalse(bounds.contains(new Location(0, -1)));
    }

    @Test
    void clampsToTheNearestLocationInside() {
        assertEquals(new Location(3, 2), bounds.clamp(new Location(9, 9)));
        assertEquals(new Location(0, 0), bounds.clamp(new Location(-5, -5)));
        assertEquals(new Location(3, 0), bounds.clamp(new Location(7, -2)));
    }

    @Test
    @DisplayName("clamping a location that is already inside leaves it alone")
    void clampingIsIdempotentInside() {
        Location inside = new Location(2, 1);

        assertEquals(inside, bounds.clamp(inside));
        assertEquals(inside, bounds.clamp(bounds.clamp(inside)));
    }

    @Test
    void reportsItsCentreAndSize() {
        assertEquals(new Location(2, 1), bounds.center());
        assertEquals(12, bounds.tileCount());
        assertTrue(bounds.contains(bounds.center()));
    }

    @Test
    @DisplayName("a world with no area is not a world")
    void rejectsEmptyWorlds() {
        assertThrows(IllegalArgumentException.class, () -> WorldBounds.of(0, 5));
        assertThrows(IllegalArgumentException.class, () -> WorldBounds.of(5, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldBounds.of(-1, 5));
    }
}
