package dev.evoworld.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A fire: what it warms, what it eats, and how it goes out. */
class FireTest {

    private static final Duration TICK = Duration.ofMinutes(10);
    private static final Location HERE = new Location(0, 0);

    @Test
    @DisplayName("warmth is full beside it and fades to nothing beyond its reach")
    void warmthFades() {
        Fire fire = Fire.lit(HERE, 5.0);

        assertEquals(Fire.WARMTH, fire.warmthAt(HERE), 1e-9);
        assertTrue(fire.warmthAt(new Location(1, 0)) < Fire.WARMTH, "a step away is cooler");
        assertTrue(fire.warmthAt(new Location(Fire.RADIUS, 0)) > 0.0, "still something at the edge");
        assertEquals(0.0, fire.warmthAt(new Location(Fire.RADIUS + 1, 0)), 1e-9, "and nothing past it");
        assertEquals(
                fire.warmthAt(new Location(2, 0)),
                fire.warmthAt(new Location(1, 1)),
                1e-9,
                "warmth is measured in steps, like everything else");
    }

    @Test
    @DisplayName("it burns its wood at a steady rate and goes out when the wood is gone")
    void burnsDown() {
        Fire fire = Fire.lit(HERE, 1.0);

        assertEquals(1.0 / Fire.WOOD_PER_HOUR, fire.hoursLeft(), 1e-9);
        fire.burn(Duration.ofHours(1), 1L);

        assertEquals(0.5, fire.fuel(), 1e-9);
        fire.burn(Duration.ofHours(1), 1L);
        assertTrue(fire.isOut());
        assertEquals(0.0, fire.warmthAt(HERE), 1e-9, "a dead fire warms nobody");
    }

    @Test
    @DisplayName("burning is the same however time is sliced")
    void burningDoesNotDependOnTheTickLength() {
        Fire slow = Fire.lit(HERE, 6.0);
        Fire fast = Fire.lit(HERE, 6.0);

        slow.burn(Duration.ofHours(1), 2L);
        fast.burn(TICK, 12L);

        assertEquals(slow.fuel(), fast.fuel(), 1e-9);
    }

    @Test
    @DisplayName("it takes wood up to what it can hold, and no more")
    void feeding() {
        Fire fire = Fire.lit(HERE, 2.0);

        double taken = fire.feed(100.0);

        assertEquals(Fire.MOST_IT_HOLDS - 2.0, taken, 1e-9);
        assertEquals(Fire.MOST_IT_HOLDS, fire.fuel(), 1e-9);
        assertEquals(0.0, fire.feed(5.0), 1e-9, "a full fire takes nothing");
    }

    @Test
    @DisplayName("a world warms the ground around its fires, and forgets them once they are out")
    void theWorldKeepsTheFires() {
        World world = new World(WorldBounds.of(16, 16));

        world.tendFire(new Location(4, 4), 1.0);

        assertEquals(1, world.fires().size());
        assertTrue(world.warmthAt(new Location(4, 5)) > 0.0, "a step away is warmed");
        assertEquals(0.0, world.warmthAt(new Location(12, 12)), 1e-9, "the far side is not");
        assertTrue(world.warmestFireNear(new Location(5, 4)).isPresent(), "and that is its fire");

        world.burnFires(Duration.ofHours(3));

        assertTrue(world.fires().isEmpty(), "burnt out and forgotten");
        assertEquals(0.0, world.warmthAt(new Location(4, 4)), 1e-9);
    }

    @Test
    @DisplayName("two fires do not add up: the warmer one is what you feel")
    void firesDoNotStack() {
        World world = new World(WorldBounds.of(16, 16));
        world.tendFire(new Location(4, 4), 4.0);
        world.tendFire(new Location(5, 4), 4.0);

        assertEquals(Fire.WARMTH, world.warmthAt(new Location(4, 4)), 1e-9);
        assertFalse(world.warmthAt(new Location(4, 4)) > Fire.WARMTH, "no bonfire arithmetic");
    }

    @Test
    @DisplayName("tending an existing fire feeds it rather than starting another")
    void tendingFeeds() {
        World world = new World(WorldBounds.of(16, 16));
        world.tendFire(new Location(4, 4), 3.0);

        world.tendFire(new Location(4, 4), 2.0);

        assertEquals(1, world.fires().size());
        assertEquals(5.0, world.fireAt(new Location(4, 4)).orElseThrow().fuel(), 1e-9);
    }
}
