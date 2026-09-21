package dev.evoworld.core.exploration;

import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/**
 * A coarse block of the map, the unit an agent thinks about the world in.
 *
 * <p>Tracking familiarity tile by tile would mean an unbounded set that grows
 * with every step, and would also be the wrong grain: an agent does not
 * remember which squares it stood on, it remembers roughly where it has been.
 * A sixteen-square world is sixteen regions, which is small enough to hold
 * whole and coarse enough to be worth holding.
 */
public record Region(int column, int row) {

    /** How many tiles across a region is. */
    public static final int SIZE = 4;

    public static Region containing(Location location) {
        return new Region(
                Math.floorDiv(location.x(), SIZE), Math.floorDiv(location.y(), SIZE));
    }

    /** The middle of the region, which is where an agent heads when exploring it. */
    public Location centre() {
        return new Location(column * SIZE + SIZE / 2, row * SIZE + SIZE / 2);
    }

    /** Every region of a world, in a fixed order. */
    public static List<Region> covering(WorldBounds bounds) {
        int columns = ceilDiv(bounds.width());
        int rows = ceilDiv(bounds.height());
        List<Region> regions = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                regions.add(new Region(column, row));
            }
        }
        return regions;
    }

    private static int ceilDiv(int extent) {
        return (extent + SIZE - 1) / SIZE;
    }

    @Override
    public String toString() {
        return "region " + column + "," + row;
    }
}
