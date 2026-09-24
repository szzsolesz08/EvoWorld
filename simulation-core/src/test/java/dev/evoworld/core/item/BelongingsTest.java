package dev.evoworld.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.resource.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A kit: what it lets you carry, what it helps you do, and how it wears out. */
class BelongingsTest {

    @Test
    @DisplayName("an empty kit carries nothing and helps with nothing")
    void emptyKit() {
        Belongings nothing = Belongings.nothing();

        assertTrue(nothing.isEmpty());
        assertEquals(0.0, nothing.roomFor(ResourceType.WATER), 1e-9);
        assertEquals(0.0, nothing.bulk(), 1e-9);
        assertTrue(nothing.toolFor(ResourceType.WOOD).isEmpty());
    }

    @Test
    @DisplayName("containers add room for what they hold, and nothing else")
    void containersMakeRoom() {
        Belongings kit = Belongings.of(Item.made(ItemKind.FLASK), Item.made(ItemKind.FLASK));

        assertEquals(2 * ItemKind.FLASK.holdsUpTo(), kit.roomFor(ResourceType.WATER), 1e-9);
        assertEquals(0.0, kit.roomFor(ResourceType.BERRIES), 1e-9);
        assertEquals(2 * ItemKind.FLASK.bulk(), kit.bulk(), 1e-9);
    }

    @Test
    @DisplayName("a tool is found by the job, not by its name")
    void toolsAreFoundByTheirWork() {
        Belongings kit = Belongings.of(Item.made(ItemKind.AXE), Item.made(ItemKind.PICKAXE));

        assertEquals(ItemKind.AXE, kit.toolFor(ResourceType.WOOD).orElseThrow().kind());
        assertEquals(ItemKind.PICKAXE, kit.toolFor(ResourceType.STONE).orElseThrow().kind());
        assertTrue(kit.toolFor(ResourceType.BERRIES).isEmpty(), "nothing here helps pick berries");
    }

    @Test
    @DisplayName("using something wears it, one thing at a time, until it is finished")
    void wearWearsOneThingAtATime() {
        Belongings kit = Belongings.of(Item.made(ItemKind.AXE), Item.made(ItemKind.AXE));

        Belongings afterOne = kit.after(ItemKind.AXE);

        assertEquals(ItemKind.AXE.uses() - 1, afterOne.items().get(0).usesLeft());
        assertEquals(ItemKind.AXE.uses(), afterOne.items().get(1).usesLeft(),
                "the second axe is untouched while the first has life in it");
    }

    @Test
    @DisplayName("a worn-out thing helps with nothing and is thrown away")
    void wornOutIsUseless() {
        Belongings kit = Belongings.of(new Item(ItemKind.FLASK, 1));

        Belongings finished = kit.after(ItemKind.FLASK);

        assertTrue(finished.items().get(0).isWornOut());
        assertEquals(0.0, finished.roomFor(ResourceType.WATER), 1e-9, "a burst flask holds nothing");
        assertFalse(finished.has(ItemKind.FLASK));
        assertTrue(finished.withoutWornOut().isEmpty());
    }

    @Test
    @DisplayName("wearing something the agent does not have changes nothing")
    void wearingWhatIsNotThere() {
        Belongings kit = Belongings.of(Item.made(ItemKind.FLASK));

        assertEquals(kit, kit.after(ItemKind.AXE));
    }

    @Test
    @DisplayName("a thing cannot be worn past the end of its life")
    void noNegativeLife() {
        assertThrows(IllegalArgumentException.class, () -> new Item(ItemKind.AXE, -1));
        assertEquals(0, new Item(ItemKind.AXE, 0).worn().usesLeft());
    }

    @Test
    @DisplayName("condition is what is left of it, which is what an observer shows")
    void conditionIsWhatIsLeft() {
        assertEquals(1.0, Item.made(ItemKind.FLASK).condition(), 1e-9);
        assertEquals(0.5, new Item(ItemKind.FLASK, ItemKind.FLASK.uses() / 2).condition(), 0.01);
        assertEquals(0.0, new Item(ItemKind.FLASK, 0).condition(), 1e-9);
    }
}
