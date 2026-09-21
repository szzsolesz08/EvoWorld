package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectionTest {

    private static final Location ORIGIN = new Location(10, 10);

    private Direction towards(int x, int y) {
        return Direction.from(ORIGIN, new Location(x, y));
    }

    @Test
    @DisplayName("north is up the screen, because y increases south")
    void cardinalBearings() {
        assertEquals(Direction.NORTH, towards(10, 4));
        assertEquals(Direction.SOUTH, towards(10, 16));
        assertEquals(Direction.EAST, towards(16, 10));
        assertEquals(Direction.WEST, towards(4, 10));
    }

    @Test
    void diagonalBearings() {
        assertEquals(Direction.NORTH_EAST, towards(14, 6));
        assertEquals(Direction.SOUTH_EAST, towards(14, 14));
        assertEquals(Direction.SOUTH_WEST, towards(6, 14));
        assertEquals(Direction.NORTH_WEST, towards(6, 6));
    }

    @Test
    @DisplayName("a target twice as far along one axis reads as that cardinal direction")
    void aDominantAxisWins() {
        assertEquals(Direction.EAST, towards(20, 11), "ten east and one south is east");
        assertEquals(Direction.SOUTH, towards(11, 20), "one east and ten south is south");
        assertEquals(Direction.NORTH_EAST, towards(13, 8), "three east and two north is diagonal");
    }

    @Test
    void thingsOnTheSameTileAreHere() {
        assertEquals(Direction.HERE, towards(10, 10));
        assertEquals(Direction.HERE, Direction.HERE.opposite());
    }

    @Test
    void everyBearingHasAnOpposite() {
        for (Direction direction : Direction.values()) {
            assertEquals(direction, direction.opposite().opposite());
            assertEquals(-direction.dx(), direction.opposite().dx());
            assertEquals(-direction.dy(), direction.opposite().dy());
        }
    }

    @Test
    void readsAsWords() {
        assertEquals("north", Direction.NORTH.label());
        assertEquals("south-west", Direction.SOUTH_WEST.label());
        assertEquals("here", Direction.HERE.toString());
    }
}
