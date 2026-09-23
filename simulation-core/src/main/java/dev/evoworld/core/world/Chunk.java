package dev.evoworld.core.world;

import java.util.ArrayList;
import java.util.List;

/**
 * A square of the world, generated as one piece.
 *
 * <p>An endless world is not generated; its chunks are, one at a time, when an
 * agent comes near enough to see into one. A chunk's contents depend only on
 * the run's seed and the chunk's own column and row, so the same seed always
 * builds the same world, and a chunk an agent walks back into is the chunk it
 * left.
 *
 * <p>Columns and rows go negative, and the division that finds them floors
 * rather than truncates, so the chunk west of column 0 is column -1 rather than
 * column 0 again.
 *
 * @param column how many chunks east of the origin, which may be negative
 * @param row how many chunks south of the origin, which may be negative
 */
public record Chunk(int column, int row) {

    /** How many tiles across a chunk is. */
    public static final int SIZE = 64;

    public static Chunk containing(Location location) {
        return new Chunk(Math.floorDiv(location.x(), SIZE), Math.floorDiv(location.y(), SIZE));
    }

    /** The north-west corner: the lowest coordinate the chunk covers. */
    public Location origin() {
        return new Location(column * SIZE, row * SIZE);
    }

    /** The middle of the chunk. */
    public Location centre() {
        return new Location(column * SIZE + SIZE / 2, row * SIZE + SIZE / 2);
    }

    public boolean contains(Location location) {
        return Math.floorDiv(location.x(), SIZE) == column
                && Math.floorDiv(location.y(), SIZE) == row;
    }

    /** The bounds of this chunk, for generating inside it. */
    public WorldBounds bounds() {
        Location origin = origin();
        return new WorldBounds(
                origin.x(), origin.y(), origin.x() + SIZE - 1, origin.y() + SIZE - 1);
    }

    /**
     * Every chunk a square of {@code radius} tiles around {@code centre}
     * touches, in a fixed order - which is what a world has to have ready
     * before an agent there can look around.
     */
    public static List<Chunk> around(Location centre, int radius) {
        Chunk first = containing(new Location(centre.x() - radius, centre.y() - radius));
        Chunk last = containing(new Location(centre.x() + radius, centre.y() + radius));
        List<Chunk> chunks = new ArrayList<>();
        for (int row = first.row(); row <= last.row(); row++) {
            for (int column = first.column(); column <= last.column(); column++) {
                chunks.add(new Chunk(column, row));
            }
        }
        return List.copyOf(chunks);
    }

    @Override
    public String toString() {
        return "chunk " + column + "," + row;
    }
}
