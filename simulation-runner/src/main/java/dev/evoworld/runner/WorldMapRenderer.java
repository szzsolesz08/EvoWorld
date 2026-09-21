package dev.evoworld.runner;

import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws a {@link WorldState} as text.
 *
 * <p>Presentation, so it lives in the runner. The core has no idea a berry bush
 * looks like a {@code b}, which is what lets a future viewer draw it as a
 * sprite without the simulation changing. The renderer reads a snapshot and
 * returns a string - it touches nothing live and decides nothing.
 */
public final class WorldMapRenderer {

    private static final char EMPTY = '.';
    private static final char DEPLETED = 'x';
    private static final char AGENT = '@';

    private WorldMapRenderer() {
    }

    /** The map, a legend and a resource tally, as lines ready for the log. */
    public static List<String> render(WorldState state) {
        List<String> lines = new ArrayList<>();
        lines.add(header(state));
        lines.addAll(grid(state));
        lines.add(legend());
        lines.addAll(tally(state));
        return lines;
    }

    private static String header(WorldState state) {
        return String.format(
                Locale.ROOT,
                "world %s at %s - %d resource node(s), %d agent(s)",
                state.bounds(),
                state.time(),
                state.resources().size(),
                state.agents().size());
    }

    private static List<String> grid(WorldState state) {
        int width = state.bounds().width();
        int height = state.bounds().height();

        List<String> lines = new ArrayList<>(height + 1);
        StringBuilder axis = new StringBuilder("    ");
        for (int x = 0; x < width; x++) {
            // Only the last digit fits, which is enough to count along a row.
            axis.append(x % 10).append(' ');
        }
        lines.add(axis.toString());

        for (int y = 0; y < height; y++) {
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%3d ", y));
            for (int x = 0; x < width; x++) {
                row.append(symbolAt(state, new Location(x, y))).append(' ');
            }
            lines.add(row.toString());
        }
        return lines;
    }

    private static char symbolAt(WorldState state, Location location) {
        // An agent stands on top of whatever is underneath it.
        boolean occupied = state.agents().stream()
                .anyMatch(agent -> agent.alive() && agent.location().equals(location));
        if (occupied) {
            return AGENT;
        }
        return state.resourceAt(location)
                .map(node -> node.isAvailable() ? symbolFor(node.type()) : DEPLETED)
                .orElse(EMPTY);
    }

    private static char symbolFor(ResourceType type) {
        return switch (type) {
            case BERRIES -> 'b';
            case WATER -> '~';
            case WOOD -> 'T';
            case STONE -> 'o';
        };
    }

    private static String legend() {
        return "legend: ~ water   b berries   T wood   o stone   x depleted   @ agent   . empty";
    }

    private static List<String> tally(WorldState state) {
        Map<ResourceType, Double> held = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Double> total = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Integer> nodes = new EnumMap<>(ResourceType.class);
        for (ResourceSnapshot node : state.resources()) {
            held.merge(node.type(), node.quantity(), Double::sum);
            total.merge(node.type(), node.capacity(), Double::sum);
            nodes.merge(node.type(), 1, Integer::sum);
        }

        List<String> lines = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            if (!nodes.containsKey(type)) {
                continue;
            }
            lines.add(String.format(
                    Locale.ROOT,
                    "  %-8s %d node(s)  %6.1f / %6.1f  %s",
                    type.label(),
                    nodes.get(type),
                    held.get(type),
                    total.get(type),
                    type.isRenewable()
                            ? "regrows " + type.regenPerTick() + "/tick"
                            : "does not regrow"));
        }
        for (AgentSnapshot agent : state.agents()) {
            lines.add(String.format(
                    Locale.ROOT,
                    "  %-8s at %s  %s  %s  age %d ticks%s",
                    agent.name(),
                    agent.location(),
                    agent.needs(),
                    agent.inventory(),
                    agent.ticksAlive(),
                    agent.alive() ? "" : "  DEAD"));
        }
        return lines;
    }
}
