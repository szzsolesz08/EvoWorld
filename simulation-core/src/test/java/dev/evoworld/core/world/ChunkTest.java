package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The grid an endless world is generated on. */
class ChunkTest {

    @Test
    @DisplayName("a place belongs to one chunk, west and north of the origin as well as east of it")
    void containingWorksBothWaysFromTheOrigin() {
        assertEquals(new Chunk(0, 0), Chunk.containing(new Location(0, 0)));
        assertEquals(new Chunk(0, 0), Chunk.containing(new Location(63, 63)));
        assertEquals(new Chunk(1, 1), Chunk.containing(new Location(64, 64)));
        // The tile west of the origin is in the chunk west of it, not in it
        // again - which is what floored division buys and truncation loses.
        assertEquals(new Chunk(-1, -1), Chunk.containing(new Location(-1, -1)));
        assertEquals(new Chunk(-1, -1), Chunk.containing(new Location(-64, -64)));
        assertEquals(new Chunk(-2, -2), Chunk.containing(new Location(-65, -65)));
    }

    @Test
    @DisplayName("a chunk's corner and middle are inside it, and its neighbours' are not")
    void originAndCentre() {
        Chunk chunk = new Chunk(-3, 2);

        assertEquals(new Location(-192, 128), chunk.origin());
        assertEquals(new Location(-160, 160), chunk.centre());
        assertTrue(chunk.contains(chunk.origin()));
        assertTrue(chunk.contains(chunk.centre()));
        assertFalse(chunk.contains(new Location(-193, 128)), "one step west is the next chunk");
        assertFalse(chunk.contains(new Location(-192, 192)), "one chunk south is the next chunk");
        assertEquals(chunk, Chunk.containing(chunk.centre()));
    }

    @Test
    @DisplayName("what an agent can see is covered by the chunks around it")
    void aroundCoversEverythingInSight() {
        Location standing = new Location(-1, 63);
        int radius = 12;

        List<Chunk> around = Chunk.around(standing, radius);

        for (int x = standing.x() - radius; x <= standing.x() + radius; x++) {
            for (int y = standing.y() - radius; y <= standing.y() + radius; y++) {
                assertTrue(
                        around.contains(Chunk.containing(new Location(x, y))),
                        "(" + x + "," + y + ") is in sight but its chunk is not listed: " + around);
            }
        }
        // Standing on a corner, sight reaches into all four chunks and no more.
        assertEquals(4, around.size(), around.toString());
    }

    @Test
    @DisplayName("the chunks around a place come back in the same order every time")
    void aroundIsOrdered() {
        assertEquals(
                Chunk.around(new Location(0, 0), 70), Chunk.around(new Location(0, 0), 70));
        assertEquals(
                List.of(new Chunk(0, 0)),
                Chunk.around(new Location(32, 32), 4),
                "well inside one chunk, sight stays in it");
    }
}
