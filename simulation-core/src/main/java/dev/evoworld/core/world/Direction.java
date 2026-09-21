package dev.evoworld.core.world;

import java.util.Objects;

/**
 * A compass bearing from one tile to another.
 *
 * <p>x increases east and y increases south, matching {@link WorldBounds}.
 *
 * <p>Exists so perception can say "berries four steps north" rather than
 * handing over a pair of coordinates and leaving the reader to work it out.
 * Nothing navigates by it - movement still aims at a {@link Location} - so a
 * bearing is a description, not an instruction.
 */
public enum Direction {

    NORTH(0, -1, "north"),
    NORTH_EAST(1, -1, "north-east"),
    EAST(1, 0, "east"),
    SOUTH_EAST(1, 1, "south-east"),
    SOUTH(0, 1, "south"),
    SOUTH_WEST(-1, 1, "south-west"),
    WEST(-1, 0, "west"),
    NORTH_WEST(-1, -1, "north-west"),
    HERE(0, 0, "here");

    /**
     * How far one axis must dominate the other before a bearing counts as
     * cardinal rather than diagonal. Twice as far along one axis reads as that
     * direction, which keeps "ten east and one south" from being reported as
     * south-east.
     */
    private static final int CARDINAL_DOMINANCE = 2;

    private final int dx;
    private final int dy;
    private final String label;

    Direction(int dx, int dy, String label) {
        this.dx = dx;
        this.dy = dy;
        this.label = label;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public String label() {
        return label;
    }

    /** The bearing from {@code origin} to {@code target}. */
    public static Direction from(Location origin, Location target) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");

        int dx = target.x() - origin.x();
        int dy = target.y() - origin.y();
        if (dx == 0 && dy == 0) {
            return HERE;
        }
        if (Math.abs(dx) >= CARDINAL_DOMINANCE * Math.abs(dy)) {
            return dx > 0 ? EAST : WEST;
        }
        if (Math.abs(dy) >= CARDINAL_DOMINANCE * Math.abs(dx)) {
            return dy > 0 ? SOUTH : NORTH;
        }
        if (dx > 0) {
            return dy > 0 ? SOUTH_EAST : NORTH_EAST;
        }
        return dy > 0 ? SOUTH_WEST : NORTH_WEST;
    }

    /** The bearing you would take coming back the other way. */
    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case NORTH_EAST -> SOUTH_WEST;
            case EAST -> WEST;
            case SOUTH_EAST -> NORTH_WEST;
            case SOUTH -> NORTH;
            case SOUTH_WEST -> NORTH_EAST;
            case WEST -> EAST;
            case NORTH_WEST -> SOUTH_EAST;
            case HERE -> HERE;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
