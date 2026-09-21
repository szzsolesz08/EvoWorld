package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void translatesByOffset() {
        assertEquals(new Location(3, 7), new Location(1, 2).translated(2, 5));
    }

    @Test
    void measuresGridDistance() {
        assertEquals(5, new Location(0, 0).manhattanDistanceTo(new Location(2, 3)));
        assertEquals(5, new Location(2, 3).manhattanDistanceTo(new Location(0, 0)));
    }

    @Test
    @DisplayName("a step covers exactly one tile along the longer axis")
    void stepsOneTileTowardsTarget() {
        assertEquals(new Location(1, 0), new Location(0, 0).stepTowards(new Location(5, 1)));
        assertEquals(new Location(0, 1), new Location(0, 0).stepTowards(new Location(1, 5)));
        assertEquals(new Location(-1, 0), new Location(0, 0).stepTowards(new Location(-3, 0)));
    }

    @Test
    @DisplayName("ties resolve the same way every time, keeping movement deterministic")
    void breaksTiesOnTheXAxis() {
        assertEquals(new Location(1, 0), new Location(0, 0).stepTowards(new Location(4, 4)));
    }

    @Test
    void stayingPutWhenAlreadyAtTheTarget() {
        Location here = new Location(2, 2);
        assertEquals(here, here.stepTowards(here));
    }
}
