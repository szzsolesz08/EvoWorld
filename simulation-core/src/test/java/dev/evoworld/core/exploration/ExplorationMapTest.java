package dev.evoworld.core.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExplorationMapTest {

    private static final double EPSILON = 1e-9;
    private static final WorldBounds BOUNDS = WorldBounds.of(16, 16);

    private static SimulationTime at(long tick) {
        return new SimulationTime(tick, Duration.ofMinutes(10).multipliedBy(tick));
    }

    @Nested
    @DisplayName("regions")
    class Regions {

        @Test
        @DisplayName("a sixteen-square world is sixteen four-by-four regions")
        void worldIsDividedIntoRegions() {
            assertEquals(16, Region.covering(BOUNDS).size());
            assertEquals(new Region(0, 0), Region.containing(new Location(3, 3)));
            assertEquals(new Region(1, 0), Region.containing(new Location(4, 3)));
            assertEquals(new Region(3, 3), Region.containing(new Location(15, 15)));
        }

        @Test
        void aRegionsCentreIsInsideIt() {
            for (Region region : Region.covering(BOUNDS)) {
                assertEquals(region, Region.containing(region.centre()));
            }
        }
    }

    @Nested
    @DisplayName("coverage")
    class Coverage {

        @Test
        @DisplayName("a new agent knows nothing of the world")
        void startsKnowingNothing() {
            ExplorationMap map = new ExplorationMap();

            assertEquals(0, map.regionsVisited());
            assertEquals(0.0, map.worldFamiliarity(0), EPSILON);
        }

        @Test
        @DisplayName("standing somewhere makes that region, and only that region, familiar")
        void sightingMarksOneRegion() {
            ExplorationMap map = new ExplorationMap();

            map.sight(BOUNDS, new Location(1, 1), at(10));

            assertEquals(1.0, map.familiarity(new Region(0, 0), 10), EPSILON);
            assertEquals(0.0, map.familiarity(new Region(1, 0), 10), EPSILON);
            assertEquals(1, map.regionsVisited());
            assertEquals(16, map.regionCount());
            assertEquals(1.0 / 16, map.worldFamiliarity(10), EPSILON);
        }

        @Test
        @DisplayName("familiarity halves over the half-life, so places are worth revisiting")
        void familiarityFades() {
            ExplorationMap map = new ExplorationMap();
            map.sight(BOUNDS, new Location(1, 1), at(0));

            assertEquals(
                    0.5,
                    map.familiarity(new Region(0, 0), ExplorationMap.FAMILIARITY_HALF_LIFE),
                    1e-6);
            assertTrue(map.familiarity(new Region(0, 0), 5000) < 0.01);
        }

        @Test
        @DisplayName("going back refreshes a region that had faded")
        void revisitingRefreshes() {
            ExplorationMap map = new ExplorationMap();
            map.sight(BOUNDS, new Location(1, 1), at(0));
            map.sight(BOUNDS, new Location(2, 2), at(1000));

            assertEquals(1.0, map.familiarity(new Region(0, 0), 1000), EPSILON);
        }

        @Test
        @DisplayName("the frontier is simply the least familiar regions, never a special list")
        void frontierIsTheLeastFamiliar() {
            ExplorationMap map = new ExplorationMap();
            for (Region region : Region.covering(BOUNDS)) {
                if (!region.equals(new Region(3, 3))) {
                    map.sight(BOUNDS, region.centre(), at(100));
                }
            }

            List<Region> frontier = map.leastFamiliar(100, 1);

            assertEquals(List.of(new Region(3, 3)), frontier);
        }

        @Test
        @DisplayName("the frontier is ordered the same way every time")
        void frontierIsDeterministic() {
            ExplorationMap first = new ExplorationMap();
            ExplorationMap second = new ExplorationMap();
            first.sight(BOUNDS, new Location(5, 5), at(3));
            second.sight(BOUNDS, new Location(5, 5), at(3));

            assertEquals(first.leastFamiliar(50, 6), second.leastFamiliar(50, 6));
        }
    }

    @Nested
    @DisplayName("discoveries")
    class Discoveries {

        @Test
        @DisplayName("the first sight of a kind of thing is a discovery, and says so")
        void firstSightingIsAFirstEverDiscovery() {
            ExplorationMap map = new ExplorationMap();
            map.sight(BOUNDS, new Location(1, 1), at(1));

            Optional<Discovery> found =
                    map.notice(new Location(2, 1), ResourceType.WATER, at(1));

            assertTrue(found.isPresent());
            assertTrue(found.get().firstEver());
            assertEquals(new Region(0, 0), found.get().region());
            assertEquals(1L, found.get().when().tick());
        }

        @Test
        @DisplayName("seeing the same kind of thing in the same region again is not news")
        void repeatsAreNotDiscoveries() {
            ExplorationMap map = new ExplorationMap();
            map.notice(new Location(1, 1), ResourceType.WATER, at(1));

            assertTrue(map.notice(new Location(2, 2), ResourceType.WATER, at(5)).isEmpty());
        }

        @Test
        @DisplayName("finding a known kind of thing somewhere new is a discovery, but not a first")
        void newPlaceIsADiscoveryButNotAFirst() {
            ExplorationMap map = new ExplorationMap();
            map.notice(new Location(1, 1), ResourceType.WATER, at(1));

            Optional<Discovery> elsewhere =
                    map.notice(new Location(13, 13), ResourceType.WATER, at(9));

            assertTrue(elsewhere.isPresent());
            assertFalse(elsewhere.get().firstEver());
        }

        @Test
        @DisplayName("what was found where is kept, which is the start of a map of the world")
        void findsAreAssociatedWithRegions() {
            ExplorationMap map = new ExplorationMap();
            map.notice(new Location(1, 1), ResourceType.WATER, at(1));
            map.notice(new Location(2, 2), ResourceType.BERRIES, at(1));

            assertEquals(
                    java.util.Set.of(ResourceType.WATER, ResourceType.BERRIES),
                    map.foundIn(new Region(0, 0)));
            assertTrue(map.foundIn(new Region(3, 3)).isEmpty());
        }
    }
}
