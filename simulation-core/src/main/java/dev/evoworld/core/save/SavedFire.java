package dev.evoworld.core.save;

/**
 * A fire as saved: where it is and how much wood is left on it.
 *
 * @param x where it burns
 * @param y where it burns
 * @param fuel how much wood it still has
 */
public record SavedFire(int x, int y, double fuel) {
}
