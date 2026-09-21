package dev.evoworld.core.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.resource.ResourceType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryTest {

    private static final double EPSILON = 1e-9;

    @Test
    @DisplayName("a new inventory is empty but has room")
    void startsEmpty() {
        Inventory inventory = Inventory.empty();

        assertTrue(inventory.isEmpty());
        assertFalse(inventory.isFull());
        assertEquals(0.0, inventory.total(), EPSILON);
        assertEquals(Inventory.DEFAULT_CAPACITY, inventory.freeSpace(), EPSILON);
    }

    @Test
    void addsAndReportsQuantities() {
        Inventory inventory = Inventory.empty().plus(ResourceType.BERRIES, 3.0);

        assertEquals(3.0, inventory.quantityOf(ResourceType.BERRIES), EPSILON);
        assertEquals(0.0, inventory.quantityOf(ResourceType.STONE), EPSILON);
        assertTrue(inventory.has(ResourceType.BERRIES, 3.0));
        assertFalse(inventory.has(ResourceType.BERRIES, 3.1));
    }

    @Test
    @DisplayName("adding the same resource twice accumulates")
    void addingAccumulates() {
        Inventory inventory = Inventory.empty()
                .plus(ResourceType.BERRIES, 2.0)
                .plus(ResourceType.BERRIES, 2.5);

        assertEquals(4.5, inventory.quantityOf(ResourceType.BERRIES), EPSILON);
    }

    @Test
    @DisplayName("capacity is shared across everything carried")
    void capacityIsShared() {
        Inventory inventory = Inventory.empty(10.0)
                .plus(ResourceType.STONE, 7.0)
                .plus(ResourceType.BERRIES, 5.0);

        assertEquals(10.0, inventory.total(), EPSILON, "only what fits is taken");
        assertEquals(3.0, inventory.quantityOf(ResourceType.BERRIES), EPSILON);
        assertTrue(inventory.isFull());
        assertEquals(0.0, inventory.freeSpace(), EPSILON);
    }

    @Test
    @DisplayName("a full inventory silently refuses more")
    void fullInventoryTakesNothing() {
        Inventory full = Inventory.empty(5.0).plus(ResourceType.WOOD, 5.0);

        Inventory after = full.plus(ResourceType.BERRIES, 3.0);

        assertEquals(full, after);
        assertEquals(0.0, after.spaceFor(3.0), EPSILON);
    }

    @Test
    void spaceForReportsWhatWouldFit() {
        Inventory inventory = Inventory.empty(10.0).plus(ResourceType.WOOD, 8.0);

        assertEquals(2.0, inventory.spaceFor(5.0), EPSILON);
        assertEquals(1.0, inventory.spaceFor(1.0), EPSILON);
        assertEquals(0.0, inventory.spaceFor(0.0), EPSILON);
    }

    @Test
    void removesWhatIsThere() {
        Inventory inventory = Inventory.empty().plus(ResourceType.WATER, 6.0).minus(ResourceType.WATER, 2.0);

        assertEquals(4.0, inventory.quantityOf(ResourceType.WATER), EPSILON);
    }

    @Test
    @DisplayName("removing more than is held empties that resource, no further")
    void removingTooMuchJustEmpties() {
        Inventory inventory = Inventory.empty().plus(ResourceType.WATER, 2.0);

        Inventory after = inventory.minus(ResourceType.WATER, 99.0);

        assertEquals(0.0, after.quantityOf(ResourceType.WATER), EPSILON);
        assertTrue(after.isEmpty());
    }

    @Test
    @DisplayName("removing something not carried changes nothing")
    void removingWhatIsNotThereIsANoOp() {
        Inventory inventory = Inventory.empty().plus(ResourceType.WOOD, 1.0);

        assertEquals(inventory, inventory.minus(ResourceType.STONE, 5.0));
    }

    @Test
    @DisplayName("an emptied inventory equals one that was never filled")
    void zeroEntriesAreDropped() {
        Inventory emptied = Inventory.empty().plus(ResourceType.WOOD, 3.0).minus(ResourceType.WOOD, 3.0);

        assertEquals(Inventory.empty(), emptied);
        assertTrue(emptied.contents().isEmpty());
    }

    @Test
    @DisplayName("changes produce a new inventory and leave the old one alone")
    void changesDoNotMutate() {
        Inventory original = Inventory.empty().plus(ResourceType.BERRIES, 5.0);

        Inventory changed = original.minus(ResourceType.BERRIES, 5.0);

        assertEquals(5.0, original.quantityOf(ResourceType.BERRIES), EPSILON);
        assertTrue(changed.isEmpty());
    }

    @Test
    @DisplayName("the contents cannot be edited from outside")
    void contentsAreUnmodifiable() {
        Inventory inventory = Inventory.empty().plus(ResourceType.BERRIES, 1.0);

        assertThrows(
                UnsupportedOperationException.class,
                () -> inventory.contents().put(ResourceType.STONE, 1.0));
    }

    @Test
    @DisplayName("two inventories holding the same things are equal")
    void equalityIsByValue() {
        Inventory first = Inventory.empty()
                .plus(ResourceType.BERRIES, 2.0)
                .plus(ResourceType.STONE, 1.0);
        Inventory second = Inventory.empty()
                .plus(ResourceType.STONE, 1.0)
                .plus(ResourceType.BERRIES, 2.0);

        assertEquals(first, second, "order of acquisition must not matter");
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void rejectsImpossibleValues() {
        assertThrows(IllegalArgumentException.class, () -> Inventory.empty(0.0));
        assertThrows(IllegalArgumentException.class, () -> Inventory.empty(-1.0));
        assertThrows(
                IllegalArgumentException.class, () -> Inventory.empty().plus(ResourceType.WOOD, -1.0));
        assertThrows(
                IllegalArgumentException.class, () -> Inventory.empty().minus(ResourceType.WOOD, -1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Inventory(Map.of(ResourceType.WOOD, -2.0), 10.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Inventory(Map.of(ResourceType.WOOD, 20.0), 10.0));
    }

    @Test
    @DisplayName("an inventory reads clearly in a log")
    void readsClearlyInALog() {
        assertEquals("carrying nothing", Inventory.empty().toString());
        assertEquals(
                "carrying berries 2.0",
                Inventory.empty().plus(ResourceType.BERRIES, 2.0).toString());
    }
}
