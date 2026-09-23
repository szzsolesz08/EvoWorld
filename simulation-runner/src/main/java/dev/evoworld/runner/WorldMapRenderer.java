package dev.evoworld.runner;

import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.ViewWindow;
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

    /** How much ground a printed map shows: a couple of hours' walk across. */
    private static final int ACROSS = 41;
    private static final int DOWN = 21;

    private static String header(WorldState state) {
        return String.format(
                Locale.ROOT,
                "world %s at %s - %d resource node(s) in view of %d agent(s), around %s",
                state.bounds(),
                state.time(),
                state.resources().size(),
                state.agents().size(),
                focus(state));
    }

    /** Where the map is drawn around: the first agent, or the origin if there is none. */
    private static Location focus(WorldState state) {
        return state.agents().stream().findFirst().map(agent -> agent.location()).orElse(new Location(0, 0));
    }

    private static List<String> grid(WorldState state) {
        // A world with no edges cannot be printed whole, so this prints the
        // ground around whoever is in it.
        ViewWindow window = ViewWindow.around(focus(state), ACROSS, DOWN, state.bounds());

        List<String> lines = new ArrayList<>(window.down() + 1);
        StringBuilder axis = new StringBuilder("       ");
        for (int column = 0; column < window.across(); column++) {
            // Only the last digit fits, which is enough to count along a row.
            axis.append(Math.floorMod(window.tileAt(column, 0).x(), 10)).append(' ');
        }
        lines.add(axis.toString());

        for (int row = 0; row < window.down(); row++) {
            StringBuilder line = new StringBuilder(
                    String.format(Locale.ROOT, "%6d ", window.tileAt(0, row).y()));
            for (int column = 0; column < window.across(); column++) {
                line.append(symbolAt(state, window.tileAt(column, row))).append(' ');
            }
            lines.add(line.toString());
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
                            ? "regrows " + type.regenPerHour() + "/h"
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
