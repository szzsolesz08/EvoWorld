package dev.evoworld.core.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Wanting to eat or drink: early and mild, then late and overwhelming. */
class AppetiteTest {

    private static final double EPSILON = 1e-12;

    @Test
    @DisplayName("nothing wanted with nothing needed, everything with everything")
    void ends() {
        assertEquals(0.0, Appetite.want(0.0, 30.0), EPSILON);
        assertEquals(1.0, Appetite.want(100.0, 30.0), EPSILON);
        assertEquals(1.0, Appetite.want(100.0, 100.0), EPSILON);
    }

    @Test
    @DisplayName("the want never falls as the need grows")
    void monotonic() {
        for (double fullAt : new double[] {30.0, 50.0, 100.0}) {
            double previous = -1.0;
            for (double pressure = 0.0; pressure <= 100.0; pressure += 0.5) {
                double want = Appetite.want(pressure, fullAt);
                assertTrue(want >= previous, "fell at " + pressure + " with appetite full at " + fullAt);
                previous = want;
            }
        }
    }

    @Test
    @DisplayName("appetite comes first: a mild need is wanted far more than its danger alone would say")
    void appetiteBeforeUrgency() {
        double mild = Appetite.want(10.0, 30.0);
        double urgencyOnly = (10.0 / 100.0) * (10.0 / 100.0);

        assertTrue(mild > 10 * urgencyOnly, mild + " against " + urgencyOnly);
    }

    @Test
    @DisplayName("an earlier appetite is felt sooner")
    void fullAtMovesTheAppetite() {
        assertTrue(Appetite.want(20.0, 30.0) > Appetite.want(20.0, 100.0));
    }

    @Test
    @DisplayName("once appetite is full only urgency still grows, and it grows fastest at the end")
    void urgencyTakesOver() {
        double early = Appetite.want(60.0, 30.0) - Appetite.want(40.0, 30.0);
        double late = Appetite.want(100.0, 30.0) - Appetite.want(80.0, 30.0);

        assertTrue(late > early);
    }
}
