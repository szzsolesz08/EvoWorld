package dev.evoworld.runner;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Hand-written action sequences for driving the simulation by hand.
 *
 * <p>These are demonstrations, not behaviour. Choosing which bush to visit here
 * is a scriptwriter reading a map, not an agent deciding anything - the agent
 * runs whatever it is handed, in order, without looking. When the decision
 * layer learns to reach for food itself, these scripts stay exactly as useful
 * for checking that an action does what it says.
 */
public final class ActionScripts {

    /** How many berries to take in one go, and how many to eat in one sitting. */
    private static final double HARVEST = 10.0;
    private static final double MEAL = 4.0;
    private static final double GULP = 4.0;
    private static final double GATHER_EFFORT = 1.0;

    private static final int DEFAULT_CYCLES = 3;

    private ActionScripts() {
    }

    /** Three rounds of the forage cycle, which is enough to see every action work. */
    public static List<Action> forage(World world, Location start, SimulationConfig config) {
        return forage(world, start, config, DEFAULT_CYCLES);
    }

    /**
     * Repeats: walk to berries, gather, eat, walk to water, drink, eat.
     *
     * <p>Each round visits a bush the script has not used yet, because a bush
     * that has just been picked clean would only block - the cycle is meant to
     * show actions succeeding, and there is a separate demonstration of
     * failure in the tests.
     */
    public static List<Action> forage(
            World world, Location start, SimulationConfig config, int cycles) {
        List<Action> script = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Location here = start;

        for (int cycle = 0; cycle < cycles; cycle++) {
            Optional<Location> berries = nearestUnvisited(world, ResourceType.BERRIES, here, visited);
            if (berries.isPresent()) {
                visited.add(berries.get());
                script.addAll(walk(here, berries.get(), config));
                here = berries.get();
                script.add(new GatherAction(ResourceType.BERRIES, HARVEST, GATHER_EFFORT));
                script.add(new EatAction(ResourceType.BERRIES, MEAL));
            }

            Optional<Location> water = nearest(world, ResourceType.WATER, here);
            if (water.isPresent()) {
                script.addAll(walk(here, water.get(), config));
                here = water.get();
                script.add(new DrinkAction(ResourceType.WATER, GULP));
                script.add(new DrinkAction(ResourceType.WATER, GULP));
            }

            script.add(new EatAction(ResourceType.BERRIES, MEAL));
            script.add(new RestAction(config.actions().restEnergyGain()));
        }
        return List.copyOf(script);
    }

    /** One {@link MoveAction} per tile, since a move is a single step. */
    private static List<Action> walk(Location from, Location to, SimulationConfig config) {
        int steps = from.manhattanDistanceTo(to);
        List<Action> moves = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            moves.add(new MoveAction(to, config.actions().moveEnergyCost()));
        }
        return moves;
    }

    private static Optional<Location> nearest(World world, ResourceType type, Location from) {
        return nearestUnvisited(world, type, from, Set.of());
    }

    /**
     * The closest node of a type the script has not already spent, ties broken
     * by placement order, so a script built from a seed is the same every time.
     */
    private static Optional<Location> nearestUnvisited(
            World world, ResourceType type, Location from, Set<Location> visited) {
        return world.resourcesOfType(type).stream()
                .filter(ResourceNode::isAvailable)
                .map(ResourceNode::location)
                .filter(location -> !visited.contains(location))
                .min(Comparator.comparingInt(location -> location.manhattanDistanceTo(from)));
    }
}
