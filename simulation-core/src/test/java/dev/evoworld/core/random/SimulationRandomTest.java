package dev.evoworld.core.random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationRandomTest {

    private static List<Integer> draw(SimulationRandom random, int count, int bound) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(random.nextInt(bound));
        }
        return values;
    }

    @Test
    @DisplayName("the same seed produces the same sequence")
    void sameSeedSameSequence() {
        List<Integer> first = draw(SimulationRandom.seededWith(1234L), 100, 1000);
        List<Integer> second = draw(SimulationRandom.seededWith(1234L), 100, 1000);

        assertEquals(first, second);
    }

    @Test
    @DisplayName("a different seed produces a different sequence")
    void differentSeedsDiverge() {
        List<Integer> first = draw(SimulationRandom.seededWith(1L), 100, 1000);
        List<Integer> second = draw(SimulationRandom.seededWith(2L), 100, 1000);

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("the sequence is fixed by the platform, not by this JVM run")
    void reproducesAKnownSequence() {
        // java.util.Random specifies its algorithm, so these exact values hold
        // on any conforming JVM. If this test ever fails, runs have stopped
        // being reproducible across machines.
        SimulationRandom random = SimulationRandom.seededWith(42L);

        assertEquals(List.of(30, 63, 48, 84, 70), draw(random, 5, 100));
    }

    @Test
    @DisplayName("draws are counted, which is the half of a saved run the seed does not cover")
    void countsDraws() {
        SimulationRandom random = SimulationRandom.seededWith(7L);

        assertEquals(0L, random.drawCount());
        draw(random, 13, 4);
        assertEquals(13L, random.drawCount());
    }

    @Test
    @DisplayName("values stay inside the requested bound")
    void respectsTheBound() {
        SimulationRandom random = SimulationRandom.seededWith(99L);

        assertTrue(draw(random, 500, 16).stream().allMatch(value -> value >= 0 && value < 16));
        assertEquals(List.of(0, 0, 0), draw(random, 3, 1));
    }

    @Test
    @DisplayName("a bound has to be positive")
    void rejectsImpossibleBounds() {
        SimulationRandom random = SimulationRandom.seededWith(1L);

        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(-5));
        assertEquals(0L, random.drawCount(), "a rejected draw must not consume the stream");
    }

    @Test
    @DisplayName("the seed is remembered so a run can say how it was made")
    void remembersItsSeed() {
        assertEquals(88L, SimulationRandom.seededWith(88L).seed());
    }

    @Test
    @DisplayName("the generator is exactly java.util.Random, bound for bound")
    void matchesTheSpecifiedGenerator() {
        // Includes huge non-power-of-two bounds, which is where the rejection
        // loop actually runs and where a hand-written copy would go wrong.
        int[] bounds = {1, 2, 3, 7, 16, 100, 1 << 30, (1 << 30) + 1, Integer.MAX_VALUE};
        for (long seed = -50L; seed <= 50L; seed++) {
            java.util.Random reference = new java.util.Random(seed);
            SimulationRandom ours = SimulationRandom.seededWith(seed);
            for (int i = 0; i < 400; i++) {
                int bound = bounds[i % bounds.length];
                assertEquals(reference.nextInt(bound), ours.nextInt(bound),
                        "seed " + seed + " draw " + i + " bound " + bound);
            }
        }
    }

    @Test
    @DisplayName("a resumed stream carries on exactly where the saved one stopped")
    void resumesFromSavedState() {
        SimulationRandom original = SimulationRandom.seededWith(77L);
        draw(original, 37, 1_000_003);

        SimulationRandom resumed = SimulationRandom.resumedAt(
                original.seed(), original.state(), original.drawCount());

        assertEquals(draw(original, 200, 13), draw(resumed, 200, 13));
        assertEquals(original.drawCount(), resumed.drawCount());
    }

    @Test
    void rejectsAnImpossibleState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationRandom.resumedAt(1L, -1L, 0L));
    }
}
