package dev.evoworld.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.world.Location;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResourceNodeTest {

    /** The default tick; regrowth is per hour, applied a tick at a time. */
    private static final Duration TICK = Duration.ofMinutes(10);

    private static final double EPSILON = 1e-9;
    private static final Location HERE = new Location(3, 4);

    @Nested
    @DisplayName("creation")
    class Creation {

        @ParameterizedTest
        @EnumSource(ResourceType.class)
        @DisplayName("a full node starts at its type's capacity")
        void fullNodeStartsAtCapacity(ResourceType type) {
            ResourceNode node = ResourceNode.full(type, HERE);

            assertEquals(type, node.type());
            assertEquals(HERE, node.location());
            assertEquals(type.capacity(), node.capacity(), EPSILON);
            assertEquals(type.capacity(), node.quantity(), EPSILON);
            assertTrue(node.isFull());
            assertTrue(node.isAvailable());
        }

        @Test
        @DisplayName("a node can start part full")
        void nodeCanStartPartFull() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 3.0);

            assertEquals(3.0, node.quantity(), EPSILON);
            assertEquals(ResourceType.BERRIES.capacity(), node.capacity(), EPSILON);
            assertFalse(node.isFull());
        }

        @Test
        @DisplayName("a node can start empty, which is how a picked bush looks")
        void nodeCanStartEmpty() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 0.0);

            assertTrue(node.isDepleted());
            assertFalse(node.isAvailable());
            assertEquals(0.0, node.availability(), EPSILON);
        }

        @Test
        @DisplayName("a node cannot hold more than it can hold, or less than nothing")
        void rejectsImpossibleQuantities() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ResourceNode.holding(ResourceType.BERRIES, HERE, -1.0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ResourceNode.holding(
                            ResourceType.BERRIES, HERE, ResourceType.BERRIES.capacity() + 0.1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ResourceNode(ResourceType.BERRIES, HERE, 0.0, 0.0));
        }

        @Test
        void rejectsMissingTypeOrLocation() {
            assertThrows(
                    NullPointerException.class, () -> ResourceNode.full(null, HERE));
            assertThrows(
                    NullPointerException.class, () -> ResourceNode.full(ResourceType.WATER, null));
        }
    }

    @Nested
    @DisplayName("availability")
    class Availability {

        @Test
        @DisplayName("availability is the fraction of capacity still present")
        void availabilityIsAFraction() {
            ResourceNode node = new ResourceNode(ResourceType.BERRIES, HERE, 10.0, 10.0);

            assertEquals(1.0, node.availability(), EPSILON);
            node.harvest(2.5);
            assertEquals(0.75, node.availability(), EPSILON);
            node.harvestAll();
            assertEquals(0.0, node.availability(), EPSILON);
        }

        @Test
        @DisplayName("a node stops being available the moment it is emptied")
        void availabilityFlipsAtEmpty() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 1.0);

            assertTrue(node.isAvailable());
            node.harvest(1.0);
            assertFalse(node.isAvailable());
            assertTrue(node.isDepleted());
        }
    }

    @Nested
    @DisplayName("consumption")
    class Consumption {

        @Test
        @DisplayName("harvesting removes exactly what it returns")
        void harvestRemovesWhatItReturns() {
            ResourceNode node = ResourceNode.full(ResourceType.BERRIES, HERE);
            double before = node.quantity();

            double taken = node.harvest(4.0);

            assertEquals(4.0, taken, EPSILON);
            assertEquals(before - 4.0, node.quantity(), EPSILON);
        }

        @Test
        @DisplayName("asking for more than is there returns what is there, not an error")
        void harvestIsLimitedByWhatRemains() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 2.0);

            double taken = node.harvest(10.0);

            assertEquals(2.0, taken, EPSILON);
            assertEquals(0.0, node.quantity(), EPSILON);
        }

        @Test
        @DisplayName("harvesting a depleted node yields nothing and is not an error")
        void harvestingNothingYieldsNothing() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 0.0);

            assertEquals(0.0, node.harvest(5.0), EPSILON);
            assertEquals(0.0, node.quantity(), EPSILON);
        }

        @Test
        @DisplayName("repeated harvests drain a node and no further")
        void repeatedHarvestsDrainTheNode() {
            ResourceNode node = new ResourceNode(ResourceType.BERRIES, HERE, 10.0, 10.0);

            double total = 0.0;
            for (int i = 0; i < 6; i++) {
                total += node.harvest(2.0);
            }

            assertEquals(10.0, total, EPSILON, "a node cannot give more than it held");
            assertTrue(node.isDepleted());
        }

        @Test
        @DisplayName("harvesting zero is a no-op")
        void harvestingZeroChangesNothing() {
            ResourceNode node = ResourceNode.full(ResourceType.WOOD, HERE);

            assertEquals(0.0, node.harvest(0.0), EPSILON);
            assertTrue(node.isFull());
        }

        @Test
        @DisplayName("harvestAll empties the node in one go")
        void harvestAllTakesEverything() {
            ResourceNode node = ResourceNode.full(ResourceType.STONE, HERE);

            assertEquals(ResourceType.STONE.capacity(), node.harvestAll(), EPSILON);
            assertTrue(node.isDepleted());
            assertEquals(0.0, node.harvestAll(), EPSILON);
        }

        @Test
        void rejectsNegativeHarvests() {
            ResourceNode node = ResourceNode.full(ResourceType.BERRIES, HERE);

            assertThrows(IllegalArgumentException.class, () -> node.harvest(-1.0));
            assertTrue(node.isFull(), "a rejected harvest must not change the node");
        }
    }

    @Nested
    @DisplayName("regeneration")
    class Regeneration {

        @Test
        @DisplayName("a renewable node recovers its type's rate per tick")
        void renewableNodesRegrow() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 0.0);

            node.regenerate(TICK);

            assertEquals(ResourceType.BERRIES.regenPerTick(TICK), node.quantity(), EPSILON);
        }

        @Test
        @DisplayName("a picked bush grows back to full and stops there")
        void regrowthStopsAtCapacity() {
            ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 0.0);
            long expected = ResourceType.BERRIES.ticksToFullFromEmpty(TICK).orElseThrow();

            for (long tick = 0; tick < expected; tick++) {
                node.regenerate(TICK);
            }
            assertTrue(node.isFull(), "should be full after " + expected + " ticks");

            node.regenerate(TICK);
            assertEquals(node.capacity(), node.quantity(), EPSILON, "must not overflow capacity");
        }

        @Test
        @DisplayName("stone never comes back")
        void nonRenewableNodesStayDepleted() {
            ResourceNode node = ResourceNode.full(ResourceType.STONE, HERE);
            node.harvestAll();

            for (int tick = 0; tick < 1000; tick++) {
                node.regenerate(TICK);
            }

            assertTrue(node.isDepleted());
            assertEquals(0.0, node.quantity(), EPSILON);
        }

        @Test
        @DisplayName("regrowth respects a node's own capacity, not just the type's")
        void regrowthRespectsNodeCapacity() {
            ResourceNode small = new ResourceNode(ResourceType.BERRIES, HERE, 1.0, 0.0);

            small.regenerate(TICK);
            small.regenerate(TICK);
            small.regenerate(TICK);
            small.regenerate(TICK);
            small.regenerate(TICK);

            assertEquals(1.0, small.quantity(), EPSILON);
        }

        @Test
        @DisplayName("harvest and regrowth interleave the way a foraged bush would")
        void harvestAndRegrowthInterleave() {
            ResourceNode node = ResourceNode.full(ResourceType.BERRIES, HERE);
            double rate = ResourceType.BERRIES.regenPerTick(TICK);

            node.harvest(6.0);
            double afterHarvest = node.quantity();
            node.regenerate(TICK);
            node.regenerate(TICK);

            assertEquals(afterHarvest + 2 * rate, node.quantity(), EPSILON);
            assertFalse(node.isFull());
        }

        @Test
        @DisplayName("regenerating a full node changes nothing")
        void regeneratingAFullNodeIsANoOp() {
            ResourceNode node = ResourceNode.full(ResourceType.WATER, HERE);

            node.regenerate(TICK);

            assertEquals(ResourceType.WATER.capacity(), node.quantity(), EPSILON);
        }
    }

    @Test
    @DisplayName("a snapshot copies the node and does not follow it")
    void snapshotIsAFrozenCopy() {
        ResourceNode node = ResourceNode.full(ResourceType.BERRIES, HERE);

        ResourceSnapshot snapshot = node.snapshot();
        node.harvest(5.0);

        assertEquals(ResourceType.BERRIES, snapshot.type());
        assertEquals(HERE, snapshot.location());
        assertEquals(ResourceType.BERRIES.capacity(), snapshot.quantity(), EPSILON);
        assertEquals(ResourceType.BERRIES.capacity() - 5.0, node.quantity(), EPSILON);
        assertTrue(snapshot.isAvailable());
    }

    @Test
    @DisplayName("a snapshot of an empty node reads as depleted")
    void snapshotReportsDepletion() {
        ResourceNode node = ResourceNode.holding(ResourceType.BERRIES, HERE, 0.0);

        ResourceSnapshot snapshot = node.snapshot();

        assertTrue(snapshot.isDepleted());
        assertFalse(snapshot.isAvailable());
        assertEquals(0.0, snapshot.availability(), EPSILON);
    }
}
