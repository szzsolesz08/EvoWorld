package dev.evoworld.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.resource.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeTest {

    private static final double EPSILON = 1e-9;

    @Test
    @DisplayName("an agent starts out having worked nothing out")
    void startsInnocent() {
        Knowledge knowledge = Knowledge.innocent();

        assertTrue(knowledge.isEmpty());
        assertFalse(knowledge.hasTested(BeliefKind.NOURISHMENT, ResourceType.BERRIES));
    }

    @Test
    @DisplayName("asking about something untested returns the assumption, not nothing")
    void untestedBeliefsFallBackOnThePrior() {
        Belief guess = Knowledge.innocent().believe(BeliefKind.NOURISHMENT, ResourceType.BERRIES);

        assertTrue(guess.isAssumed());
        assertEquals(BeliefKind.NOURISHMENT.prior(), guess.estimate(), EPSILON);
        assertEquals(0.0, guess.confidence(), EPSILON);
    }

    @Test
    @DisplayName("the first observation replaces the guess rather than averaging with it")
    void evidenceOverridesAssumption() {
        Belief learned = Belief.assumed(BeliefKind.NOURISHMENT, "BERRIES").updatedWith(8.0);

        assertEquals(8.0, learned.estimate(), EPSILON);
        assertEquals(1, learned.observations());
        assertFalse(learned.isAssumed());
    }

    @Test
    @DisplayName("later observations are averaged in, so one odd result does not overturn a belief")
    void laterObservationsAreAveraged() {
        Belief belief = Belief.assumed(BeliefKind.NOURISHMENT, "BERRIES")
                .updatedWith(8.0)
                .updatedWith(8.0)
                .updatedWith(8.0)
                .updatedWith(0.0);

        assertEquals(6.0, belief.estimate(), EPSILON, "three eights and a zero average to six");
        assertEquals(4, belief.observations());
    }

    @Test
    @DisplayName("confidence follows the count and never reaches certainty")
    void confidenceFollowsTheCount() {
        Belief belief = Belief.assumed(BeliefKind.HYDRATION, "WATER");

        assertEquals(0.0, belief.confidence(), EPSILON);
        assertEquals(0.25, belief.updatedWith(12.0).confidence(), EPSILON);

        for (int i = 0; i < 9; i++) {
            belief = belief.updatedWith(12.0);
        }
        assertEquals(0.75, belief.confidence(), EPSILON);
        assertTrue(belief.confidence() < 1.0);
    }

    @Test
    @DisplayName("a strong estimate held loosely is not worth much")
    void strengthCombinesEstimateAndConfidence() {
        Belief certainAndSmall = new Belief(BeliefKind.NOURISHMENT, "BERRIES", 4.0, 100);
        Belief hugeAndUntested = Belief.assumed(BeliefKind.NOURISHMENT, "BERRIES");

        assertTrue(certainAndSmall.strength() > hugeAndUntested.strength());
        assertEquals(0.0, hugeAndUntested.strength(), EPSILON);
    }

    @Test
    @DisplayName("learning produces new knowledge and leaves the old alone")
    void learningIsImmutable() {
        Knowledge before = Knowledge.innocent();

        Knowledge after = before.learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0);

        assertTrue(before.isEmpty(), "what the agent knew before should still be there");
        assertEquals(1, after.size());
        assertEquals(8.0, after.estimate(BeliefKind.NOURISHMENT, ResourceType.BERRIES), EPSILON);
    }

    @Test
    @DisplayName("beliefs about different things do not collide")
    void beliefsAreKeyedByKindAndSubject() {
        Knowledge knowledge = Knowledge.innocent()
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0)
                .learned(BeliefKind.HYDRATION, ResourceType.BERRIES, 0.0)
                .learned(BeliefKind.NOURISHMENT, ResourceType.WATER, 0.0);

        assertEquals(3, knowledge.size());
        assertEquals(8.0, knowledge.estimate(BeliefKind.NOURISHMENT, ResourceType.BERRIES), EPSILON);
        assertEquals(0.0, knowledge.estimate(BeliefKind.HYDRATION, ResourceType.BERRIES), EPSILON);
    }

    @Test
    @DisplayName("every belief can say what it is and how it got there")
    void beliefsAreExplainable() {
        Belief untested = Belief.assumed(BeliefKind.NOURISHMENT, "BERRIES");
        Belief tested = untested.updatedWith(8.0).updatedWith(8.0).updatedWith(8.0);

        assertTrue(untested.describe().contains("untested"), untested.describe());
        assertTrue(tested.describe().contains("8.0 hunger per unit"), tested.describe());
        assertTrue(tested.describe().contains("0.50 sure after 3"), tested.describe());
    }

    @Test
    @DisplayName("the same experience always produces the same belief")
    void learningIsDeterministic() {
        Knowledge first = Knowledge.innocent()
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0)
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 6.0);
        Knowledge second = Knowledge.innocent()
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 8.0)
                .learned(BeliefKind.NOURISHMENT, ResourceType.BERRIES, 6.0);

        assertEquals(first, second);
    }

    @Test
    void rejectsNonsense() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Belief(BeliefKind.NOURISHMENT, "BERRIES", Double.NaN, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Belief(BeliefKind.NOURISHMENT, "BERRIES", 1.0, -1));
    }
}
