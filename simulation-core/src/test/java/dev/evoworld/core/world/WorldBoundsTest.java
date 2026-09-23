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

    @Test
    @DisplayName("an endless world holds anything within the limit, and nothing beyond it")
    void endlessWorldsAreAlmostInfinite() {
        WorldBounds endless = WorldBounds.endless();

        assertTrue(endless.isEndless());
        assertFalse(WorldBounds.of(16, 16).isEndless());
        assertTrue(endless.contains(new Location(0, 0)));
        assertTrue(endless.contains(new Location(-WorldBounds.LIMIT, WorldBounds.LIMIT)));
        assertEquals(new Location(0, 0), endless.center(), "the origin is the middle of nowhere");
    }

    @Test
    @DisplayName("the limit is a wall at the end of the world, not an overflow")
    void theLimitHolds() {
        WorldBounds endless = WorldBounds.endless();
        Location edge = new Location(WorldBounds.LIMIT, WorldBounds.LIMIT);

        assertEquals(edge, endless.clamp(new Location(Integer.MAX_VALUE, Integer.MAX_VALUE)));
        // Far enough inside Integer.MAX_VALUE that the distance between any two
        // places in the world still fits in an int, which is what every
        // distance in the simulation is measured in.
        assertTrue(
                (long) endless.width() * 2 < Integer.MAX_VALUE,
                "a walk across the world must be countable");
        long across = 2L * WorldBounds.LIMIT + 1;
        assertEquals(across * across, endless.tileCount(),
                "counted as a long, because an int would wrap");
    }

    @Test
    @DisplayName("bounds beyond the limit are refused rather than quietly wrapped")
    void rejectsWorldsBeyondTheLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldBounds(-WorldBounds.LIMIT - 1, 0, 0, 0));
    }
}
