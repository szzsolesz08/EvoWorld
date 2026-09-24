package dev.evoworld.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldMapRendererTest {

    private static final SimulationTime TICK_1 = new SimulationTime(1L, Duration.ofMinutes(10));
    private static final WorldBounds BOUNDS = WorldBounds.of(4, 3);

    private static ResourceSnapshot node(ResourceType type, int x, int y, double quantity) {
        return new ResourceSnapshot(type, new Location(x, y), quantity, type.capacity());
    }

    private static String rendered(WorldState state) {
        return String.join("\n", WorldMapRenderer.render(state));
    }

    @Test
    @DisplayName("every resource type gets its own symbol on the map")
    void drawsEachResourceType() {
        WorldState state = new WorldState(
                TICK_1,
                BOUNDS,
                List.of(),
                List.of(
                        node(ResourceType.WATER, 0, 0, 100.0),
                        node(ResourceType.BERRIES, 1, 0, 12.0),
                        node(ResourceType.WOOD, 2, 0, 20.0),
                        node(ResourceType.STONE, 3, 0, 30.0)));

        List<String> lines = WorldMapRenderer.render(state);
        // Rows are labelled with their world coordinate, which is now wide
        // enough for a place a long walk from the origin.
        String label = String.format(java.util.Locale.ROOT, "%6d ", 0);
        String firstRow =
                lines.stream().filter(line -> line.startsWith(label)).findFirst().orElseThrow();

        assertEquals(label + "~ b T o ", firstRow);
    }

    @Test
    @DisplayName("an empty tile is a dot and a depleted node is marked as spent")
    void distinguishesEmptyFromDepleted() {
        WorldState state = new WorldState(
                TICK_1, BOUNDS, List.of(), List.of(node(ResourceType.BERRIES, 1, 1, 0.0)));

        String map = rendered(state);

        assertTrue(map.contains("  1 . x . . "), "depleted bush should read as x:\n" + map);
        assertTrue(map.contains("  0 . . . . "), "untouched row should be empty:\n" + map);
    }

    @Test
    @DisplayName("an agent is drawn over whatever it is standing on")
    void drawsAgentsOverResources() {
        WorldState state = new WorldState(
                TICK_1,
                BOUNDS,
                List.of(new AgentSnapshot(
                        AgentId.of("agent-1"),
                        "Atlas",
                        new Location(2, 2),
                        Needs.healthy(),
                        Inventory.empty(), Belongings.nothing(),
                        10L,
                        true)),
                List.of(node(ResourceType.BERRIES, 2, 2, 12.0)));

        String map = rendered(state);

        assertTrue(map.contains("  2 . . @ . "), "agent should stand on the bush:\n" + map);
    }

    @Test
    @DisplayName("a dead agent stops being drawn but is still reported")
    void deadAgentsLeaveTheMap() {
        WorldState state = new WorldState(
                TICK_1,
                BOUNDS,
                List.of(new AgentSnapshot(
                        AgentId.of("agent-1"),
                        "Atlas",
                        new Location(1, 1),
                        Needs.healthy().harm(Needs.MAX),
                        Inventory.empty(), Belongings.nothing(),
                        99L,
                        false)),
                List.of());

        String map = rendered(state);

        assertTrue(map.contains("  1 . . . . "), "a dead agent should not occupy a tile:\n" + map);
        assertTrue(map.contains("DEAD"), "but the summary should say so:\n" + map);
    }

    @Test
    @DisplayName("the map has one row per world row, plus an axis")
    void mapMatchesTheWorldSize() {
        WorldState state = new WorldState(TICK_1, BOUNDS, List.of(), List.of());

        List<String> rows = WorldMapRenderer.render(state).stream()
                .filter(line -> line.matches("\\s*\\d+ [.\\sxb~To@]*"))
                .toList();

        assertEquals(BOUNDS.height(), rows.size());
    }

    @Test
    @DisplayName("the summary tallies each type and says whether it regrows")
    void talliesResources() {
        WorldState state = new WorldState(
                TICK_1,
                BOUNDS,
                List.of(),
                List.of(
                        node(ResourceType.BERRIES, 0, 0, 6.0),
                        node(ResourceType.BERRIES, 1, 0, 12.0),
                        node(ResourceType.STONE, 2, 0, 30.0)));

        String output = rendered(state);

        assertTrue(output.contains("berries  2 node(s)    18.0 /   24.0"), output);
        assertTrue(output.contains("regrows 1.5/h"), output);
        assertTrue(output.contains("does not regrow"), output);
    }

    @Test
    @DisplayName("the header reports the world, the moment and what is in it")
    void headerDescribesTheWorld() {
        WorldState state = new WorldState(
                TICK_1, BOUNDS, List.of(), List.of(node(ResourceType.WATER, 0, 0, 100.0)));

        String header = WorldMapRenderer.render(state).get(0);

        assertTrue(header.contains("4x3"), header);
        assertTrue(header.contains("1 resource node(s)"), header);
        assertTrue(header.contains("0 agent(s)"), header);
    }

    @Test
    @DisplayName("rendering a snapshot does not need anything but the snapshot")
    void rendersAnEmptyWorld() {
        WorldState state = new WorldState(TICK_1, BOUNDS, List.of(), List.of());

        assertTrue(WorldMapRenderer.render(state).size() > BOUNDS.height());
    }
}
