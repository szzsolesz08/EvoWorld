package dev.evoworld.core.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemoryStoreTest {

    private static final double EPSILON = 1e-9;
    private static final Location HOME = new Location(8, 8);

    private static SimulationTime at(long tick) {
        return new SimulationTime(tick, Duration.ofMinutes(10).multipliedBy(tick));
    }

    private static Memory water(long tick, int x, int y) {
        return Memory.formed(
                MemoryKind.RESOURCE_LOCATION,
                at(tick),
                new Location(x, y),
                RecallQuery.subjectFor(ResourceType.WATER),
                "water here");
    }

    private static Memory berries(long tick, int x, int y) {
        return Memory.formed(
                MemoryKind.RESOURCE_LOCATION,
                at(tick),
                new Location(x, y),
                RecallQuery.subjectFor(ResourceType.BERRIES),
                "berries here");
    }

    private List<Recollection> recallFrom(MemoryStore store, long tick) {
        return store.recall(RecallQuery.from(at(tick), HOME));
    }

    // --- storing ----------------------------------------------------------

    @Nested
    @DisplayName("storing")
    class Storing {

        @Test
        void remembersWhatItIsTold() {
            MemoryStore store = new MemoryStore();

            store.remember(water(1, 3, 8));

            assertEquals(1, store.size());
            assertEquals(MemoryKind.RESOURCE_LOCATION, store.contents().get(0).kind());
        }

        @Test
        @DisplayName("seeing the same thing again updates the memory rather than adding one")
        void repeatSightingsReplace() {
            MemoryStore store = new MemoryStore();

            store.remember(water(1, 3, 8));
            store.remember(water(50, 3, 8));
            store.remember(water(99, 3, 8));

            assertEquals(1, store.size(), "one spring, one memory of it");
            assertEquals(99L, store.contents().get(0).formed().tick());
        }

        @Test
        @DisplayName("different things at the same place are different memories")
        void differentSubjectsCoexist() {
            MemoryStore store = new MemoryStore();

            store.remember(water(1, 3, 8));
            store.remember(berries(1, 3, 8));

            assertEquals(2, store.size());
        }

        @Test
        @DisplayName("the same thing in two places is two memories")
        void differentPlacesCoexist() {
            MemoryStore store = new MemoryStore();

            store.remember(water(1, 3, 8));
            store.remember(water(1, 12, 2));

            assertEquals(2, store.size());
        }

        @Test
        @DisplayName("memory is bounded, so it is memory and not a database")
        void neverGrowsPastCapacity() {
            MemoryStore store = new MemoryStore(5);

            for (int i = 0; i < 40; i++) {
                store.remember(berries(i, i % 16, (i * 3) % 16));
            }

            assertEquals(5, store.size());
            assertEquals(5, store.capacity());
        }

        @Test
        @DisplayName("what is forgotten first is what mattered least")
        void forgetsTheLeastValuableFirst() {
            MemoryStore store = new MemoryStore(2);
            Memory trivial = berries(1, 0, 0).withImportance(0.05);
            Memory vital = water(1, 1, 1).withImportance(0.95);

            store.remember(trivial);
            store.remember(vital);
            store.remember(berries(1, 2, 2).withImportance(0.5));

            List<Memory> kept = store.contents();
            assertTrue(kept.contains(vital), "the important one should survive");
            assertFalse(kept.contains(trivial), "the trivial one should not");
        }

        @Test
        void rejectsAnImpossibleStore() {
            assertThrows(IllegalArgumentException.class, () -> new MemoryStore(0));
        }
    }

    // --- timestamps -------------------------------------------------------

    @Nested
    @DisplayName("timestamps")
    class Timestamps {

        @Test
        @DisplayName("a memory knows when it was formed and how long ago that was")
        void memoriesAreStamped() {
            Memory memory = water(40, 3, 8);

            assertEquals(40L, memory.formed().tick());
            assertEquals(Duration.ofMinutes(400), memory.formed().elapsed());
            assertEquals(60L, memory.ageAt(100));
            assertEquals(0L, memory.ageAt(40));
        }

        @Test
        @DisplayName("a memory is never younger than new, even if asked about the past")
        void ageNeverGoesNegative() {
            assertEquals(0L, water(40, 3, 8).ageAt(10));
        }

        @Test
        @DisplayName("an updated memory carries the newer timestamp")
        void updatesRefreshTheTimestamp() {
            MemoryStore store = new MemoryStore();
            store.remember(water(10, 3, 8));
            store.remember(water(300, 3, 8));

            assertEquals(300L, store.contents().get(0).formed().tick());
            assertEquals(0L, store.contents().get(0).ageAt(300));
        }
    }

    // --- confidence -------------------------------------------------------

    @Nested
    @DisplayName("confidence")
    class Confidence {

        @Test
        @DisplayName("a fresh memory is fully believed")
        void freshMemoriesAreCertain() {
            assertEquals(1.0, water(10, 3, 8).confidenceAt(10), EPSILON);
        }

        @Test
        @DisplayName("belief halves over the kind's half-life")
        void confidenceHalvesOverTheHalfLife() {
            Memory observation = Memory.formed(
                    MemoryKind.OBSERVED_RESOURCE, at(0), HOME, "WATER", "full");
            long halfLife = MemoryKind.OBSERVED_RESOURCE.halfLifeTicks();

            assertEquals(0.5, observation.confidenceAt(halfLife), 1e-6);
            assertEquals(0.25, observation.confidenceAt(halfLife * 2), 1e-6);
        }

        @Test
        @DisplayName("where a thing is outlasts how much of it there was")
        void placesAreRememberedLongerThanQuantities() {
            Memory place = Memory.formed(MemoryKind.RESOURCE_LOCATION, at(0), HOME, "WATER", "here");
            Memory amount =
                    Memory.formed(MemoryKind.OBSERVED_RESOURCE, at(0), HOME, "WATER", "full");

            assertTrue(
                    place.confidenceAt(500) > amount.confidenceAt(500),
                    "a spring does not wander off, but it does empty");
            assertTrue(place.confidenceAt(500) > 0.9, "and it is still worth believing");
        }

        @Test
        @DisplayName("belief fades towards nothing without ever expiring outright")
        void confidenceDecaysSmoothly() {
            Memory memory = Memory.formed(MemoryKind.DANGER, at(0), HOME, "THIRST", "close call");

            double previous = 1.0;
            for (long tick = 100; tick <= 5000; tick += 100) {
                double now = memory.confidenceAt(tick);
                assertTrue(now < previous, "confidence should keep falling at tick " + tick);
                assertTrue(now > 0.0, "but never reach zero");
                previous = now;
            }
        }

        @Test
        @DisplayName("what comes back reports how sure the agent is now, not when it learned it")
        void recallReportsFadedConfidence() {
            MemoryStore store = new MemoryStore();
            store.remember(Memory.formed(
                    MemoryKind.OBSERVED_RESOURCE, at(0), HOME, "WATER", "full"));

            Recollection fresh = recallFrom(store, 0).get(0);
            Recollection stale = recallFrom(store, 400).get(0);

            assertEquals(1.0, fresh.confidence(), EPSILON);
            assertTrue(stale.confidence() < 0.3, "should be well faded: " + stale.confidence());
        }
    }

    // --- retrieval --------------------------------------------------------

    @Nested
    @DisplayName("retrieval")
    class Retrieval {

        @Test
        @DisplayName("asking about one thing does not return another")
        void filtersBySubject() {
            MemoryStore store = new MemoryStore();
            store.remember(water(1, 3, 8));
            store.remember(berries(1, 4, 8));

            List<Recollection> found = store.recall(
                    RecallQuery.from(at(10), HOME).about(ResourceType.WATER));

            assertEquals(1, found.size());
            assertEquals(new Location(3, 8), found.get(0).location());
        }

        @Test
        void filtersByKind() {
            MemoryStore store = new MemoryStore();
            store.remember(water(1, 3, 8));
            store.remember(Memory.formed(MemoryKind.DANGER, at(1), HOME, "THIRST", "close call"));

            assertEquals(
                    1,
                    store.recall(RecallQuery.from(at(10), HOME).ofKind(MemoryKind.DANGER)).size());
        }

        @Test
        @DisplayName("the nearest of two equally believed places comes back first")
        void nearerMemoriesRankHigher() {
            MemoryStore store = new MemoryStore();
            store.remember(water(1, 6, 8));
            store.remember(water(1, 0, 0));

            List<Recollection> found = recallFrom(store, 1);

            assertEquals(new Location(6, 8), found.get(0).location());
            assertTrue(found.get(0).relevance() > found.get(1).relevance());
        }

        @Test
        @DisplayName("of two equally near places, the fresher one comes back first")
        void fresherMemoriesRankHigher() {
            MemoryStore store = new MemoryStore();
            store.remember(Memory.formed(
                    MemoryKind.OBSERVED_RESOURCE, at(1), new Location(6, 8), "WATER", "old"));
            store.remember(Memory.formed(
                    MemoryKind.OBSERVED_RESOURCE, at(400), new Location(10, 8), "WATER", "new"));

            List<Recollection> found = recallFrom(store, 400);

            assertEquals(new Location(10, 8), found.get(0).location());
        }

        @Test
        @DisplayName("irrelevant memories are pushed down, not deleted")
        void irrelevantMemoriesAreDeprioritised() {
            MemoryStore store = new MemoryStore();
            Memory nearAndVital = water(100, 9, 8).withImportance(0.9);
            Memory farAndTrivial = berries(1, 15, 15).withImportance(0.1);
            store.remember(nearAndVital);
            store.remember(farAndTrivial);

            List<Recollection> found = recallFrom(store, 100);

            assertEquals(2, found.size(), "both are still held");
            assertEquals(nearAndVital, found.get(0).memory());
            assertTrue(
                    found.get(0).relevance() > found.get(1).relevance() * 10,
                    "the useful one should dominate: " + found);
        }

        @Test
        @DisplayName("a memory too faint to act on is not offered at all")
        void veryFaintMemoriesAreNotWorthMentioning() {
            MemoryStore store = new MemoryStore();
            store.remember(Memory.formed(
                    MemoryKind.OBSERVED_RESOURCE, at(0), new Location(15, 15), "WATER", "full"));

            assertTrue(store.recall(RecallQuery.from(at(4000), HOME)).isEmpty());
            assertEquals(1, store.size(), "but it is still in there");
        }

        @Test
        @DisplayName("recall returns at most what was asked for")
        void respectsTheLimit() {
            MemoryStore store = new MemoryStore();
            for (int i = 0; i < 10; i++) {
                store.remember(berries(1, i, 8));
            }

            assertEquals(3, store.recall(RecallQuery.from(at(1), HOME).limitedTo(3)).size());
        }

        @Test
        @DisplayName("asking twice gives the same answer in the same order")
        void recallIsDeterministic() {
            MemoryStore store = new MemoryStore();
            store.remember(water(1, 3, 8));
            store.remember(berries(1, 13, 8));
            store.remember(water(1, 8, 2));

            assertEquals(recallFrom(store, 50), recallFrom(store, 50));
        }

        @Test
        @DisplayName("an empty memory answers honestly rather than failing")
        void emptyMemoryRecallsNothing() {
            assertTrue(recallFrom(new MemoryStore(), 1).isEmpty());
        }

        @Test
        @DisplayName("every recollection can explain itself")
        void recollectionsAreExplainable() {
            MemoryStore store = new MemoryStore();
            store.remember(water(10, 3, 8));

            Recollection found = recallFrom(store, 60).get(0);

            String described = found.memory().describeAt(60);
            assertTrue(described.contains("RESOURCE_LOCATION"), described);
            assertTrue(described.contains("(3,8)"), described);
            assertTrue(described.contains("50 ticks ago"), described);
        }
    }

    @Test
    @DisplayName("a memory refuses to hold an impossible certainty")
    void rejectsImpossibleValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Memory(
                        MemoryKind.DANGER, at(1), HOME, "THIRST", 1.5, 0.5, "too sure"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Memory(
                        MemoryKind.DANGER, at(1), HOME, "THIRST", 0.5, -0.1, "too trivial"));
    }
}
