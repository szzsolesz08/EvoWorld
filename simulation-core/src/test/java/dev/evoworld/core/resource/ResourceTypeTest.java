package dev.evoworld.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResourceTypeTest {

    @Test
    @DisplayName("the world has exactly the four resources the milestone calls for")
    void hasTheExpectedTypes() {
        assertEquals(4, ResourceType.values().length);
        assertEquals("berries", ResourceType.BERRIES.label());
        assertEquals("water", ResourceType.WATER.label());
        assertEquals("wood", ResourceType.WOOD.label());
        assertEquals("stone", ResourceType.STONE.label());
    }

    @ParameterizedTest
    @EnumSource(ResourceType.class)
    @DisplayName("every type holds a positive amount and regrows at a sane rate")
    void everyTypeIsWellFormed(ResourceType type) {
        assertTrue(type.capacity() > 0.0, type + " must hold something");
        assertTrue(type.regenPerTick() >= 0.0, type + " must not regrow backwards");
        assertTrue(
                type.regenPerTick() <= type.capacity(),
                type + " must not refill more than it can hold in one tick");
    }

    @Test
    @DisplayName("food and water come back, stone does not")
    void renewabilityFollowsTheResource() {
        assertTrue(ResourceType.BERRIES.isRenewable());
        assertTrue(ResourceType.WATER.isRenewable());
        assertTrue(ResourceType.WOOD.isRenewable());
        assertFalse(ResourceType.STONE.isRenewable());
    }

    @Test
    @DisplayName("regrowth times rank the way the resources should feel")
    void regrowthTimesAreOrdered() {
        long water = ResourceType.WATER.ticksToFullFromEmpty().orElseThrow();
        long berries = ResourceType.BERRIES.ticksToFullFromEmpty().orElseThrow();
        long wood = ResourceType.WOOD.ticksToFullFromEmpty().orElseThrow();

        assertTrue(water < berries, "a spring refills faster than a bush fruits");
        assertTrue(berries < wood, "a bush fruits faster than a tree grows");
        assertTrue(ResourceType.STONE.ticksToFullFromEmpty().isEmpty());
    }

    @Test
    @DisplayName("a bush refills within a day of ten-minute ticks")
    void berriesRegrowWithinADay() {
        assertEquals(48L, ResourceType.BERRIES.ticksToFullFromEmpty().orElseThrow());
    }
}
